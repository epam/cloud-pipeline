#  Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
#  #
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#  #
#     http://www.apache.org/licenses/LICENSE-2.0
#  #
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

import datetime
import os
import re
import urllib
from time import sleep

import boto3
import botocore
from botocore.credentials import AssumeRoleCredentialFetcher, DeferredRefreshableCredentials
from botocore.exceptions import ClientError

from sls.cloud.cloud import StorageOperations
from sls.cloud.model.cloud_object_model import CloudObject

DESTINATION_STORAGE_CLASS_TAG = 'DESTINATION_STORAGE_CLASS'
CP_SLC_RULE_NAME_PREFIX = 'CP Storage Lifecycle Rule:'


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

    EXPEDITED_RESTORE_MODE = "EXPEDITED"
    STANDARD_RESTORE_MODE = "STANDARD"
    BULK_RESTORE_MODE = "BULK"

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

    def __init__(self, logger):
        self.logger = logger

    def prepare_bucket_if_needed(self, region, storage_container):
        bucket = storage_container.bucket
        s3_client = self._build_s3_client(region, storage_container, "s3")
        try:
            existing_slc = s3_client.get_bucket_lifecycle_configuration(Bucket=bucket)
        except ClientError as e:
            self.logger.log("Cannot load BucketLifecycleConfiguration, seems it doesn't exist. {}".format(e))
            existing_slc = {'Rules': []}

        cp_lsc_rules = [rule for rule in existing_slc['Rules'] if rule['ID'].startswith(CP_SLC_RULE_NAME_PREFIX)]

        if not cp_lsc_rules:
            self.logger.log("There are no S3 Lifecycle rules for storage: {}, will create it.".format(bucket))
            slc_rules = existing_slc['Rules']
            for rule in self.S3_STORAGE_CLASS_TO_RULE.values():
                slc_rules.append(rule)
            slc_rules.append(self.DELETION_RULE)
            s3_client.put_bucket_lifecycle_configuration(
                Bucket=bucket,
                LifecycleConfiguration={"Rules": slc_rules})
        else:
            self.logger.log("There are already defined S3 Lifecycle rules for storage: {}.".format(bucket))

    def list_objects_by_prefix(self, region, storage_container, list_versions=False, convert_paths=True):
        return self._list_objects_by_prefix(
            self._build_s3_client(region, storage_container, "s3"),
            storage_container.bucket, storage_container.bucket_prefix, list_versions, convert_paths)

    def _list_objects_by_prefix(self, s3_client, bucket, prefix, list_versions=False, convert_paths=True):
        result = []
        paginator = s3_client.get_paginator('list_objects' if not list_versions else 'list_object_versions')
        page_iterator = paginator.paginate(Bucket=bucket, Prefix=self._path_to_s3_format(prefix))
        for page in page_iterator:
            if not list_versions:
                if 'Contents' in page:
                    for obj in page['Contents']:
                        result.append(self._map_s3_obj_to_cloud_obj(obj, convert_paths))
            else:
                if 'Versions' in page:
                    for version in page['Versions']:
                        result.append(self._map_s3_obj_to_cloud_obj(version, convert_paths))
        return result

    def tag_files_to_transit(self, region, storage_container, files, storage_class, transit_id):
        if not files:
            self.logger.log("No files to tag!")
            return None
        tag_operation = {
            'S3PutObjectTagging': {
                'TagSet': [
                    {
                        'Key': DESTINATION_STORAGE_CLASS_TAG,
                        'Value': storage_class
                    },
                ]
            }
        }
        job_description = self._run_s3_batch_operation(region, storage_container, files, tag_operation, transit_id)
        return self._is_s3_batch_operation_succeeded(job_description)

    def run_files_restore(self, region, storage_container, files, days, restore_tear, restore_operation_id):
        archived_files_to_restore = [f for f in files if f.storage_class != self.STANDARD]
        if not archived_files_to_restore:
            self.logger.log("No files to tag!")
            return {
                "status": False,
                "reason": "No files to perform restore!",
                "value": None
            }

        self.logger.log("Filtered '{}' actual archived files to restore.".format(len(archived_files_to_restore)))
        restore_operation = {
            'S3InitiateRestoreObject': {
                'ExpirationInDays': days,
                'GlacierJobTier': restore_tear
            }
        }
        job_description = self._run_s3_batch_operation(
            region, storage_container, archived_files_to_restore, restore_operation, restore_operation_id)
        return {
            "status": self._is_s3_batch_operation_succeeded(job_description),
            "reason": job_description,
            "value": None
        }

    def check_files_restore(self, region, storage_container, files, restore_timestamp, restore_mode):
        bucket = storage_container.bucket

        archived_files_to_check = [f for f in files if f.storage_class != self.STANDARD]
        if archived_files_to_check:
            storage_class = archived_files_to_check[0].storage_class
        else:
            self.logger.log("There are no files to check but restore action is in progress! "
                            "Will fail restore action. Restore process start: {}. restore mode: {}."
                            .format(restore_timestamp, restore_mode))
            return {
                "status": True,
                "value": None,
                "reason": "There are no files to check but restore action is in progress!"
            }

        is_restore_possibly_ready = self._check_if_restore_could_be_ready(
            storage_class, restore_timestamp, restore_mode)
        restored_till_value = None
        if not is_restore_possibly_ready:
            self.logger.log("Probably files is steel restoring because appropriate period of time is not passed. "
                            "Will not send head request for each file. Restore process start: {}. restore mode: {}."
                            .format(restore_timestamp, restore_mode))
            return {
                "status": False,
                "value": None,
                "reason": "Probably files is steel restoring because appropriate period of time is not passed."
            }
        else:
            s3_client = self._build_s3_client(region, storage_container, "s3")
            self.logger.log("Files may be ready, so will check each file. "
                            "Last update time: {}. restore mode: {}.".format(restore_timestamp, restore_mode))
            for file in archived_files_to_check:
                if file.version_id:
                    result = s3_client.head_object(Bucket=bucket, Key=file.path, VersionId=file.version_id)
                else:
                    result = s3_client.head_object(Bucket=bucket, Key=file.path)
                if not result:
                    self.logger.log(
                        "Cannot get head of object: '{}' in bucket: {}"
                        .format(file.path, bucket))
                    return {
                        "status": False,
                        "value": None,
                        "reason": "Cannot get head for: '{}' bucket: {}, or 'Restore' property is not set"
                        .format(file.path, bucket)
                    }
                else:
                    if "Restore" not in result:
                        continue

                    restoring_status_match = re.match('.*expiry-date=["\'](.*)["\']', result["Restore"])
                    if not restoring_status_match:
                        return {
                            "status": False,
                            "value": None,
                            "reason": "Status is {} for file: '{}' bucket: {}"
                            .format(result["Restore"], file.path, bucket)
                        }
                    else:
                        file_restored_till_value = datetime.datetime.strptime(
                            restoring_status_match.group(1), "%a, %d %b %Y %H:%M:%S %Z")
                        if restored_till_value is None or file_restored_till_value < restored_till_value:
                            restored_till_value = file_restored_till_value

            return {
                "status": True,
                "value": restored_till_value.strftime("%Y-%m-%d %H:%M:%S.000"),
                "reason": "All files are ready!"
            }

    def get_storage_class_transition_map(self, transition_storage_classes):
        possible_storage_classes = ["GLACIER_IR", "GLACIER", "DEEP_ARCHIVE", "DELETION"]
        storage_class_road_map = {}
        source_storage_classes = ["STANDARD"]
        for storage_class in possible_storage_classes:
            if storage_class not in transition_storage_classes:
                source_storage_classes.append(storage_class)
            else:
                storage_class_road_map[storage_class] = source_storage_classes
                source_storage_classes = [storage_class]
        return storage_class_road_map

    def _run_s3_batch_operation(self, region, storage_container, files, operation_obj, operation_id):
        bucket = storage_container.bucket
        sls_properties = region.storage_lifecycle_service_properties.properties
        s3_batch_ops_report_bucket = sls_properties["batch_operation_job_report_bucket"]

        s3_client = self._build_s3_client(region, storage_container, "s3")
        aws_s3control_client = self._build_s3_client(region, storage_container, "s3control")

        is_files_versioned = True if next(iter(files), None).version_id else False
        manifest_content = "\n".join(
            ["{},{}".format(bucket, urllib.parse.quote(self._path_to_s3_format(file.path)))
             if not is_files_versioned
             else
             "{},{},{}".format(bucket, urllib.parse.quote(self._path_to_s3_format(file.path)), file.version_id)
             for file in files]
        )

        job_location_prefix = os.path.join(sls_properties["batch_operation_job_report_bucket_prefix"], bucket, operation_id)

        manifest_key = os.path.join(
            job_location_prefix,
            "_".join([bucket, operation_id,
                      str(datetime.datetime.utcnow()), ".csv"]).replace(" ", "_").replace(":", "_").replace("/", ".")
        )
        manifest_object = s3_client.put_object(
            Body=manifest_content,
            Bucket=s3_batch_ops_report_bucket,
            Key=manifest_key,
            ServerSideEncryption='AES256'
        )

        s3_batch_operation_job = aws_s3control_client.create_job(
            AccountId=sls_properties["batch_operation_job_aws_account_id"],
            ConfirmationRequired=False,
            Operation=operation_obj,
            Report={
                'Bucket': "arn:aws:s3:::" + s3_batch_ops_report_bucket,
                'Format': 'Report_CSV_20180820',
                'Enabled': True,
                'Prefix': job_location_prefix,
                'ReportScope': 'FailedTasksOnly'
            },
            Manifest={
                'Spec': {
                    'Format': 'S3BatchOperations_CSV_20180820',
                    'Fields': ['Bucket', 'Key', 'VersionId'] if is_files_versioned else ['Bucket', 'Key']
                },
                'Location': {
                    'ObjectArn': "".join(
                        ["arn:aws:s3:::", s3_batch_ops_report_bucket, "/", manifest_key]),
                    'ETag': manifest_object["ETag"]
                }
            },
            Priority=1,
            RoleArn=sls_properties["batch_operation_job_role_arn"]
        )

        s3_batch_operation_job_description = None
        for try_i in range(sls_properties["batch_operation_job_poll_status_retry_count"]):
            self.logger.log("Get Job {} status with try: {}".format(s3_batch_operation_job["JobId"], try_i))
            s3_batch_operation_job_description = aws_s3control_client.describe_job(
                AccountId=sls_properties["batch_operation_job_aws_account_id"],
                JobId=s3_batch_operation_job["JobId"]
            )
            if "Job" in s3_batch_operation_job_description and "Status" in s3_batch_operation_job_description["Job"]:
                if s3_batch_operation_job_description["Job"]["Status"] == "Complete" or s3_batch_operation_job_description["Job"]["Status"] == "Failed":
                    self.logger.log("Job status: {}. Proceeding.".format(s3_batch_operation_job_description["Job"]["Status"]))
                    break
                else:
                    self.logger.log("Job status: {}. Wait.".format(s3_batch_operation_job_description["Job"]["Status"]))
            sleep(sls_properties["batch_operation_job_poll_status_sleep_sec"])

        if not self._is_s3_batch_operation_succeeded(s3_batch_operation_job_description):
            self.logger.log(
                "Job status is not successful. Will keep manifest and report and skip. Task summary: {}".format(
                    str(s3_batch_operation_job_description))
            )
        else:
            self._clean_up_after_job(s3_client, s3_batch_ops_report_bucket,
                                     job_location_prefix, manifest_key, s3_batch_operation_job["JobId"])

        return s3_batch_operation_job_description

    def _clean_up_after_job(self, s3_client, report_bucket, job_location_prefix, manifest_key, job_id):
        job_report_dir = os.path.join(job_location_prefix, "job-" + job_id)
        job_related_files = self._list_objects_by_prefix(s3_client, report_bucket, job_report_dir,
                                                         list_versions=False, convert_paths=False)
        for file in job_related_files:
            s3_client.delete_object(Bucket=report_bucket, Key=file.path)
        s3_client.delete_object(Bucket=report_bucket, Key=manifest_key)

    @staticmethod
    def _build_s3_client(region, storage_container, client_type):

        def _fetch_role_arn_for_storage():
            role_arn = None
            storage_cloud_attrs = storage_container.storage.cloud_specific_attributes
            region_cloud_attrs = region.cloud_specific_attributes
            if region_cloud_attrs:
                if storage_cloud_attrs.is_use_assumed_credentials:
                    role_arn = storage_cloud_attrs.temp_credentials_role \
                        if storage_cloud_attrs.temp_credentials_role else region_cloud_attrs.temp_credentials_role
                elif region_cloud_attrs.iam_role:
                    role_arn = region_cloud_attrs.iam_role
            return role_arn

        def get_boto3_session(assume_role_arn=None, profile=None):
            def _get_client_creator(_session):
                def client_creator(service_name, **kwargs):
                    return _session.client(service_name, **kwargs)

                return client_creator

            if not assume_role_arn:
                return boto3.Session(profile_name=profile) \
                    if profile is not None \
                    else boto3.Session()

            session = boto3.Session()
            fetcher = AssumeRoleCredentialFetcher(
                client_creator=_get_client_creator(session),
                source_credentials=session.get_credentials(),
                role_arn=assume_role_arn,
            )
            botocore_session = botocore.session.Session()
            botocore_session._credentials = DeferredRefreshableCredentials(
                method='assume-role', refresh_using=fetcher.fetch_credentials
            )
            return boto3.Session(botocore_session=botocore_session)

        return get_boto3_session(
            assume_role_arn=_fetch_role_arn_for_storage(),
            profile=region.cloud_specific_attributes.profile
        ).client(client_type, region_name=region.region_id)

    @staticmethod
    def _is_s3_batch_operation_succeeded(s3_batch_operation_job_description):
        return s3_batch_operation_job_description and "Job" in s3_batch_operation_job_description and \
               "Status" in s3_batch_operation_job_description["Job"] \
               and s3_batch_operation_job_description["Job"]["Status"] == "Complete" \
               and "ProgressSummary" in s3_batch_operation_job_description["Job"] \
               and s3_batch_operation_job_description["Job"]["ProgressSummary"]["NumberOfTasksFailed"] == 0

    @staticmethod
    def _map_s3_obj_to_cloud_obj(s3_object, convert_path):
        return CloudObject(
            S3StorageOperations._path_from_s3_format(s3_object["Key"]) if convert_path else s3_object["Key"],
            s3_object["LastModified"],
            s3_object["StorageClass"],
            s3_object["VersionId"] if 'VersionId' in s3_object else None
        )

    @staticmethod
    def _path_from_s3_format(path):
        return "/" + path if not path.startswith("/") else path

    @staticmethod
    def _path_to_s3_format(path):
        return path.replace("/", "", 1) if path.startswith("/") else path

    # For more information on periods see:
    # https://docs.aws.amazon.com/AmazonS3/latest/userguide/restoring-objects-retrieval-options.html
    def _check_if_restore_could_be_ready(self, storage_class, updated, restore_mode):
        if not storage_class or not updated:
            return False
        now = datetime.datetime.now(datetime.timezone.utc)
        restore_mode = self.STANDARD_RESTORE_MODE if not restore_mode else restore_mode
        check_shift_period = datetime.timedelta(hours=12)
        if storage_class == self.GLACIER or storage_class == self.GLACIER_IR:
            if restore_mode == self.BULK_RESTORE_MODE:
                check_shift_period = datetime.timedelta(hours=12)
            elif restore_mode == self.STANDARD_RESTORE_MODE:
                check_shift_period = datetime.timedelta(hours=5)
        if storage_class == self.DEEP_ARCHIVE:
            if restore_mode == self.BULK_RESTORE_MODE:
                check_shift_period = datetime.timedelta(hours=48)
            elif restore_mode == self.STANDARD_RESTORE_MODE:
                check_shift_period = datetime.timedelta(hours=12)
        return updated + check_shift_period < now
