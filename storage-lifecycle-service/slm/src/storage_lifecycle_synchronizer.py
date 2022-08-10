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

import datetime
import fnmatch
import json
import os
import re

from slm.src.model.storage_lifecycle_rule_model import StorageLifecycleRuleProlongation, StorageLifecycleRuleTransition
from slm.src.model.storage_lifecycle_sync_model import StorageLifecycleRuleActionItems
from slm.src.util.date_utils import is_timestamp_after_date, is_timestamp_before_date
from slm.src.util.path_utils import determinate_prefix_from_glob

CRITERION_MATCHING_FILES = "MATCHING_FILES"
CRITERION_DEFAULT = "DEFAULT"

ACTIONS_MODE_FILES = "FILES"
ACTIONS_MODE_FOLDER = "FOLDER"

METHOD_ONE_BY_ONE = "ONE_BY_ONE"
METHOD_EARLIEST_FILE = "EARLIEST_FILE"
METHOD_LATEST_FILE = "LATEST_FILE"

EXECUTION_NOTIFICATION_SENT_STATUS = "NOTIFICATION_SENT"
EXECUTION_RUNNING_STATUS = "RUNNING"
EXECUTION_SUCCESS_STATUS = "SUCCESS"
EXECUTION_FAILED_STATUS = "FAILED"

ROLE_ADMIN_ID = 1


