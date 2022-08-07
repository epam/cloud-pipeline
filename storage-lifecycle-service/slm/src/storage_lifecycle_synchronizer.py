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

from slm.src.model.storage_lifecycle_rule_model import StorageLifecycleRuleProlongation, StorageLifecycleRuleTransition
from slm.src.model.storage_lifecycle_sync_model import StorageLifecycleRuleActionItems
from slm.src.util.date_utils import is_timestamp_within_date

ROLE_ADMIN_ID = 1


class StorageLifecycleSynchronizer:

    def __init__(self, cp_data_source, cloud_operations, logger):
        self.logger = logger
        self.cloud_operations = cloud_operations
        self.cp_data_source = cp_data_source

    def sync(self):
        self.logger.log("Starting object lifecycle synchronization process...")
        available_storages = self.cp_data_source.load_available_storages()
        self.logger.log("{} storages loaded.".format(len(available_storages)))

        for storage in available_storages:
            self.logger.log(
                "Starting object lifecycle synchronization process for {} with type {}.".format(
                    storage.path, storage.type)
            )
            self._sync_storage(storage)
            self.logger.log(
                "Finish object lifecycle synchronization process for {} with type {}.".format(
                    storage.path, storage.type)
            )
        self.logger.log("Done object lifecycle synchronization process...")

    def _sync_storage(self, storage):
        rules = self.cp_data_source.load_lifecycle_rules_for_storage(storage.id)

        # No rules for storage exist - just skip it
        if not rules:
            self.logger.log("No rules for storage {} is defined, skipping".format(storage.path))
            return

        if storage.type not in self.cloud_operations:
            self.logger.log(
                "Lifecycle rules feature is not implemented for storage with type {}.".format(storage.type)
            )

        self.cloud_operations[storage.type].prepare_bucket_if_needed(storage)

        for rule in rules:
            if self._rule_is_not_valid(rule):
                continue

            files = self.cloud_operations[storage.type].list_objects_by_prefix_from_glob(storage, rule.path_glob)

            for folder in self._identify_subject_folders(files, rule.path_glob):

                # to match object keys with glob we need to combine it with folder path
                # and get 'effective' glob like '{folder-path}/{glob}'
                # so with glob = '*.txt' we can match files like:
                # {folder-path}/{filename}.txt
                effective_glob = os.path.join(folder, rule.object_glob) if rule.object_glob else None

                subject_files = [
                    file for file in files
                    if file.path.startswith(folder)
                    and fnmatch.fnmatch(file.path, effective_glob)
                ] if effective_glob else files

                self._process_files(storage, folder, files, subject_files, rule)

    def _process_files(self, storage, folder, file_listing, subject_files, rule):
        transition_method = rule.transition_method
        resulted_action_items = StorageLifecycleRuleActionItems(
            None, None, []).with_folder(folder).with_rule_id(rule.rule_id)

        if transition_method == "ONE_BY_ONE":
            resulted_action_items.with_mode("FILE")
            for file in subject_files:
                resulted_action_items.merge(self._build_action_items(file.path, [file], file, rule))
        elif transition_method == "LATEST_FILE" or transition_method == "EARLIEST_FILE":
            criterion_file = self.define_criterion_file(rule, file_listing, folder, subject_files)
            resulted_action_items.merge(self._build_action_items(folder, subject_files, criterion_file, rule))

        self._apply_action_items(storage, rule, resulted_action_items)

    def _build_action_items(self, path, subject_files, criterion_file, rule):
        result = StorageLifecycleRuleActionItems(None, None, []).with_rule_id(rule.rule_id)

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
                if notification.enabled and is_timestamp_within_date(date_to_check, timestamp_of_action):
                    notification_execution = next(
                        filter(lambda e: e.status == "NOTIFICATION_SENT" and e.storage_class == transition_class, lifecycle_executions), None
                    )
                    if not notification_execution or not is_timestamp_within_date(now, notification_execution.updated):
                        result.with_notification(
                            path, transition_class, transition.transition_date, notification.prolong_days
                        )

                # Check if action is needed
                date_to_check = now
                if is_timestamp_within_date(date_to_check, timestamp_of_action):
                    action_execution = next(
                        filter(lambda e: e.status == "RUNNING" and e.storage_class == transition_class, lifecycle_executions), None
                    )
                    if not action_execution or not is_timestamp_within_date(now, action_execution.updated):
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
                self.cloud_operations[storage.type].process_files_on_cloud(
                    storage, rule, action_items.folder, storage_class, files)

                if action_items.mode == "FILES":
                    for file in files:
                        self._create_or_update_execution(storage, rule, storage_class, file.path)
                else:
                    self._create_or_update_execution(storage, rule, storage_class, action_items.folder)

    def _create_or_update_execution(self, storage, rule, storage_class, path):
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

    def _check_rule_execution_progress(self, storage_id, subject_files, execution):
        if not execution.status:
            raise RuntimeError("Malformed rule execution found: " + json.dumps(execution))

        if execution.status == "RUNNING":
            # Check that all files were moved to destination storage class and if not and 2 days are passed since
            # process was started - fail this execution
            all_files_moved = next(
                filter(lambda file: file.storage_class != execution.storage_class, subject_files),
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

    @staticmethod
    def _rule_is_not_valid(rule):
        # TODO check that rule is valid
        pass

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
        if rule.transition_criterion.type == "MATCHING_FILES":
            transition_criterion_files_glob = os.path.join(folder, rule.transition_criterion.value)
            criterion_files = [
                file for file in file_listing
                if file.path.startswith(folder) and fnmatch.fnmatch(file.path, transition_criterion_files_glob)
            ]
        # Sort by date (reverse or not depends on transition method) and get first element or None
        return next(
            iter(
                sorted(criterion_files, key=lambda e: e.creation_date, reverse=(transition_method == "LATEST_FILE"))),
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
            parent_dir, filename = os.path.split(object.path)
            unique_folder_paths.add(parent_dir)
        return [p for p in generate_all_possible_dir_paths(unique_folder_paths) if fnmatch.fnmatch(p, glob_str)]
