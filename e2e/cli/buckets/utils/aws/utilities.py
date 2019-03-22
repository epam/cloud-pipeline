# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import boto3
import json
from tzlocal import get_localzone
from common_utils.cmd_utils import *


def get_bucket_entries(bucket_name, key):
    s3 = boto3.resource('s3')
    bucket = s3.Bucket(bucket_name)
    return list(bucket.objects.filter(Prefix=key))


def s3_object_exists(bucket_name, key):
    bucket_entry = get_bucket_entries(bucket_name, key)
    if len(bucket_entry) > 0 and bucket_entry[0].key == key:
        return True
    return False


def s3_folder_exists(bucket_name, key):
    return len(list_s3_folder(bucket_name, key)) > 0


def list_s3_folder(bucket_name, key):
    if not key.endswith('/'):
        key = key + '/'
    bucket_entry = get_bucket_entries(bucket_name, key)
    result = []
    for entry in bucket_entry:
        result.append(entry.key)
    return result


def s3_ls(source, args, expected_status=0):
    command = ['aws', 's3', 'ls', source]
    return get_command_output(command, args=args, expected_status=expected_status)[0]


def s3_list_object_versions(bucket, key, args=None):
    command = ['aws', 's3api', 'list-object-versions', '--bucket', bucket, '--prefix', key]
    return get_command_output(command, args=args, expected_status=0)[0]


def s3_list_object_tags(bucket, key, version=None, args=None):
    command = ['aws', 's3api', 'get-object-tagging', '--bucket', bucket, '--key', key]
    if version:
        command.extend(['--version-id', version])
    stdout = get_command_output(command, args=args, expected_status=0)[0]
    tags = json.loads(''.join(stdout).strip())['TagSet']
    result = {}
    for tag in tags:
        result[tag['Key']] = tag['Value']
    return result


def s3_object_size(bucket_name, key):
    s3 = boto3.resource('s3')
    obj = s3.Object(bucket_name, key)
    return obj.content_length


def s3_object_last_modified(bucket_name, key):
    s3 = boto3.resource('s3')
    obj = s3.Object(bucket_name, key)
    return obj.last_modified.astimezone(get_localzone()).replace(tzinfo=None)


def s3_get_bucket_lifecycle(bucket):
    s3 = boto3.resource('s3')
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


def wait_for_bucket_creation(bucket_name):
    waiter = boto3.client('s3').get_waiter('bucket_exists')
    waiter.wait(
        Bucket=bucket_name,
        WaiterConfig={
            'Delay': 3,
            'MaxAttempts': 20
        }
    )


def wait_for_bucket_deletion(bucket_name):
    waiter = boto3.client('s3').get_waiter('bucket_not_exists')
    waiter.wait(
        Bucket=bucket_name,
        WaiterConfig={
            'Delay': 3,
            'MaxAttempts': 20
        }
    )
