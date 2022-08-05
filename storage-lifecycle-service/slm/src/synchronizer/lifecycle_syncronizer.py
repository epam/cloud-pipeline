import fnmatch
import json
import os
import datetime

from slm.src.logger import AppLogger
import boto3

from slm.src.model.storage_lifecycle_rule_model import StorageLifecycleRuleProlongation, StorageLifecycleRuleTransition
from slm.src.model.storage_lifecycle_sync_model import StorageLifecycleRuleActionItems

ROLE_ADMIN_ID = 1

DESTINATION_STORAGE_CLASS_TAG = 'DESTINATION_STORAGE_CLASS'
CP_SLC_RULE_NAME_PREFIX = 'CP Storage Lifecycle Rule:'


def _is_timestamp_within_date(date, timestamp):
    day_start = date.replace(hour=0, minute=0, second=0, microsecond=0)
    day_end = date.replace(hour=23, minute=59, second=59, microsecond=999)
    return day_start <= timestamp <= day_end


def _rule_is_not_valid(rule):
    # TODO check that rule is valid
    pass


class S3StorageLifecycleSynchronizer:

    STANDARD = "STANDARD"
    GLACIER = "GLACIER"
    GLACIER_IR = "GLACIER_IR"
    DEEP_ARCHIVE = "DEEP_ARCHIVE"
    DELETION = "DELETION"

    EMPTY_ACTION_ITEMS = StorageLifecycleRuleActionItems(None, None, [GLACIER, GLACIER_IR, DEEP_ARCHIVE, DELETION])

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
        self.config = config
        self.aws_s3_client = boto3.client("s3")
        self.aws_s3control_client = boto3.client("s3control")

    def sync_storage(self, storage):
        rules = self.cp_data_source.load_lifecycle_rules_for_storage(storage.id)

        # No rules for storage exist - just skip it
        if not rules:
            self.logger.log("No rules for storage {} is defined, skipping".format(storage.path))
            return

        self._create_s3_lifecycle_policy_for_bucket_if_needed(storage)

        for rule in rules:
            if _rule_is_not_valid(rule):
                continue

            files = self._list_objects_by_prefix_from_glob(storage, rule.path_glob)

            for folder in self._identify_subject_folders(files, rule.path_glob):

                # to match object keys with glob we need to combine it with folder path
                # and get 'effective' glob like '{folder-path}/{glob}'
                # so with glob = '*.txt' we can match files like:
                # {folder-path}/{filename}.txt
                effective_glob = os.path.join(folder, rule.object_glob) if rule.object_glob else None

                subject_files = [
                    file for file in files
                    if file["Key"].startswith(folder)
                    and fnmatch.fnmatch(file["Key"], effective_glob)
                ] if effective_glob else files

                self._process_files(storage, folder, files, subject_files, rule)

    def _create_s3_lifecycle_policy_for_bucket_if_needed(self, storage):
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

    def _list_objects_by_prefix_from_glob(self, storage, glob_str):
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
                result.append(obj)
        return result

    def _process_files(self, storage, folder, file_listing, subject_files, rule):
        transition_method = rule.transition_method
        resulted_action_items = self.EMPTY_ACTION_ITEMS.copy().with_folder(folder).with_rule_id(rule.rule_id)

        if transition_method == "ONE_BY_ONE":
            resulted_action_items.with_mode("FILE")
            for file in subject_files:
                resulted_action_items.merge(self._build_action_items(file["Key"], [file], file, rule))
        elif transition_method == "LATEST_FILE" or transition_method == "EARLIEST_FILE":
            criterion_file = self.define_criterion_file(rule, file_listing, folder, subject_files)
            resulted_action_items.merge(self._build_action_items(folder, subject_files, criterion_file, rule))

        self._apply_action_items(storage, rule, resulted_action_items)

    def _build_action_items(self, path, subject_files, criterion_file, rule):
        result = self.EMPTY_ACTION_ITEMS.copy().with_rule_id(rule.rule_id)

        lifecycle_executions = self.cp_data_source.load_lifecycle_rule_executions_by_path(
            rule.datastorage_id, rule.rule_id, path)
        for execution in lifecycle_executions:
            result.with_execution(self._check_rule_execution_progress(rule.datastorage_id, subject_files, execution))

        effective_transitions = self._define_effective_transitions(
            self._fetch_prolongation_for_path_or_default(path, rule), rule)

        notification = rule.notification

        if criterion_file:
            for transition in effective_transitions:
                transition_class = transition.storage_class
                timestamp_of_action = self.define_transition_effective_timepoint(criterion_file, transition)

                now = datetime.datetime.now()

                # Check if notification is needed
                date_to_check = now + datetime.timedelta(days=notification.notify_before_days)
                if notification.enabled and _is_timestamp_within_date(date_to_check, timestamp_of_action):
                    notification_execution = next(
                        filter(lambda e: e.status == "NOTIFICATION_SENT" and e.storage_class == transition_class, lifecycle_executions), None
                    )
                    if not notification_execution or not _is_timestamp_within_date(now, notification_execution.updated):
                        result.with_notification(
                            path, transition_class, transition.transition_date, notification.prolong_days
                        )

                # Check if action is needed
                date_to_check = now
                if _is_timestamp_within_date(date_to_check, timestamp_of_action):
                    action_execution = next(
                        filter(lambda e: e.status == "RUNNING" and e.storage_class == transition_class, lifecycle_executions), None
                    )
                    if not action_execution or not _is_timestamp_within_date(now, action_execution.updated):
                        for file in subject_files:
                            result.with_transition(transition_class, file)

        return result

    def _apply_action_items(self, storage, rule, action_items):
        for notification_properties in action_items.notification_queue:
            self._send_notification(storage, rule, notification_properties)

        for execution in action_items.executions:
            if execution.status == "FAILED":
                self.logger.log("Execution failed: {}".format(json.dumps(execution)))

        for storage_class, files in action_items.destination_transitions_queues.items():
            if files:
                self.logger.log(
                    "Starting to tagging files for Storage Class: {}, number of files: {}".format(
                        storage_class, len(files)
                    )
                )
                self._tag_files(storage, rule, action_items.mode, action_items.folder, storage_class, files)

    def _check_rule_execution_progress(self, storage_id, subject_files, execution):
        if not execution.status:
            raise RuntimeError("Malformed rule execution found: " + json.dumps(execution))

        if execution.status == "RUNNING":
            # Check that all files were moved to destination storage class and if not and 2 days are passed since
            # process was started - fail this execution
            all_files_moved = next(
                filter(lambda file: file["StorageClass"] != execution.storage_class, subject_files),
                None
            ) is not None
            if all_files_moved:
                return self.cp_data_source.update_status_lifecycle_rule_execution(
                    storage_id, execution.execution_id, "SUCCESS")
            elif execution.updated + datetime.timedelta(days=2) > datetime.datetime.now():
                return self.cp_data_source.update_status_lifecycle_rule_execution(
                    storage_id, execution.execution_id, "FAILED")
        return None

    def _send_notification(self, storage, rule, notification_properties):
        def _prepare_massage():
            to_user_id = None
            if rule.notification.keep_informed_owner:
                loaded = self.cp_data_source.load_user_by_name(storage.owner)
                to_user_id = loaded["id"] if loaded else None

            cc_users = rule.notification.user_to_notify_ids if rule.notification.user_to_notify_ids else []
            if rule.notification.keep_informed_admins:
                role_admin = self.cp_data_source.load_role(ROLE_ADMIN_ID)
                if role_admin and "users" in role_admin:
                    cc_users.extend([user["id"] for user in role_admin["users"]])
                    if to_user_id is None:
                        to_user_id = next(iter(role_admin["users"]))

            notification_parameters = {
                "storageName": storage.name,
                "storageId": storage.id,
                "ruleId": rule.rule_id,
                "path": notification_properties["path"],
                "storageClass": notification_properties["storageClass"],
                "dateOfAction": notification_properties["date_of_action"],
                "prolongDays": notification_properties["prolong_days"]
            }

            return rule.notification.subject, rule.notification.body, to_user_id, cc_users, notification_parameters

        path = notification_properties["path"]
        storage_class = notification_properties["storage_class"]
        if not rule.notification.enabled:
            self.logger.log(
                "Notification disabled: rule {}. Notification for path {} and storage class {} won't be sent".format(
                    rule.rule_id, path, storage_class)
            )
            return

        self.cp_data_source.create_lifecycle_rule_execution(
            rule.datastorage_id, rule.rule_id,
            {
                "status": "NOTIFICATION_SENT",
                "path": path,
                "storageClass": storage_class
            }
        )
        subject, body, to_user, copy_users, parameters = _prepare_massage()
        self.cp_data_source.send_notification(subject, body, to_user, copy_users, parameters)

    def _tag_files(self, storage, rule, mode, folder, storage_class, files):

        def _create_or_update_execution(path):
            execution = self.cp_data_source.load_lifecycle_rule_executions_by_path(
                storage.id, rule.rule_id, path
            )
            if execution:
                self.cp_data_source.update_status_lifecycle_rule_execution(storage.id, execution["id"], "RUNNING")
            else:
                self.cp_data_source.create_lifecycle_rule_execution(
                    storage.id, rule.rule_id,
                    {
                        "status": "RUNNING",
                        "path": path,
                        "storageClass": storage_class
                    }
                )

        manifest_content = "\n".join(["{},{}".format(storage.path, file["Key"]) for file in files])
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

        if mode == "FILES":
            for file in files:
                _create_or_update_execution(file["Key"])
        else:
            _create_or_update_execution(folder)

    @staticmethod
    def define_transition_effective_timepoint(criterion_file, transition):
        if transition.transition_date:
            timestamp_of_action = transition.transition_date
        elif transition.transition_after_days:
            timestamp_of_action = criterion_file["LastModified"] \
                                  + datetime.timedelta(days=transition.transition_after_days)
        else:
            raise RuntimeError(
                "Malformed transition: date or days should be present. " + json.dumps(transition))
        return timestamp_of_action

    @staticmethod
    def define_criterion_file(rule, file_listing, folder, subject_files):
        transition_method = rule.transition_method
        criterion_files = subject_files
        if rule.transition_criterion.type == "MATCHING_FILES":
            transition_criterion_files_glob = os.path.join(folder, rule.transition_criterion.value)
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
    def _define_effective_transitions(prolongation, rule):
        def _define_transition(transition):
            resulted_transition = StorageLifecycleRuleTransition(transition.storage_class)
            if transition.transition_after_days:
                resulted_transition.transition_after_days = transition.transition_after_days + prolongation.days
            if transition.transition_date:
                resulted_transition.transition_date = \
                    transition.transition_date + datetime.timedelta(days=prolongation.days)
            return resulted_transition

        return [_define_transition(transition) for transition in rule.transitions]

    @staticmethod
    def _fetch_prolongation_for_path_or_default(path, rule):
        file_prolongation = StorageLifecycleRuleProlongation(prolongation_id=-1, prolonged_date=None, days=0, path=path)
        if rule.prolongations:
            file_prolongation = next(
                filter(lambda prolongation: prolongation.path == path, rule.prolongations),
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
