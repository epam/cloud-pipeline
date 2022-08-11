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
import os
import re

from sls.model.rule_model import StorageLifecycleRuleProlongation, StorageLifecycleRuleTransition
from sls.model.action_model import StorageLifecycleRuleActionItems
from sls.util import path_utils
from sls.util import date_utils


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

    def __init__(self, config, cp_data_source, cloud_providers, logger):
        self.config = config
        self.cloud_providers = cloud_providers
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

        if storage.storage_type not in self.cloud_providers:
            self.logger.log(
                "Lifecycle rules feature is not implemented for storage with type {}.".format(storage.storage_type)
            )

        self.cloud_providers[storage.storage_type].prepare_bucket_if_needed(storage.path)

        file_listing_cache = {}

        for rule in rules:
            self.logger.log("Storage: {}. Rule: {}. [Starting]".format(storage.id, rule.rule_id))
            try:
                if self._rule_is_not_valid(rule):
                    continue

                path_prefix = path_utils.determinate_prefix_from_glob(rule.path_glob)
                existing_listing_prefix = next(
                    filter(lambda k: path_prefix.startswith(k), file_listing_cache.keys()), None
                )
                if existing_listing_prefix:
                    files = [f for f in file_listing_cache[existing_listing_prefix] if f.path.startswith(path_prefix)]
                else:
                    files = self.cloud_providers[storage.storage_type].list_objects_by_prefix(storage.path, path_prefix)
                    file_listing_cache[path_prefix] = files

                subject_folders = self._identify_subject_folders(files, rule.path_glob)
                self.logger.log(
                    "Storage: {}. Rule: {}. Subject folders are: {}".format(storage.id, rule.rule_id, subject_folders))

                for folder in subject_folders:
                    self.logger.log(
                        "Storage: {}. Rule: {}. Path: '{}'. [Applying rule]".format(storage.id, rule.rule_id, folder))

                    # to match object keys with glob we need to combine it with folder path
                    # and get 'effective' glob like '{folder-path}/{glob}'
                    # so with glob = '*.txt' we can match files like:
                    # {folder-path}/{filename}.txt
                    effective_glob = os.path.join(folder, rule.object_glob) if rule.object_glob else None

                    self.logger.log(
                        "Storage: {}. Rule: {}. Path: '{}'. Effective glob is '{}'".format(
                            storage.id, rule.rule_id, folder, effective_glob)
                    )

                    rule_subject_files = [
                        file for file in files
                        if file.path.startswith(folder)
                        and fnmatch.fnmatch(file.path, effective_glob)
                    ] if effective_glob else files
                    self.logger.log(
                        "Storage: {}. Rule: {}. Path: '{}'. Found {} subject files, "
                        "than might be eligible to transition.".format(
                            storage.id, rule.rule_id, folder, len(rule_subject_files))
                    )

                    self._process_files(storage, folder, files, rule_subject_files, rule)
                    self.logger.log("Storage: {}. Rule: {}, Path: '{}'. [Rule applied]".format(storage.id, rule.rule_id, folder))
                self.logger.log("Storage: {}. Rule: {}. [Complete]".format(storage.id, rule.rule_id))
            except Exception as e:
                self.logger.log(
                    "Storage: {}. Rule: {}. Problems to apply the rule. "
                    "Cause: {}".format(storage.id, rule.rule_id, str(e)))

    def _process_files(self, storage, folder, file_listing, rule_subject_files, rule):
        transition_method = rule.transition_method

        self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Transition method is {}. Building action items."
                        .format(rule.datastorage_id, rule.rule_id, folder, transition_method))

        if transition_method == METHOD_ONE_BY_ONE:
            resulted_action_items = self._build_action_items_for_files(folder, rule_subject_files, rule)
        else:
            criterion_file = self._define_criterion_file(rule, file_listing, folder, rule_subject_files)
            resulted_action_items = self._build_action_items_for_folder(
                folder, rule_subject_files, criterion_file, rule)

        self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Done with building action items."
                        .format(rule.datastorage_id, rule.rule_id, folder))

        self._apply_action_items(storage, rule, resulted_action_items)

    def _build_action_items_for_files(self, folder, rule_subject_files, rule):
        result = StorageLifecycleRuleActionItems().with_folder(folder)\
            .with_mode(ACTIONS_MODE_FILES).with_rule_id(rule.rule_id)

        for file in rule_subject_files:
            effective_transitions = self._define_effective_transitions(
                self._fetch_prolongation_for_path_or_default(file.path, rule), rule.transitions)

            transition_class, transition_date = None, None
            for transition in effective_transitions:
                date_of_action = self._calculate_transition_date(file, transition)
                today = datetime.datetime.now(datetime.timezone.utc).date()

                if date_utils.is_date_after_that(date_of_action, today):
                    should_be_transferred = \
                        file.storage_class != transition.storage_class and \
                        (transition_date is None or date_utils.is_date_after_that(transition_date, date_of_action))
                    if not should_be_transferred:
                        continue
                    transition_class = transition.storage_class
                    transition_date = date_of_action
            result.with_transition(transition_class, file)
        return result

    def _build_action_items_for_folder(self, path, rule_subject_files, criterion_file, rule):
        result = StorageLifecycleRuleActionItems().with_folder(path).with_rule_id(rule.rule_id)

        if not criterion_file:
            return result

        effective_transitions = self._define_effective_transitions(
            self._fetch_prolongation_for_path_or_default(path, rule), rule.transitions)

        rule_executions = self.cp_data_source.load_lifecycle_rule_executions_by_path(
            rule.datastorage_id, rule.rule_id, path)

        self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Found {} executions.".format(
                rule.datastorage_id, rule.rule_id, path, len(rule_executions)))

        for execution_for_transition in rule_executions:
            updated_execution = self._check_rule_execution_progress(
                rule.datastorage_id, rule_subject_files, execution_for_transition
            )
            if updated_execution:
                execution_for_transition.status = updated_execution.status
                execution_for_transition.updated = updated_execution.updated
                result.with_execution(updated_execution)

        today = datetime.datetime.now(datetime.timezone.utc).date()

        transition_class, transition_date, transition_execution, trn_subject_file = \
            self._get_eligible_transition(criterion_file, effective_transitions, rule_executions,
                                          rule_subject_files, today)

        if transition_execution and transition_execution.status == EXECUTION_RUNNING_STATUS:
            self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Transition: {}. "
                            "No need for action: execution is in RUNNING state.".format(
                             rule.datastorage_id, rule.rule_id, path, transition_class))
            return result

        if not transition_class:
            self.logger.log(
                "Storage: {}. Rule: {}. Path: '{}'. No eligible action for now, skipping.".format(
                    rule.datastorage_id, rule.rule_id, path))
            return result

        if not trn_subject_file:
            self.logger.log(
                "Storage: {}. Rule: {}. Path: '{}'. Transition: {}. No files to transit.".format(
                    rule.datastorage_id, rule.rule_id, path, transition_class))
            return result

        # Check if notification is needed
        notification = rule.notification
        if self._notification_should_be_sent(notification, transition_execution, transition_date, today):
            self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Transition: {}. Notification will be sent.".format(
                rule.datastorage_id, rule.rule_id, path, transition_class, len(trn_subject_file)))
            result.with_notification(path, transition_class, str(transition_date), notification.prolong_days)

        # Check if action is needed
        if date_utils.is_date_after_that(transition_date, today):
            self.logger.log(
                "Storage: {}. Rule: {}. Path: '{}'. Transition: {}. All criteria are met. Will transit {} files."
                .format(rule.datastorage_id, rule.rule_id, path, transition_class, len(trn_subject_file)))

            for file in trn_subject_file:
                result.with_transition(transition_class, file)
        else:
            self.logger.log(
                "Storage: {}. Rule: {}. Path: '{}'. No eligible transition found now. "
                "Nearest transition: destination - {} date - {}"
                .format(rule.datastorage_id, rule.rule_id, path, transition_class, transition_date))
            return result
        return result

    def _get_eligible_transition(self, criterion_file, transitions, executions, rule_subject_files, today):

        def _is_execution_in_active_phase(_execution):
            return _execution and \
                   (execution_for_transition.status == EXECUTION_RUNNING_STATUS
                    or execution_for_transition.status == EXECUTION_NOTIFICATION_SENT_STATUS)

        def _is_execution_in_complete_phase(_execution):
            return _execution and execution_for_transition.status == EXECUTION_SUCCESS_STATUS

        transitions_by_dates = sorted(
            [(t, self._calculate_transition_date(criterion_file, t)) for t in transitions],
            key=lambda pair: pair[1]
        )
        transition_class, transition_date, transition_execution, trn_subject_file = None, None, None, None
        for transition, date_of_action in transitions_by_dates:
            files_for_transition = [f for f in rule_subject_files if f.storage_class != transition.storage_class]

            execution_for_transition = next(
                filter(lambda e: e.storage_class == transition.storage_class, executions), None
            )

            if _is_execution_in_active_phase(execution_for_transition):
                return transition.storage_class, date_of_action, execution_for_transition, files_for_transition

            if date_utils.is_date_after_that(date=date_of_action, to_check=today):
                should_be_transferred = not _is_execution_in_complete_phase(execution_for_transition) and \
                        (transition_date is None or date_utils.is_date_after_that(date=transition_date, to_check=date_of_action))
                if not should_be_transferred:
                    continue
                transition_class = transition.storage_class
                transition_date = date_of_action
                transition_execution = execution_for_transition
                trn_subject_file = files_for_transition
            elif not transition_class:
                transition_class = transition.storage_class
                transition_date = date_of_action
                transition_execution = execution_for_transition
                trn_subject_file = files_for_transition
        return transition_class, transition_date, transition_execution, trn_subject_file

    def _apply_action_items(self, storage, rule, action_items):
        self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Performing action items."
                        .format(rule.datastorage_id, rule.rule_id, action_items.folder))

        for notification_properties in action_items.notification_queue:
            self._send_notification(storage, rule, notification_properties)

        for execution in action_items.executions:
            self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Transition: {}. Execution finished with status: {}"
                            .format(rule.datastorage_id, rule.rule_id, action_items.folder,
                                    execution.storage_class, execution.status))

        for storage_class, subject_files in action_items.destination_transitions_queues.items():
            if subject_files:
                self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Transition: {}. "
                                "Starting to tagging files for transition, number of files: {}.".format(
                    rule.datastorage_id, rule.rule_id, action_items.folder, storage_class, len(subject_files)))

                if action_items.mode == ACTIONS_MODE_FOLDER:
                    self._create_or_update_execution(storage.id, rule, storage_class,
                                                     action_items.folder, EXECUTION_RUNNING_STATUS)
                is_successful = self.cloud_providers[storage.storage_type].process_files_on_cloud(
                    storage.path, storage.region_name, rule, action_items.folder, storage_class, subject_files)

                if not is_successful:
                    if action_items.mode == ACTIONS_MODE_FOLDER:
                        self._create_or_update_execution(storage.id, rule, storage_class,
                                                         action_items.folder, EXECUTION_FAILED_STATUS)

        self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Complete action items."
                        .format(rule.datastorage_id, rule.rule_id, action_items.folder))

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
            raise RuntimeError("Malformed rule execution found.")

        self.logger.log(
            "Storage: {}. Rule: {}. Path: '{}'. Transition: {}. Checking existing execution, status: {}.".format(
                storage_id, execution.rule_id, execution.path, execution.storage_class, execution.status)
        )

        if execution.status == EXECUTION_RUNNING_STATUS:
            # Check that all files were moved to destination storage class and if not and 2 days are passed since
            # process was started - fail this execution
            file_in_wrong_location = next(filter(lambda file: file.storage_class != execution.storage_class, subject_files), None)
            all_files_moved = file_in_wrong_location is None
            max_running_days = self.config.execution_max_running_days
            if all_files_moved:
                self.logger.log(
                    "Storage: {}. Rule: {}. Path: '{}'. Transition: {}. "
                    "All files moved to destination location, completing the action.".format(
                        storage_id, execution.rule_id, execution.path, execution.storage_class)
                )
                return self.cp_data_source.update_status_lifecycle_rule_execution(
                    storage_id, execution.execution_id, EXECUTION_SUCCESS_STATUS)
            elif execution.updated + datetime.timedelta(days=max_running_days) < datetime.datetime.now(datetime.timezone.utc):
                self.logger.log(
                    "Storage: {}. Rule: {}. Path: '{}'. Transition: {}. "
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
            self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Transition: {}. Notification disabled."
                            "Notification disabled.".format(rule.datastorage_id, rule.rule_id, path, storage_class))
            return
        else:
            self.logger.log("Storage: {}. Rule: {}. Path: '{}'. Transition: {}. Sending notification."
                            .format(rule.datastorage_id, rule.rule_id, path, storage_class))

        self._create_or_update_execution(
            rule.datastorage_id, rule, storage_class, path, EXECUTION_NOTIFICATION_SENT_STATUS)
        subject, body, to_user, copy_users, parameters = _prepare_massage()
        result = self.cp_data_source.send_notification(subject, body, to_user, copy_users, parameters)
        if not result:
            self._create_or_update_execution(
                rule.datastorage_id, rule, storage_class, path, EXECUTION_FAILED_STATUS)
            raise RuntimeError("Problem to send a notification for: {}".format(str(notification_properties)))

    @staticmethod
    def _notification_should_be_sent(notification, execution, date_of_action, today):
        if not notification.enabled:
            return False

        date_to_check = today + datetime.timedelta(days=notification.notify_before_days)
        if date_utils.is_date_before_that(date_of_action, date_to_check):
            return False

        if execution:
            was_updated_before = date_utils.is_date_after_that(
                date=today - datetime.timedelta(days=notification.notify_before_days),
                to_check=execution.updated.date()
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
            raise RuntimeError("Rule: {}. Transition criterion with not DEFAULT type should have value!"
                               .format(rule.rule_id))
        method_possible_values = [METHOD_LATEST_FILE, METHOD_ONE_BY_ONE, METHOD_EARLIEST_FILE]
        if not rule.transition_method or rule.transition_method not in method_possible_values:
            raise RuntimeError("Rule: {}. Transition method should have one of possible values: {}!"
                               .format(rule.rule_id, method_possible_values))

    @staticmethod
    def _calculate_transition_date(criterion_file, transition):
        if transition.transition_date:
            date_of_action = transition.transition_date
        elif transition.transition_after_days is not None:
            date_of_action = \
                (criterion_file.creation_date + datetime.timedelta(days=transition.transition_after_days)).date()
        else:
            raise RuntimeError("Malformed transition: date or days should be present.")
        return date_of_action

    @staticmethod
    def _define_criterion_file(rule, file_listing, folder, subject_files):
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
                sorted(
                    criterion_files, key=lambda e: e.creation_date, reverse=(transition_method == METHOD_LATEST_FILE)
                )
            ), None
        )

    @staticmethod
    def _define_effective_transitions(prolongation, transitions):
        def _define_transition(transition):
            resulted_transition = StorageLifecycleRuleTransition(transition.storage_class)
            if transition.transition_after_days is not None:
                resulted_transition.transition_after_days = transition.transition_after_days + prolongation.days
            if transition.transition_date:
                resulted_transition.transition_date = \
                    transition.transition_date + datetime.timedelta(days=prolongation.days)
            return resulted_transition

        return [_define_transition(transition) for transition in transitions]

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
        if not glob_str or glob_str == "/":
            return ["/"]

        unique_folder_paths = set()
        for file in files:
            parent_dir, filename = os.path.split(file.path)
            if parent_dir:
                unique_folder_paths.add(parent_dir)
        directories = path_utils.generate_all_possible_dir_paths(unique_folder_paths)
        resulted_regexp = path_utils.convert_glob_to_regexp(glob_str)
        pattern = re.compile(resulted_regexp)
        return [p for p in directories if pattern.match(p)]
