import fnmatch
import os
import datetime

from slm.src.logger import AppLogger
import boto3

from slm.src.model.storage_lifecycle_sync_model import StorageLifecycleRuleActionItems


DESTINATION_STORAGE_CLASS_TAG = 'DESTINATION_STORAGE_CLASS'
CP_SLC_RULE_NAME_PREFIX = 'CP Storage Lifecycle Rule:'


class S3StorageLifecycleSynchronizer:

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

    def __init__(self, api, logger=AppLogger()):
        self.logger = logger
        self.api = api
        self.aws_client = boto3.client("s3")

    def sync_storage(self, storage):
        rules = self.api.load_lifecycle_rules_for_storage(storage.id)

        # No rules for storage exist - just skip it
        if not rules:
            return

        self._create_s3_lifecycle_policy_for_bucket_if_needed(storage)

        for rule in rules:
            files = self._list_objects_by_prefix_from_glob(storage, rule["pathGlob"])

            for folder in self._identify_subject_folders(files, rule["pathGlob"]):

                # to match object keys with glob we need to combine it with folder path
                # and get 'effective' glob like '{folder-path}/{glob}'
                # so with glob = '*.txt' we can match files like:
                # {folder-path}/{filename}.txt
                effective_glob = os.path.join(folder, rule["objectGlob"])

                subject_files = [
                    file for file in files
                    if file["Key"].startswith(folder)
                    and fnmatch.fnmatch(file["Key"], effective_glob)
                ]
                self._process_files(folder, subject_files, rule)

    def _create_s3_lifecycle_policy_for_bucket_if_needed(self, storage):
        existing_slc = self.aws_client.get_bucket_lifecycle_configuration(Bucket=storage.path)
        cp_lsc_rules = [rule for rule in existing_slc['Rules'] if rule['ID'].startswith(CP_SLC_RULE_NAME_PREFIX)]

        if not cp_lsc_rules:
            slc_rules = existing_slc['Rules']
            for rule in self.S3_STORAGE_CLASS_TO_RULE.values():
                slc_rules.append(rule)
            slc_rules.append(self.DELETION_RULE)
            self.aws_client.put_bucket_lifecycle_configuration(existing_slc)

    def _list_objects_by_prefix_from_glob(self, storage, glob_str):
        def determinate_prefix(glob_str):
            if "*" in glob_str:
                return os.path.split(glob_str.split("*", 1)[0])
            else:
                return glob_str

        result = []
        prefix = determinate_prefix(glob_str)
        paginator = self.aws_client.get_paginator('list_objects')
        page_iterator = paginator.paginate(Bucket=storage.path, Prefix=prefix)
        for page in page_iterator:
            for obj in page['Contents']:
                result.append(obj)
        return result

    def _process_files(self, folder, subject_files, rule):
        transition_method = rule["transitionMethod"]
        resulted_action_items = StorageLifecycleRuleActionItems(
            [self.GLACIER, self.GLACIER_IR, self.DEEP_ARCHIVE, self.DELETION]
        )

        if transition_method == "ONE_BY_ONE":
            lifecycle_executions = self.api.load_lifecycle_rule_executions(rule["datastorageId"], rule["id"])
            for file in subject_files:
                resulted_action_items.merge(self._build_action_items_for_file(file, lifecycle_executions, rule))

        elif transition_method == "LATEST_FILE" or transition_method == "EARLIEST_FILE":
            lifecycle_executions = self.api.load_lifecycle_rule_executions_by_path(
                rule["datastorageId"], rule["id"], folder)
            resulted_action_items.merge(self._build_action_items_for_files(subject_files, lifecycle_executions, rule))
            pass

        self._apply_action_items(resulted_action_items)

    def _build_action_items_for_file(self, file, lifecycle_executions, rule):
        return None

    def _build_action_items_for_files(self, files, lifecycle_executions, rule):
        return None

    def _apply_action_items(self, action_items):
        pass

    def check_rule_execution_progress(self, storage, subject_files, folder_executions):
        for execution in folder_executions:
            if execution["status"] and execution["status"] == "RUNNING":

                timestamp = datetime.datetime.strptime(execution["updated"], "%Y-%m-%dT%H:%M:%S.%fZ")
                if timestamp + datetime.timedelta(days=2) > datetime.datetime.now():
                    self.api.update_status_lifecycle_rule_execution(storage.id, execution["id"], "FAILED")
        return False

    @staticmethod
    def _identify_subject_folders(files, glob_str):
        def generate_all_possible_dir_paths(paths):
            def generate_hierarchy(path):
                result = set()
                interim_result = ""
                for path_part in path.split("/"):
                    interim_result += path_part
                    result.add(interim_result)
                return result

            result = set()
            for path in paths:
                result.union(generate_hierarchy(path))
            return result

        unique_folder_paths = set()
        for object in files:
            parent_dir, filename = os.path.split(object["Key"])
            unique_folder_paths.add(parent_dir)
        return [p for p in generate_all_possible_dir_paths(unique_folder_paths) if fnmatch.fnmatch(p, glob_str)]


class UnsupportedStorageLifecycleSynchronizer:

    def __init__(self, api, logger=AppLogger()):
        self.logger = logger
        self.api = api

    def sync_storage(self, storage):
        self.logger.log(
            "Lifecycle configuration for storage: {} with storage type: {} is not supported. Skipping.".format(
                storage.path, storage.type
            )
        )
