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
from collections import OrderedDict

from sls.app.synchronizer.storage_synchronizer_interface import StorageLifecycleSynchronizer
from sls.pipelineapi.model.restore_action_model import StorageLifecycleRestoreAction


class StorageLifecycleRestoringSynchronizer(StorageLifecycleSynchronizer):

    DATASTORAGE_RESTORE_ACTION_NOTIFICATION_TYPE = "DATASTORAGE_LIFECYCLE_RESTORE_ACTION"

    SEARCH_CHILD_RECURSIVELY = "SEARCH_CHILD_RECURSIVELY"

    INITIATED_STATUS = "INITIATED"
    RUNNING_STATUS = "RUNNING"
    SUCCEEDED_STATUS = "SUCCEEDED"
    FAILED_STATUS = "FAILED"
    CANCELLED_STATUS = "CANCELLED"

    RUNNING_STATUSES = [INITIATED_STATUS, RUNNING_STATUS]
    ACTIVE_STATUSES = [SUCCEEDED_STATUS, INITIATED_STATUS, RUNNING_STATUS]
    TERMINAL_STATUSES = [SUCCEEDED_STATUS, CANCELLED_STATUS, FAILED_STATUS]

    def _sync_storage(self, storage):
        ongoing_actions = self.cp_data_source.filter_restore_actions(
            storage.id,
            filter_obj={
                "datastorageId": storage.id,
                "statuses": self.RUNNING_STATUSES,
                "searchType": self.SEARCH_CHILD_RECURSIVELY
            }
        )
        if not ongoing_actions or len(ongoing_actions) == 0:
            self.logger.log("Storage: {}. No active restore action found for storage.".format(storage.id))
            return
        else:
            self.logger.log("Storage: {}. Found {} INITIATED or RUNNING restore actions."
                            .format(storage.id, len(ongoing_actions)))

        self.logger.log("Storage: {}. Processing RUNNING restore actions.".format(storage.id))
        running_actions = [a for a in ongoing_actions if a.status == self.RUNNING_STATUS]
        for running_action in running_actions:
            self._process_action_and_update(storage, running_action)

        # Go through paths for which actions are defined.
        # Here we will do the next for each path:
        #  - Find effective (latest) one action that related to the path
        #  - Process this action according to its status
        #  - Find all action that can be effected by current one
        #    (f.e. actions for child folders and files that were launched before current one)
        #    In this case current one will override these and we need to cancel these actions
        actions_by_path = self._fetch_restore_action_paths_sorted(ongoing_actions)
        self.logger.log("Storage: {}. Processing INITIATED restore actions.".format(storage.id))
        for path, actions in actions_by_path.items():
            effective_action = next(iter(sorted(actions, key=lambda a: a.started, reverse=True)), None)
            if effective_action and effective_action.status == self.INITIATED_STATUS:

                running_related_action = next(
                    filter(lambda a: a.status == self.RUNNING_STATUS,
                           sorted(self._fetch_child_actions_to_current_one(effective_action, ongoing_actions),
                                  key=lambda a: a.started, reverse=True)),
                    None
                )

                if running_related_action:
                    self.logger.log(
                        "Storage: {}. Action: {}. Path: {}. Found running action: {} that is related to current. "
                        "Will skip current action to wait until running one will finish."
                        .format(storage.id, effective_action.action_id, path, running_related_action.action_id))
                else:
                    self.logger.log(
                        "Storage: {}. Action: {}. Path: {}. Found effective action, status: {}, updated date: {}, processing it."
                        .format(storage.id, effective_action.action_id, path, effective_action.status, effective_action.updated))
                    self._process_action_and_update(storage, effective_action)

                related_actions_to_cancel = [a for a in
                                             self._fetch_child_actions_to_current_one(effective_action, ongoing_actions)
                                             if a.status == self.INITIATED_STATUS]
                if len(related_actions_to_cancel) > 0:
                    self.logger.log(
                        "Storage: {}. Action: {}. Path: {}. There are '{}' related actions, that will be cancelled.".format(
                            storage.id, effective_action.action_id, path, len(related_actions_to_cancel)))
                    for action_to_cancel in related_actions_to_cancel:
                        self._update_action(action_to_cancel, self.CANCELLED_STATUS)

    def _process_action_and_update(self, storage, action):
        if action.status == self.INITIATED_STATUS:
            self.logger.log("Storage: {}. Action: {}. Path: {}. Initiating restore process for '{}' days."
                            .format(storage.id, action.action_id, action.path, action.days))
            restore_result = self.cloud_bridge.run_restore_action(storage, action, self._get_restore_operation_id(action))
            self.logger.log("Storage: {}. Action: {}. Path: {}. Restore initiating process finished with status: '{}' reason: '{}'"
                            .format(storage.id, action.action_id, action.path, restore_result["status"], restore_result["reason"]))
            self._update_action(action, self.RUNNING_STATUS if restore_result["status"] else self.FAILED_STATUS)
        elif action.status == self.RUNNING_STATUS:
            self.logger.log("Storage: {}. Action: {}. Path: {}. Checking status of restore process."
                            .format(storage.id, action.action_id, action.path))
            restore_result = self.cloud_bridge.check_restore_action(storage, action)
            self.logger.log("Storage: {}. Action: {}. Path: {}. Checking restore process finished with status: {} and reason: {}"
                            .format(storage.id, action.action_id, action.path, restore_result["status"], restore_result["reason"]))
            if restore_result["status"]:
                self._update_action(action, self.SUCCEEDED_STATUS, restored_till=restore_result["value"])
                if action.notification.enabled:
                    self._send_restore_notification(storage, action)

    def _update_action(self, action_to_update, status, restored_till=None):
        action_to_update.status = status
        if restored_till:
            action_to_update.restored_till = restored_till
        updated_response = self.cp_data_source.update_restore_action(action_to_update)
        if updated_response:
            updated_action = StorageLifecycleRestoreAction.parse_from_dict(updated_response)
            self._merge_actions(action_to_update, updated_action)
        else:
            self.logger.log("Problem to update restore action with id: {}".format(action_to_update.id))
        return action_to_update

    def _send_restore_notification(self, storage, action):

        def _prepare_message():
            notification = self.cp_data_source.load_notification(self.DATASTORAGE_RESTORE_ACTION_NOTIFICATION_TYPE)
            cc_users = []

            if notification.settings.keep_informed_admins:
                loaded_role = self.cp_data_source.load_role_by_name("ROLE_ADMIN")
                if loaded_role and "users" in loaded_role:
                    cc_users.extend([user["userName"] for user in loaded_role["users"]])

            for recipient in action.notification.recipients:
                if recipient["principal"]:
                    cc_users.append(recipient["name"])
                else:
                    loaded_role = self.cp_data_source.load_role_by_name(recipient["name"])
                    if loaded_role and "users" in loaded_role:
                        cc_users.extend([user["userName"] for user in loaded_role["users"]])

            _to_user = next(iter(cc_users), None)
            return notification.template.subject, notification.template.body, _to_user, cc_users, \
                   {
                       "datastorageId": storage.id,
                       "datastorageName": storage.path,
                       "path": action.path,
                       "pathType": action.path_type,
                       "actionId": action.action_id,
                       "restoredTill": action.restored_till.strftime("%Y-%m-%d %H:%M:%S")
                   }

        subject, body, to_user, copy_users, parameters = _prepare_message()
        if subject and body and to_user:
            result = self.cp_data_source.send_notification(subject, body, to_user, copy_users, parameters)
            if not result:
                self.logger.log("Storage: {}. Action: {}. Path: {}. Problem to send restore notification."
                                .format(storage.id, action.action_id, action.path))
        else:
            self.logger.log("Storage: {}. Action: {}. Path: {}. "
                            "Will not send notification because parameters are not present, "
                            "subject: {} body: {} to_user: {}"
                            .format(storage.id, action.action_id, action.path, subject, body, to_user))

    @staticmethod
    def _fetch_child_actions_to_current_one(root_action, actions):
        return [a for a in actions
                if a.path.startswith(root_action.path)
                and root_action.started > a.started
                and root_action is not a]

    def _fetch_restore_action_paths_sorted(self, _ongoing_restore_actions):
        _actions_by_path = {}
        for action in _ongoing_restore_actions:
            if action.status not in self.ACTIVE_STATUSES:
                continue
            actions_for_path = []
            if action.path in _actions_by_path:
                actions_for_path = _actions_by_path[action.path]
            else:
                _actions_by_path[action.path] = actions_for_path
            actions_for_path.append(action)
        return OrderedDict(sorted(_actions_by_path.items()))

    @staticmethod
    def _merge_actions(to_merge, updated):
        to_merge.status = updated.status
        to_merge.updated = updated.updated
        if updated.restored_till:
            to_merge.restored_till = updated.restored_till
        return to_merge

    @staticmethod
    def _get_restore_operation_id(action):
        return "restore_{}".format(action.path.replace(" ", "_").replace("/", "."))
