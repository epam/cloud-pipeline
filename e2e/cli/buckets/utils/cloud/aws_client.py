# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
import json
from time import sleep

import boto3
from tzlocal import get_localzone

from buckets.utils.cloud.cloud_client import CloudClient
from buckets.utils.listing import FileModel
from common_utils.cmd_utils import get_command_output


class S3Client(CloudClient):

    name = "S3"

    def object_exists(self, bucket_name, key):
        bucket_entry = self.get_bucket_entries(bucket_name, key)
        if len(bucket_entry) > 0 and bucket_entry[0].key == key:
            return True
        return False

    def folder_exists(self, bucket_name, key):
        return len(self.list_s3_folder(bucket_name, key)) > 0

    def list_object_tags(self, bucket, key, version=None, args=None):
        command = ['aws', 's3api', 'get-object-tagging', '--bucket', bucket, '--key', key]
        if version:
            command.extend(['--version-id', version])
        stdout = get_command_output(command, args=args, expected_status=0)[0]
        tags = json.loads(''.join(stdout).strip())['TagSet']
        result = {}
        for tag in tags:
            result[tag['Key']] = tag['Value']
        return result

    def assert_policy(self, bucket_name, sts, lts, backup_duration):
        sleep(40)
        actual_policy = self.s3_get_bucket_lifecycle(bucket_name)
        if sts:
            assert actual_policy['shortTermStorageDuration'] == sts, \
                "STS assertion failed: expected sts is {}, but actual is {}"\
                    .format(sts, actual_policy['shortTermStorageDuration'])
        if lts:
            assert actual_policy['longTermStorageDuration'] == lts, "LTS assertion failed"
        if backup_duration:
            if backup_duration != backup_duration:
                actual_policy = self.s3_get_bucket_lifecycle(bucket_name)
            assert actual_policy['backupDuration'] == backup_duration, "Backup Duration assertion failed"

    def get_modification_date(self, path):
        listing = self._get_listing(path)
        if not listing:
            raise RuntimeError('Storage path %s wasn\'t found.' % path)
        return listing[0].last_modified

    def _get_listing(self, path, recursive=False, expected_status=0):
        args = []
        if recursive:
            args.append("--recursive")
        cmd_output = self.s3_ls(path, args, expected_status)
        return self.parse_aws_listing(cmd_output)

    def get_versions(self, bucket_name, key):
        file_versions = get_aws_object_version_listing(bucket_name, key)
        return [version.version_id for version in file_versions]

    def wait_for_bucket_creation(self, bucket_name):
        waiter = boto3.client('s3').get_waiter('bucket_exists')
        waiter.wait(
            Bucket=bucket_name,
            WaiterConfig={
                'Delay': 3,
                'MaxAttempts': 20
            }
        )

    def wait_for_bucket_deletion(self, bucket_name):
        waiter = boto3.client('s3').get_waiter('bucket_not_exists')
        waiter.wait(
            Bucket=bucket_name,
            WaiterConfig={
                'Delay': 3,
                'MaxAttempts': 40
            }
        )

    @staticmethod
    def s3_get_bucket_lifecycle(bucket):
        s3 = boto3.resource('s3')
        try:
            rules = s3.BucketLifecycle(bucket).rules
        except:
            sleep(30)
            rules = s3.BucketLifecycle(bucket).rules
        result = {'shortTermStorageDuration': None, 'longTermStorageDuration': None, 'backupDuration': None}
        for rule in rules:
            if 'ID' not in rule:
                continue
            if rule['ID'] == 'Backup rule' and 'NoncurrentVersionExpiration' in rule and \
                    'NoncurrentDays' in rule['NoncurrentVersionExpiration'] and \
                    'Status' in rule and rule['Status'] == 'Enabled':
                result['backupDuration'] = rule['NoncurrentVersionExpiration']['NoncurrentDays']
            if rule['ID'] == 'Short term storage rule' and 'Transition' in rule and \
                    'Days' in rule['Transition'] and 'Status' in rule and rule['Status'] == 'Enabled':
                result['shortTermStorageDuration'] = rule['Transition']['Days']
            if rule['ID'] == 'Long term storage rule' and 'Expiration' in rule and 'Days' in rule['Expiration'] \
                    and 'Status' in rule and rule['Status'] == 'Enabled':
                result['longTermStorageDuration'] = rule['Expiration']['Days']
        return result

    @staticmethod
    def parse_aws_listing(lines):
        return map(FileModel.parse_from_aws_line, lines)

    @staticmethod
    def s3_ls(source, args, expected_status=0):
        command = ['aws', 's3', 'ls', source.replace('cp://', 's3://')]
        return get_command_output(command, args=args, expected_status=expected_status)[0]

    @staticmethod
    def get_bucket_entries(bucket_name, key):
        s3 = boto3.resource('s3')
        bucket = s3.Bucket(bucket_name)
        return list(bucket.objects.filter(Prefix=key))

    @staticmethod
    def list_s3_folder(bucket_name, key):
        if not key.endswith('/'):
            key = key + '/'
        bucket_entry = S3Client.get_bucket_entries(bucket_name, key)
        result = []
        for entry in bucket_entry:
            result.append(entry.key)
        return result


def get_aws_object_version_listing(bucket, key):
    cmd_output = s3_list_object_versions(bucket, key)
    if len(cmd_output) == 0:
        return cmd_output
    return FileModel.parse_aws_object_versions(cmd_output)


def s3_list_object_versions(bucket, key, args=None):
    command = ['aws', 's3api', 'list-object-versions', '--bucket', bucket, '--prefix', key]
    return get_command_output(command, args=args, expected_status=0)[0]


def s3_object_size(bucket_name, key):
    s3 = boto3.resource('s3')
    obj = s3.Object(bucket_name, key)
    return obj.content_length


def s3_object_last_modified(bucket_name, key):
    s3 = boto3.resource('s3')
    obj = s3.Object(bucket_name, key)
    return obj.last_modified.astimezone(get_localzone()).replace(tzinfo=None)