class StorageLifecycleSynchronizer:

    def __init__(self, synchronizer_config, cp_data_source, cloud_operations, logger):
        self.synchronizer_config = synchronizer_config
        self.cloud_operations = cloud_operations
        self.cp_data_source = cp_data_source
        self.logger = logger

    def sync(self):
        self.logger.log("Starting object lifecycle synchronization process...")
        available_storages = self.cp_data_source.load_available_storages()
        self.logger.log("{} storages loaded.".format(len(available_storages)))

        regions_by_id = {region.id: region.region_id for region in self.cp_data_source.load_regions()}

        for storage in available_storages:
            storage.region_name = regions_by_id[storage.region_id]
            self.logger.log(
                "Starting object lifecycle synchronization process for {} with type {}.".format(
                    storage.path, storage.storage_type)
            )
            self._sync_storage(storage)
            self.logger.log(
                "Finish object lifecycle synchronization process for {} with type {}.".format(
                    storage.path, storage.storage_type)
            )
        self.logger.log("Done object lifecycle synchronization process...")

    def _sync_storage(self, storage):
        rules = self.cp_data_source.load_lifecycle_rules_for_storage(storage.id)

        # No rules for storage exist - just skip it
        if not rules:
            self.logger.log("No rules for storage {} is defined, skipping".format(storage.path))
            return

        if storage.storage_type not in self.cloud_operations:
            self.logger.log(
                "Lifecycle rules feature is not implemented for storage with type {}.".format(storage.storage_type)
            )

        self.cloud_operations[storage.storage_type].prepare_bucket_if_needed(storage.path)

        files_by_prefix = {}

        for rule in rules:
            self.logger.log("Storage: {}. Rule: {}. [Applying]".format(storage.id, rule.rule_id))
            try:
                if self._rule_is_not_valid(rule):
                    continue

                path_prefix = determinate_prefix_from_glob(rule.path_glob)
                existing_listing_prefix = next(filter(lambda k: path_prefix.startswith(k), files_by_prefix.keys()), None)
                if existing_listing_prefix:
                    files = [f for f in files_by_prefix[existing_listing_prefix] if f.path.startswith(path_prefix)]
                else:
                    files = self.cloud_operations[storage.storage_type]\
                        .list_objects_by_prefix(storage.path, path_prefix)
                    files_by_prefix[path_prefix] = files

                # TODO fix situation when we can't say: pathGlob: root/* - and it should means any directory in root
                subject_folders = self._identify_subject_folders(files, rule.path_glob)
                self.logger.log(
                    "Storage: {}. Rule: {}. Subject folders are: {}".format(storage.id, rule.rule_id, subject_folders))

                for folder in subject_folders:
                    self.logger.log(
                        "Storage: {}. Rule: {}, Path: {}. [Applying]".format(storage.id, rule.rule_id, folder))

                    # to match object keys with glob we need to combine it with folder path
                    # and get 'effective' glob like '{folder-path}/{glob}'
                    # so with glob = '*.txt' we can match files like:
                    # {folder-path}/{filename}.txt
                    effective_glob = os.path.join(folder, rule.object_glob) if rule.object_glob else None

                    self.logger.log(
                        "Storage: {}. Rule: {}. Path: {}. Effective glob is '{}'".format(
                            storage.id, rule.rule_id, folder, effective_glob)
                    )

                    rule_subject_files = [
                        file for file in files
                        if file.path.startswith(folder)
                        and fnmatch.fnmatch(file.path, effective_glob)
                    ] if effective_glob else files
                    self.logger.log(
                        "Storage: {}. Rule: {}. Path: {}. Found {} subject files.".format(
                            storage.id, rule.rule_id, folder, len(rule_subject_files))
                    )

                    self._process_files(storage, folder, files, rule_subject_files, rule)
                    self.logger.log("Storage: {}. Rule: {}, Path: {}. [Done]".format(storage.id, rule.rule_id, folder))
                self.logger.log("Storage: {}. Rule: {}. [Done]".format(storage.id, rule.rule_id))
            except Exception as e:
                self.logger.log(
                    "Storage: {}. Rule: {}. Problems to apply. Cause: {}".format(storage.id, rule.rule_id, str(e)))

    def _process_files(self, storage, folder, file_listing, rule_subject_files, rule):
        transition_method = rule.transition_method
        resulted_action_items = StorageLifecycleRuleActionItems(
            None, None, []).with_folder(folder).with_rule_id(rule.rule_id)

        if transition_method == METHOD_ONE_BY_ONE:
            resulted_action_items.with_mode(ACTIONS_MODE_FILES)
            for file in rule_subject_files:
                resulted_action_items.merge(self._build_action_items(file.path, [file], file, rule))
        elif transition_method == METHOD_LATEST_FILE or transition_method == METHOD_EARLIEST_FILE:
            criterion_file = self.define_criterion_file(rule, file_listing, folder, rule_subject_files)
            resulted_action_items.merge(self._build_action_items(folder, rule_subject_files, criterion_file, rule))

        self._apply_action_items(storage, rule, resulted_action_items)

    def _build_action_items(self, path, rule_subject_files, criterion_file, rule):
        result = StorageLifecycleRuleActionItems().with_rule_id(rule.rule_id)

        if not criterion_file:
            return result

        effective_transitions = self._define_effective_transitions(
            self._fetch_prolongation_for_path_or_default(path, rule), rule)

        rule_executions = self.cp_data_source.load_lifecycle_rule_executions_by_path(
            rule.datastorage_id, rule.rule_id, path)

        self.logger.log(
            "Storage: {}. Rule: {}. Path: {}. Found {} executions.".format(
                rule.datastorage_id, rule.rule_id, path, len(rule_executions)))

        for execution_for_transition in rule_executions:
            updated_execution = self._check_rule_execution_progress(
                rule.datastorage_id, rule_subject_files, execution_for_transition
            )
            if updated_execution:
                result.with_execution(updated_execution)

        notification = rule.notification
        for transition in effective_transitions:
            transition_class = transition.storage_class
            timestamp_of_action = self.define_transition_effective_timepoint(criterion_file, transition)
            trn_subject_file = [f for f in rule_subject_files if f.storage_class != transition_class]

            self.logger.log(
                "Storage: {}. Rule: {}. Path: {}. Transition: {}. Found {} files to transit, "
                "timepoint of planned action: {}.".format(
                    rule.datastorage_id, rule.rule_id, path, transition_class,
                    len(trn_subject_file), timestamp_of_action))

            execution_for_transition = next(
                filter(lambda e: e.storage_class == transition_class, rule_executions), None
            )

            now = datetime.datetime.now(datetime.timezone.utc)

            # Check if notification is needed
            if self._notification_should_be_sent(notification, execution_for_transition,
                                                 trn_subject_file, timestamp_of_action, now):
                self.logger.log("Storage: {}. Rule: {}. Path: {}. Transition: {}. Notification will be sent.".format(
                    rule.datastorage_id, rule.rule_id, path, transition_class, len(trn_subject_file)))
                result.with_notification(path, transition_class, transition.transition_date, notification.prolong_days)

            # Check if action is needed
            if is_timestamp_after_date(timestamp_of_action, now):
                if not trn_subject_file:
                    self.logger.log("Storage: {}. Rule: {}. Path: {}. Transition: {}. No file to transit.".format(
                            rule.datastorage_id, rule.rule_id, path, transition_class))
                    continue
                if execution_for_transition and execution_for_transition.status == EXECUTION_RUNNING_STATUS:
                    self.logger.log("Storage: {}. Rule: {}. Path: {}. Transition: {}. "
                                    "No need for action: execution is in RUNNING state.".format(
                                        rule.datastorage_id, rule.rule_id, path, transition_class))
                    continue

                self.logger.log(
                    "Storage: {}. Rule: {}. Path: {}. Transition: {}. All criteria are met. Will transit {} files."
                    .format(rule.datastorage_id, rule.rule_id, path, transition_class, len(trn_subject_file)))

                for file in trn_subject_file:
                    result.with_transition(transition_class, file)
            else:
                self.logger.log(
                    "Storage: {}. Rule: {}. Path: {}. Transition: {}. "
                    "Timepoint of action is after then now. Skip action.".format(
                        rule.datastorage_id, rule.rule_id, path, transition_class, timestamp_of_action))
        return result

    def _apply_action_items(self, storage, rule, action_items):
        for notification_properties in action_items.notification_queue:
            self._send_notification(storage, rule, notification_properties)

        for execution in action_items.executions:
            if execution.status == EXECUTION_FAILED_STATUS:
                self.logger.log("Execution failed: {}".format(str(execution)))

        for storage_class, subject_files in action_items.destination_transitions_queues.items():
            if subject_files:
                self.logger.log("Starting to tagging files for Storage Class: {}, number of files: {}".format(
                    storage_class, len(subject_files)))

                if action_items.mode == ACTIONS_MODE_FILES:
                    for file in subject_files:
                        self._create_or_update_execution(storage.id, rule, storage_class,
                                                         file.path, EXECUTION_RUNNING_STATUS)
                else:
                    self._create_or_update_execution(storage.id, rule, storage_class,
                                                     action_items.folder, EXECUTION_RUNNING_STATUS)
                is_successful = self.cloud_operations[storage.storage_type].process_files_on_cloud(
                    storage.path, storage.region_name, rule, action_items.folder, storage_class, subject_files)

                if not is_successful:
                    if action_items.mode == ACTIONS_MODE_FOLDER:
                        self._create_or_update_execution(storage.id, rule, storage_class,
                                                         action_items.folder, EXECUTION_RUNNING_STATUS)

    def _create_or_update_execution(self, storage_id, rule, storage_class, path, status):
        executions = self.cp_data_source.load_lifecycle_rule_executions_by_path(storage_id, rule.rule_id, path)
        execution = next(filter(lambda e: e.storage_class == storage_class, executions), None)

        if execution:
            self.cp_data_source.update_status_lifecycle_rule_execution(
                storage_id, execution.execution_id, status)
        else:
            self.cp_data_source.create_lifecycle_rule_execution(
                storage_id, rule.rule_id,
                {
                    "status": status,
                    "path": path,
                    "storageClass": storage_class
                }
            )

    def _check_rule_execution_progress(self, storage_id, subject_files, execution):
        if not execution.status:
            raise RuntimeError("Malformed rule execution found: " + json.dumps(execution))

        self.logger.log(
            "Storage: {}. Rule: {}. Path: {}. Transition: {}. Execution status: {}.".format(
                storage_id, execution.rule_id, execution.path, execution.storage_class, execution.status)
        )

        if execution.status == EXECUTION_RUNNING_STATUS:
            self.logger.log(
                "Storage: {}. Rule: {}. Path: {}. Transition: {}. "
                "Execution for this rule, path and transition in RUNNING state.".format(
                    storage_id, execution.rule_id, execution.path, execution.storage_class)
            )
            # Check that all files were moved to destination storage class and if not and 2 days are passed since
            # process was started - fail this execution
            file_in_wrong_location = next(filter(lambda file: file.storage_class != execution.storage_class, subject_files), None)
            all_files_moved = file_in_wrong_location is None
            max_running_days = self.synchronizer_config.execution_max_running_days
            if all_files_moved:
                self.logger.log(
                    "Storage: {}. Rule: {}. Path: {}. Transition: {}. All files moved to destination location".format(
                        storage_id, execution.rule_id, execution.path, execution.storage_class)
                )
                successful_execution = self.cp_data_source.delete_lifecycle_rule_execution(
                    storage_id, execution.execution_id)
                successful_execution.status = EXECUTION_SUCCESS_STATUS
                return successful_execution
            elif execution.updated + datetime.timedelta(days=max_running_days) < datetime.datetime.now(datetime.timezone.utc):
                self.logger.log(
                    "Storage: {}. Rule: {}. Path: {}. Transition: {}. "
                    "{} days are left but files in a wrong destination: {}. Failing this execution.".format(
                        storage_id, execution.rule_id, execution.path, execution.storage_class,
                        str(max_running_days), file_in_wrong_location.storage_class)
                )
                return self.cp_data_source.update_status_lifecycle_rule_execution(
                    storage_id, execution.execution_id, EXECUTION_FAILED_STATUS)
        return None

    def _send_notification(self, storage, rule, notification_properties):
        def _prepare_massage():
            cc_users = [
                self.cp_data_source.load_user(user_id)["userName"]
                for user_id in (rule.notification.user_to_notify_ids if rule.notification.user_to_notify_ids else [])
            ]
            if rule.notification.keep_informed_admins:
                role_admin = self.cp_data_source.load_role(ROLE_ADMIN_ID)
                if role_admin and "users" in role_admin:
                    cc_users.extend([user["userName"] for user in role_admin["users"]])

            _to_user = None
            if rule.notification.keep_informed_owner:
                loaded = self.cp_data_source.load_user_by_name(storage.owner)
                _to_user = loaded["userName"]
            if not _to_user:
                _to_user = next(iter(cc_users), None)

            notification_parameters = {
                "storageName": storage.name,
                "storageId": storage.id,
                "ruleId": rule.rule_id,
                "path": notification_properties["path"],
                "storageClass": notification_properties["storage_class"],
                "dateOfAction": notification_properties["date_of_action"],
                "prolongDays": notification_properties["prolong_days"]
            }

            return rule.notification.subject, rule.notification.body, _to_user, cc_users, notification_parameters

        path = notification_properties["path"]
        storage_class = notification_properties["storage_class"]
        if not rule.notification.enabled:
            self.logger.log(
                "Notification disabled: rule {}. Notification for path {} and storage class {} won't be sent".format(
                    rule.rule_id, path, storage_class)
            )
            return

        self._create_or_update_execution(
            rule.datastorage_id, rule, storage_class, path, EXECUTION_NOTIFICATION_SENT_STATUS)
        subject, body, to_user, copy_users, parameters = _prepare_massage()
        result = self.cp_data_source.send_notification(subject, body, to_user, copy_users, parameters)
        if not result:
            self._create_or_update_execution(
                rule.datastorage_id, rule, storage_class, path, EXECUTION_FAILED_STATUS)
            raise RuntimeError("Problem to send a notification for: {}".format(str(notification_properties)))

    @staticmethod
    def _notification_should_be_sent(notification, execution, file_to_transition, timestamp_of_action, now):
        if not notification.enabled:
            return False

        date_to_check = now + datetime.timedelta(days=notification.notify_before_days)
        if not file_to_transition or is_timestamp_before_date(timestamp_of_action, date_to_check):
            return False

        if execution:
            was_updated_before = is_timestamp_after_date(
                date=now - datetime.timedelta(days=notification.notify_before_days),
                timestamp=execution.updated
            )
            if execution.status == EXECUTION_RUNNING_STATUS:
                return False
            if execution.status == EXECUTION_NOTIFICATION_SENT_STATUS and was_updated_before:
                return False
        return True

    @staticmethod
    def _rule_is_not_valid(rule):
        if not rule.notification:
            raise RuntimeError("Rule: {}. No notification defined!".format(rule.rule_id))
        if rule.notification.enabled:
            if not rule.notification.body or not rule.notification.subject:
                raise RuntimeError("Rule: {}. Enabled notification should have subject and body!".format(rule.rule_id))
        if not rule.transitions:
            raise RuntimeError("Rule: {}. Transitions cannot be None or empty!".format(rule.rule_id))
        if not rule.transition_criterion:
            raise RuntimeError("Rule: {}. Transition criterion cannot be None!".format(rule.rule_id))
        if rule.transition_criterion.type != CRITERION_DEFAULT and not rule.transition_criterion.value:
            raise RuntimeError("Rule: {}. Transition criterion with not DEFAULT type should have value!".format(rule.rule_id))

    @staticmethod
    def define_transition_effective_timepoint(criterion_file, transition):
        if transition.transition_date:
            timestamp_of_action = transition.transition_date
        elif transition.transition_after_days:
            timestamp_of_action = criterion_file.creation_date \
                                  + datetime.timedelta(days=transition.transition_after_days)
        else:
            raise RuntimeError(
                "Malformed transition: date or days should be present. " + json.dumps(transition))
        return timestamp_of_action

    @staticmethod
    def define_criterion_file(rule, file_listing, folder, subject_files):
        transition_method = rule.transition_method
        criterion_files = subject_files
        if rule.transition_criterion.type == CRITERION_MATCHING_FILES:
            transition_criterion_files_glob = os.path.join(folder, rule.transition_criterion.value)
            criterion_files = [
                file for file in file_listing
                if file.path.startswith(folder) and fnmatch.fnmatch(file.path, transition_criterion_files_glob)
            ]
        # Sort by date (reverse or not depends on transition method) and get first element or None
        return next(
            iter(
                sorted(criterion_files, key=lambda e: e.creation_date, reverse=(transition_method == METHOD_LATEST_FILE))),
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
                    interim_result = interim_result + "/" + path_part if path_part else interim_result
                    result.add(interim_result)
                return result

            result = set()
            for path in paths:
                result = result.union(generate_hierarchy(path))
            return result

        if not glob_str or glob_str == "/":
            return ["/"]

        unique_folder_paths = set()
        for object in files:
            parent_dir, filename = os.path.split(object.path)
            if parent_dir:
                unique_folder_paths.add(parent_dir)
        directories = generate_all_possible_dir_paths(unique_folder_paths)
        pattern = re.compile(fnmatch.translate(glob_str).replace(".", "[^/]"))
        return [p for p in directories if pattern.match(p)]
