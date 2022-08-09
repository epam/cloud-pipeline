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
from time import sleep

from slm.src.logger import AppLogger
import boto3

from slm.src.model.cloud_object_model import CloudObject

ROLE_ADMIN_ID = 1

DESTINATION_STORAGE_CLASS_TAG = 'DESTINATION_STORAGE_CLASS'
CP_SLC_RULE_NAME_PREFIX = 'CP Storage Lifecycle Rule:'


class StorageOperations:
    def prepare_bucket_if_needed(self, bucket):
        pass

    def list_objects_by_prefix(self, bucket, glob_str):
        pass

    def process_files_on_cloud(self, bucket, region, rule, folder, storage_class, files):
        pass


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
                'Days': 1,
                'StorageClass': storage_class
            },
        ],
        'NoncurrentVersionTransitions': [
            {
                'NoncurrentDays': 0,
                'StorageClass': storage_class
            },
        ]
    }


class S3StorageOperations(StorageOperations):

    STANDARD = "STANDARD"
    GLACIER = "GLACIER"
    GLACIER_IR = "GLACIER_IR"
    DEEP_ARCHIVE = "DEEP_ARCHIVE"
    DELETION = "DELETION"

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
            'Days': 1
        },
        'NoncurrentVersionExpiration': {
            'NoncurrentDays': 1
        }
    }

    def __init__(self, config, cp_data_source, logger=AppLogger()):
        self.logger = logger
        self.cp_data_source = cp_data_source
        self._verify_config(config)
        self.config = config
        self.aws_s3_client = boto3.client("s3")

    def prepare_bucket_if_needed(self, bucket):
        existing_slc = self.aws_s3_client.get_bucket_lifecycle_configuration(Bucket=bucket)
        cp_lsc_rules = [rule for rule in existing_slc['Rules'] if rule['ID'].startswith(CP_SLC_RULE_NAME_PREFIX)]

        if not cp_lsc_rules:
            self.logger.log("There are no S3 Lifecycle rules for storage: {}, will create it.".format(bucket))
            slc_rules = existing_slc['Rules']
            for rule in self.S3_STORAGE_CLASS_TO_RULE.values():
                slc_rules.append(rule)
            slc_rules.append(self.DELETION_RULE)
            self.aws_s3_client.put_bucket_lifecycle_configuration(
                Bucket=bucket,
                LifecycleConfiguration={"Rules": slc_rules})
        else:
            self.logger.log("There are already defined S3 Lifecycle rules for storage: {}.".format(bucket))

    def list_objects_by_prefix(self, bucket, prefix, convert_paths=True):
        result = []
        paginator = self.aws_s3_client.get_paginator('list_objects')
        page_iterator = paginator.paginate(Bucket=bucket, Prefix=self._path_to_s3_format(prefix))
        for page in page_iterator:
            if 'Contents' in page:
                for obj in page['Contents']:
                    result.append(self._map_s3_obj_to_cloud_obj(obj, convert_paths))
        return result

    def process_files_on_cloud(self, bucket, region, rule, folder, storage_class, files):
        aws_s3control_client = boto3.client("s3control", region_name=region)
        manifest_content = "\n".join(["{},{}".format(bucket, self._path_to_s3_format(file.path)) for file in files])
        manifest_key = "_".join([
            bucket, folder, "rule", str(rule.rule_id),
            storage_class, str(datetime.datetime.utcnow()).replace(" ", "_").replace(":", "_"),
            ".csv"
        ])
        manifest_object = self.aws_s3_client.put_object(
            Body=manifest_content,
            Bucket=self.config["system_bucket"],
            Key=manifest_key,
            ServerSideEncryption='AES256'
        )

        s3_tagging_job = aws_s3control_client.create_job(
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
                'Bucket': "arn:aws:s3:::" + self.config["system_bucket"],
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
                    'ObjectArn': "".join(["arn:aws:s3:::", self.config["system_bucket"], "/", manifest_key]),
                    'ETag': manifest_object["ETag"]
                }
            },
            Priority=1,
            RoleArn=self.config["role_arn"]
        )

        s3_tagging_job_description = None
        for try_i in range(30):
            self.logger.log("Get Job {} status with try: {}".format(s3_tagging_job["JobId"], try_i))
            s3_tagging_job_description = aws_s3control_client.describe_job(
                AccountId=self.config["aws_account_id"],
                JobId=s3_tagging_job["JobId"]
            )
            if "Job" in s3_tagging_job_description and "Status" in s3_tagging_job_description["Job"]:
                if s3_tagging_job_description["Job"]["Status"] == "Complete":
                    self.logger.log("Job status: {}. Proceeding.".format(s3_tagging_job_description["Job"]["Status"]))
                    break
                else:
                    self.logger.log("Job status: {}. Wait.".format(s3_tagging_job_description["Job"]["Status"]))
            sleep(5)

        if not s3_tagging_job_description or "Job" not in s3_tagging_job_description or \
                "Status" not in s3_tagging_job_description["Job"] \
                or s3_tagging_job_description["Job"]["Status"] != "Complete" \
                or "ProgressSummary" not in s3_tagging_job_description["Job"] \
                or s3_tagging_job_description["Job"]["ProgressSummary"]["NumberOfTasksFailed"] > 0:
            self.logger.log(
                "Can't get Job status. Will keep manifest and report and skip. Task summary: {}".format(
                    str(s3_tagging_job_description))
            )
            return False

        self._clean_up_after_job(manifest_key, s3_tagging_job["JobId"])
        return True

    def _clean_up_after_job(self, manifest_key, job_id):
        job_report_dir = os.path.join(self.config["report_prefix"], "job-" + job_id)
        job_related_files = self.list_objects_by_prefix(self.config["system_bucket"], job_report_dir, False)
        for file in job_related_files:
            self.aws_s3_client.delete_object(
                Bucket=self.config["system_bucket"],
                Key=file.path
            )
        self.aws_s3_client.delete_object(
            Bucket=self.config["system_bucket"],
            Key=manifest_key
        )

    @staticmethod
    def _map_s3_obj_to_cloud_obj(s3_object, convert_path):
        return CloudObject(
            S3StorageOperations._path_from_s3_format(s3_object["Key"]) if convert_path else s3_object["Key"],
            s3_object["LastModified"],
            s3_object["StorageClass"]
        )

    @staticmethod
    def _verify_config(config):
        if "aws_account_id" not in config:
            raise RuntimeError("Please provide aws_account_id within --aws configuration option")
        if "system_bucket" not in config:
            raise RuntimeError("Please provide system_bucket within --aws configuration option")
        if "role_arn" not in config:
            raise RuntimeError("Please provide role_arn within --aws configuration option")
        if "report_prefix" not in config:
            config["report_prefix"] = "cp_storage_lifecycle_tagging_report"
        config["report_prefix"] = config["report_prefix"].strip("/")

    @staticmethod
    def _path_from_s3_format(path):
        return "/" + path if not path.startswith("/") else path

    @staticmethod
    def _path_to_s3_format(path):
        return path.replace("/", "", 1) if path.startswith("/") else path