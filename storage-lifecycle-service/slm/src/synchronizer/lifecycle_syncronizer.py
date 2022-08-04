import fnmatch
import json
import os
import datetime

from slm.src.logger import AppLogger
import boto3

from slm.src.model.storage_lifecycle_sync_model import StorageLifecycleRuleActionItems

ISO_DATE_FORMAT = "%Y-%m-%dT%H:%M:%S.%fZ"

DESTINATION_STORAGE_CLASS_TAG = 'DESTINATION_STORAGE_CLASS'
CP_SLC_RULE_NAME_PREFIX = 'CP Storage Lifecycle Rule:'


class S3StorageLifecycleSynchronizer:

    STANDARD = "STANDARD"
    GLACIER = "GLACIER"
    GLACIER_IR = "GLACIER_IR"
    DEEP_ARCHIVE = "DEEP_ARCHIVE"
    DELETION = "DELETION"

    EMPTY_ACTION_ITEMS = StorageLifecycleRuleActionItems([GLACIER, GLACIER_IR, DEEP_ARCHIVE, DELETION])

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
            # TODO check that rule is valid and perform mapping to object
            files = self._list_objects_by_prefix_from_glob(storage, rule["pathGlob"])

            for folder in self._identify_subject_folders(files, rule["pathGlob"]):

                # to match object keys with glob we need to combine it with folder path
                # and get 'effective' glob like '{folder-path}/{glob}'
                # so with glob = '*.txt' we can match files like:
                # {folder-path}/{filename}.txt
                effective_glob = os.path.join(folder, rule["objectGlob"]) if rule["objectGlob"] else None

                subject_files = [
                    file for file in files
                    if file["Key"].startswith(folder)
                    and fnmatch.fnmatch(file["Key"], effective_glob)
                ] if effective_glob else files

                self._process_files(folder, files, subject_files, rule)

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

    def _process_files(self, folder, file_listing, subject_files, rule):
        transition_method = rule["transitionMethod"]
        resulted_action_items = self.EMPTY_ACTION_ITEMS.copy()

        if transition_method == "ONE_BY_ONE":
            for file in subject_files:
                resulted_action_items.merge(self._build_action_items(file["Key"], [file], file, rule))
        elif transition_method == "LATEST_FILE" or transition_method == "EARLIEST_FILE":
            criterion_file = self.define_criterion_file(rule, file_listing, folder, subject_files)
            resulted_action_items.merge(self._build_action_items(folder, subject_files, criterion_file, rule))

        self._apply_action_items(resulted_action_items)

    def _build_action_items(self, path, subject_files, criterion_file, rule):
        result = self.EMPTY_ACTION_ITEMS.copy()

        lifecycle_executions = self.api.load_lifecycle_rule_executions_by_path(
            rule["datastorageId"], rule["id"], path)
        for execution in lifecycle_executions:
            result.with_execution(self._check_rule_execution_progress(rule["storageId"], subject_files, execution))

        effective_transitions = self._define_effective_transitions(
            self._fetch_prolongation_for_path_or_default(path, rule), rule)

        # TODO should be rewritten with just rule.notification, when rule will be validated
        #      and object will be constructed ahead
        notification = self._define_notification(rule)

        if criterion_file:
            for transition in effective_transitions:
                transition_class = transition["storageClass"]
                timestamp_of_action = self.define_transition_effective_timepoint(criterion_file, transition)

                now = datetime.datetime.now()

                # Check if notification is needed
                date_to_check = now + datetime.timedelta(days=notification["notifyBeforeDays"])
                if notification["enabled"] and self._is_timestamp_within_date(date_to_check, timestamp_of_action):
                    notification_execution = next(
                        filter(lambda e: e["status"] == "NOTIFICATION_SENT" and e["storageClass"] == transition_class, lifecycle_executions), None
                    )
                    if not notification_execution or not self._is_timestamp_within_date(now, notification_execution["updated"]):
                        result.with_notification(path, transition_class, transition["transitionDate"])

                # Check if action is needed
                date_to_check = now
                if self._is_timestamp_within_date(date_to_check, timestamp_of_action):
                    action_execution = next(
                        filter(lambda e: e["status"] == "RUNNING" and e["storageClass"] == transition_class, lifecycle_executions), None
                    )
                    if not action_execution or not self._is_timestamp_within_date(now, action_execution["updated"]):
                        for file in subject_files:
                            result.with_transition(transition_class, file)

        return result

    @staticmethod
    def define_transition_effective_timepoint(criterion_file, transition):
        if transition["transitionDate"]:
            timestamp_of_action = transition["transitionDate"]
        elif transition["transitionAfterDays"]:
            timestamp_of_action = criterion_file["LastModified"] \
                                  + datetime.timedelta(days=transition["transitionAfterDays"])
        else:
            raise RuntimeError(
                "Malformed transition: date or days should be present. " + json.dumps(transition))
        return timestamp_of_action

    @staticmethod
    def _is_timestamp_within_date(date, timestamp):
        day_start = date.replace(hour=0, minute=0, second=0, microsecond=0)
        day_end = date.replace(hour=23, minute=59, second=59, microsecond=999)
        return day_start <= timestamp <= day_end

    def _apply_action_items(self, action_items):
        pass

    def _check_rule_execution_progress(self, storage_id, subject_files, execution):
        if not execution["status"]:
            raise RuntimeError("Malformed rule execution found: " + json.dumps(execution))

        timestamp = datetime.datetime.strptime(execution["updated"], ISO_DATE_FORMAT)
        if execution["status"] == "RUNNING":
            # Check that all files were moved to destination storage class and if not and 2 days are passed since
            # process was started - fail this execution
            all_files_moved = next(
                filter(lambda file: file["StorageClass"] != execution["storageClass"], subject_files),
                None
            ) is not None
            if all_files_moved:
                return self._update_execution(storage_id, execution, "SUCCESS")
            elif timestamp + datetime.timedelta(days=2) > datetime.datetime.now():
                return self._update_execution(storage_id, execution, "FAILED")
        return None

    def _update_execution(self, storage_id, execution, status):
        self.api.update_status_lifecycle_rule_execution(storage_id, execution["id"], status)
        execution["status"] = status
        execution["updated"] = datetime.datetime.now()
        return execution

    def _define_notification(self, rule):
        if rule["notification"]:
            return rule["notification"]
        else:
            raise NotImplemented("TODO: Do not forget to implement this part!")

    @staticmethod
    def define_criterion_file(rule, file_listing, folder, subject_files):
        transition_method = rule["transitionMethod"]
        criterion_files = subject_files
        if rule["transitionCriterion"] and rule["transitionCriterion"] == "MATCHING_FILES":
            transition_criterion_files_glob = os.path.join(folder, rule["transitionCriterion"]["value"])
            criterion_files = [
                file for file in file_listing
                if file["Key"].startswith(folder) and fnmatch.fnmatch(file["Key"], transition_criterion_files_glob)
            ]
        # Sort by date (reverse or not depends on transition method) and get first element or None
        return next(
            iter(
                sorted(criterion_files, key=lambda e: e["LastModified"], reverse=(transition_method == "LATEST_FILE"))),
            None
        )

    @staticmethod
    def _define_effective_transitions(file_prolongation, rule):
        def _define_transition(transition):
            resulted_transition = {"storageClass": transition["storageClass"]}
            if transition["transitionAfterDays"]:
                resulted_transition["transitionAfterDays"] = transition["transitionAfterDays"] + file_prolongation["days"]
            if transition["transitionDate"]:
                resulted_transition["transitionDate"] = \
                    datetime.datetime.strptime(transition["transitionDate"], ISO_DATE_FORMAT) \
                    + datetime.timedelta(days=file_prolongation["days"])
            return resulted_transition

        return [_define_transition(transition) for transition in rule["transitions"]]

    @staticmethod
    def _fetch_prolongation_for_path_or_default(path, rule):
        file_prolongation = {"days": 0}
        if rule["prolongations"]:
            file_prolongation = next(
                filter(lambda prolongation: prolongation["path"] == path, rule["prolongations"]),
                file_prolongation
            )
        return file_prolongation

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
