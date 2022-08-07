# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import os
import datetime

from slm.src.logger import AppLogger
import boto3

from slm.src.model.cloud_object_model import CloudObject

ROLE_ADMIN_ID = 1

DESTINATION_STORAGE_CLASS_TAG = 'DESTINATION_STORAGE_CLASS'
CP_SLC_RULE_NAME_PREFIX = 'CP Storage Lifecycle Rule:'


class StorageOperations:
    def prepare_bucket_if_needed(self, storage):
        pass

    def list_objects_by_prefix_from_glob(self, storage, glob_str):
        pass

    def process_files_on_cloud(self, storage, rule, folder, storage_class, files):
        pass


class S3StorageOperations(StorageOperations):

    STANDARD = "STANDARD"
    GLACIER = "GLACIER"
    GLACIER_IR = "GLACIER_IR"
    DEEP_ARCHIVE = "DEEP_ARCHIVE"
    DELETION = "DELETION"

    @staticmethod
    def construct_s3_slc_rule(storage_class):
        return {
            'ID': CP_SLC_RULE_NAME_PREFIX + storage_class,
            'Filter': {
                'Tag': {
                    'Key': DESTINATION_STORAGE_CLASS_TAG,
                    'Value': storage_class
                }
            },
            'Status': 'Enabled',
            'Transitions': [
                {
                    'Days': 0,
                    'StorageClass': storage_class
                },
            ],
            'NoncurrentVersionTransitions': [
                {
                    'NoncurrentDays': 0,
                    'StorageClass': storage_class,
                    'NewerNoncurrentVersions': 0
                },
            ]
        }

    S3_STORAGE_CLASS_TO_RULE = {
        GLACIER: construct_s3_slc_rule(GLACIER),
        DEEP_ARCHIVE: construct_s3_slc_rule(DEEP_ARCHIVE),
        GLACIER_IR: construct_s3_slc_rule(GLACIER_IR)
    }

    DELETION_RULE = {
        'ID': CP_SLC_RULE_NAME_PREFIX + DELETION,
        'Filter': {
            'Tag': {
                'Key': DESTINATION_STORAGE_CLASS_TAG,
                'Value': DELETION
            }
        },
        'Status': 'Enabled',
        'Expiration': {
            'Days': 0
        },
        'NoncurrentVersionExpiration': {
            'NoncurrentDays': 0,
            'NewerNoncurrentVersions': 0
        }
    }

    def __init__(self, config, cp_data_source, logger=AppLogger()):
        self.logger = logger
        self.cp_data_source = cp_data_source
        self._verify_config(config)
        self.config = config
        self.aws_s3_client = boto3.client("s3")
        self.aws_s3control_client = boto3.client("s3control")

    def prepare_bucket_if_needed(self, storage):
        existing_slc = self.aws_s3_client.get_bucket_lifecycle_configuration(Bucket=storage.path)
        cp_lsc_rules = [rule for rule in existing_slc['Rules'] if rule['ID'].startswith(CP_SLC_RULE_NAME_PREFIX)]

        if not cp_lsc_rules:
            self.logger.log("There are no S3 Lifecycle rules for storage: {}, will create it.".format(storage.path))
            slc_rules = existing_slc['Rules']
            for rule in self.S3_STORAGE_CLASS_TO_RULE.values():
                slc_rules.append(rule)
            slc_rules.append(self.DELETION_RULE)
            self.aws_s3_client.put_bucket_lifecycle_configuration(existing_slc)
        else:
            self.logger.log("There are already defined S3 Lifecycle rules for storage: {}.".format(storage.path))

    def list_objects_by_prefix_from_glob(self, storage, glob_str):
        def determinate_prefix(glob_str):
            if "*" in glob_str:
                return os.path.split(glob_str.split("*", 1)[0])
            else:
                return glob_str

        result = []
        prefix = determinate_prefix(glob_str)
        paginator = self.aws_s3_client.get_paginator('list_objects')
        page_iterator = paginator.paginate(Bucket=storage.path, Prefix=prefix)
        for page in page_iterator:
            for obj in page['Contents']:
                result.append(self._map_s3_obj_to_cloud_obj(obj))
        return result

    def process_files_on_cloud(self, storage, rule, folder, storage_class, files):
        # TODO check that file key is url encoded
        manifest_content = "\n".join(["{},{}".format(storage.path, file.path) for file in files])
        manifest_key = "_".join([storage.path, folder, "rule", rule.rule_id, storage_class, datetime.datetime.now(), ".csv"])
        manifest_object = self.aws_s3_client.put_object(
            Body=manifest_content,
            Bucket=self.config["system_bucket"],
            Key=manifest_key
        )

        s3_tagging_job = self.aws_s3control_client.create_job(
            AccountId=self.config["aws_account_id"],
            ConfirmationRequired=False,
            Operation={
                'S3PutObjectTagging': {
                    'TagSet': [
                        {
                            'Key': DESTINATION_STORAGE_CLASS_TAG,
                            'Value': storage_class
                        },
                    ]
                },
            },
            Report={
                'Bucket': self.config["system_bucket"],
                'Format': 'Report_CSV_20180820',
                'Enabled': True,
                'Prefix': self.config["report_prefix"],
                'ReportScope': 'FailedTasksOnly'
            },
            Manifest={
                'Spec': {
                    'Format': 'S3BatchOperations_CSV_20180820',
                    'Fields': ['Bucket', 'Key']
                },
                'Location': {
                    'ObjectArn': "".join(["arn:aws:s3:::", self.config["system_bucket"], manifest_key]),
                    'ETag': manifest_object["ETag"]
                }
            },
            Description='Cloud-Pipeline job for tagging s3 objects for transition with respect to cp lifecycle rule',
            Priority=1,
            RoleArn=self.config["role_arn"],
            Tags=[
                {
                    'Key': 'cp-storage-lifecycle-job',
                    'Value': 'true'
                },
            ]
        )

        s3_tagging_job_description = None
        for try_i in range(30):
            self.logger.log("Get Job status with try: {}".format(try_i))
            s3_tagging_job_description = self.aws_s3control_client.describe_job(
                AccountId=self.config["aws_account_id"],
                JobId=s3_tagging_job["JobId"]
            )
            if "Status" in s3_tagging_job_description and s3_tagging_job_description["Status"] == "Complete":
                self.logger.log("Job status: {}. Proceeding.".format(s3_tagging_job_description["Status"]))
                break
            else:
                self.logger.log("Job status: {}. Wait.".format(s3_tagging_job_description["Status"]))

        delete_manifest_object = self.aws_s3_client.delete_object(
            Bucket=self.config["system_bucket"],
            Key=manifest_key
        )

        if not s3_tagging_job_description or "Status" not in s3_tagging_job_description \
                or s3_tagging_job_description["Status"]:
            self.logger.log("Can't get Job status. Will delete manifest and skip.")
            return

    @staticmethod
    def _map_s3_obj_to_cloud_obj(s3_object):
        return CloudObject(s3_object["Key"], s3_object["LastModified"], s3_object["StorageClass"])

    def _verify_config(self, config):
        pass
