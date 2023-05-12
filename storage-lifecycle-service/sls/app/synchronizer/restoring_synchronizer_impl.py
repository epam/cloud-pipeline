#  Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

from sls.app.storage_permissions_manager import StoragePermissionsManager
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

    PATH_TYPE_FOLDER = "FOLDER"

    def _sync_storage(self, storage):
        ongoing_actions = self.pipeline_api_client.filter_restore_actions(
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

                # search for parent or child running action (so, related)
                running_related_action = next(
                       iter(self._fetch_related_actions_to_current_one(effective_action, ongoing_actions,
                                                                       statuses=[self.RUNNING_STATUS])),
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

                # Child actions with INITIATED_STATUS and started date that is before then
                # effective action started date can be cancelled because effective action override it
                related_actions_to_cancel = [a for a in
                                             self._fetch_related_actions_to_current_one(
                                                 effective_action, ongoing_actions,
                                                 search_down=True, statuses=[self.INITIATED_STATUS])
                                             if a.started < effective_action.started]
                if len(related_actions_to_cancel) > 0:
                    self.logger.log(
                        "Storage: {}. Action: {}. Path: {}. There are '{}' related actions, that will be cancelled.".format(
                            storage.id, effective_action.action_id, path, len(related_actions_to_cancel)))
                    for action_to_cancel in related_actions_to_cancel:
                        self.logger.log(
                            "Storage: {}. Action: {}. Path: {}. Cancelling.".format(
                                storage.id, action_to_cancel.action_id, action_to_cancel.path))
                        updated_action = self._update_action(action_to_cancel, self.CANCELLED_STATUS)
                        if not updated_action:
                            self.logger.log(
                                "Storage: {}. Action: {}. Path: {}. Something went wrong, can't cancel action.".format(
                                    storage.id, action_to_cancel.action_id, action_to_cancel.path))
                        else:
                            self.logger.log(
                                "Storage: {}. Action: {}. Path: {}. Successfully cancelled.".format(
                                    storage.id, action_to_cancel.action_id, action_to_cancel.path))

    def _process_action_and_update(self, storage, action):
        if action.status == self.INITIATED_STATUS:

            if not self.cloud_bridge.is_support(storage):
                self.logger.log(
                    "Lifecycle restore feature is not implemented for storage with type {}.".format(storage.storage_type)
                )
                self._update_action(action, self.FAILED_STATUS)
                return

            self.logger.log("Storage: {}. Action: {}. Path: {}. Initiating restore process for '{}' days."
                            .format(storage.id, action.action_id, action.path, action.days))
            restore_result = self.cloud_bridge.run_restore_action(storage, action, self._get_restore_operation_id(action))
            self.logger.log("Storage: {}. Action: {}. Path: {}. Restore initiating process finished with status: '{}' reason: '{}'"
                            .format(storage.id, action.action_id, action.path, restore_result["status"], restore_result["reason"]))
            run_action = self._update_action(action, self.RUNNING_STATUS if restore_result["status"] else self.FAILED_STATUS)
            if not run_action:
                self.logger.log(
                    "Storage: {}. Action: {}. Path: {}. Something went wrong. "
                    "Can't updated status for run restoring action action."
                    .format(storage.id, action.action_id, action.path)
                )
        elif action.status == self.RUNNING_STATUS:
            self.logger.log("Storage: {}. Action: {}. Path: {}. Checking status of restore process."
                            .format(storage.id, action.action_id, action.path))
            restore_result = self.cloud_bridge.check_restore_action(storage, action)
            self.logger.log("Storage: {}. Action: {}. Path: {}. Checking restore process finished with status: {} and reason: {}"
                            .format(storage.id, action.action_id, action.path, restore_result["status"], restore_result["reason"]))
            if restore_result["status"]:
                if restore_result["value"]:
                    self.logger.log(
                        "Storage: {}. Action: {}. Path: {}. Restore process succeeded, restoredTill: {}"
                        .format(storage.id, action.action_id, action.path, restore_result["value"]))
                    succeeded_action = self._update_action(action, self.SUCCEEDED_STATUS, restored_till=restore_result["value"])
                    if succeeded_action:
                        if action.notification.enabled:
                            self._send_restore_notification(storage, succeeded_action)
                    else:
                        self.logger.log(
                            "Storage: {}. Action: {}. Path: {}. Something went wrong. "
                            "Can't updated status for succeeded action.".format(storage.id, action.action_id, action.path)
                        )
                else:
                    self.logger.log(
                        "Storage: {}. Action: {}. Path: {}. Something went wrong. "
                        "Can't check status for action. Action will be failed!".format(storage.id, action.action_id, action.path)
                    )
                    self._update_action(action, self.FAILED_STATUS)

    def _update_action(self, action_to_update, status, restored_till=None):
        copy_action_to_update = action_to_update.copy()
        copy_action_to_update.status = status
        if restored_till:
            copy_action_to_update.restored_till = restored_till
        updated_response = self.pipeline_api_client.update_restore_action(copy_action_to_update)
        if updated_response:
            updated_action = StorageLifecycleRestoreAction.parse_from_dict(updated_response)
            self._merge_actions(action_to_update, updated_action)
        else:
            self.logger.log("Problem to update restore action with id: {}".format(action_to_update.id))
            return None
        return action_to_update

    def _send_restore_notification(self, storage, action):

        def _prepare_message():
            notification = self.pipeline_api_client.load_notification(self.DATASTORAGE_RESTORE_ACTION_NOTIFICATION_TYPE)
            cc_users = []

            if notification.settings.keep_informed_admins:
                loaded_role = self.pipeline_api_client.load_role_by_name("ROLE_ADMIN")
                if loaded_role and "users" in loaded_role:
                    cc_users.extend([user["userName"] for user in loaded_role["users"]])

            for recipient in action.notification.recipients:
                if recipient["principal"]:
                    cc_users.append(recipient["name"])
                else:
                    loaded_role = self.pipeline_api_client.load_role_by_name(recipient["name"])
                    if loaded_role and "users" in loaded_role:
                        cc_users.extend([user["userName"] for user in loaded_role["users"]])

            if action.notification.notify_users:
                storage_users = StoragePermissionsManager(self.pipeline_api_client, storage.id).get_users()
                cc_users.extend([user_name for user_name in storage_users if not cc_users.__contains__(user_name)])

            _to_user = next(iter(cc_users), None)
            return notification.template.subject, notification.template.body, _to_user, cc_users, \
                   {
                       "storageId": storage.id,
                       "storageName": storage.path,
                       "path": action.path,
                       "pathType": action.path_type,
                       "actionId": action.action_id,
                       "restoredTill": action.restored_till.strftime("%Y-%m-%d %H:%M:%S"),
                       "notificationType": "DATASTORAGE_LIFECYCLE_RESTORE_ACTION",
                       "notificationEntities": [{
                           "entityId": storage.id,
                           "entityClass": "STORAGE",
                           "storagePath": action.path,
                       }],
                   }

        subject, body, to_user, copy_users, parameters = _prepare_message()
        if subject and body and to_user:
            result = self.pipeline_api_client.send_notification(subject, body, to_user, copy_users, parameters)
            if not result:
                self.logger.log("Storage: {}. Action: {}. Path: {}. Problem to send restore notification."
                                .format(storage.id, action.action_id, action.path))
        else:
            self.logger.log("Storage: {}. Action: {}. Path: {}. "
                            "Will not send notification because parameters are not present, "
                            "subject: {} body: {} to_user: {}"
                            .format(storage.id, action.action_id, action.path, subject, body, to_user))

    @staticmethod
    def _fetch_related_actions_to_current_one(root_action, actions, search_down=None, statuses=None):
        def _has_relation(action, _search_down):
            if _search_down is None:
                return _has_relation(action, False) or _has_relation(action, True)
            elif _search_down:
                parent_folder_path = root_action.path if root_action.path.endswith("/") else root_action.path + "/"
                is_child_action = root_action.path_type == StorageLifecycleRestoringSynchronizer.PATH_TYPE_FOLDER and action.path.startswith(parent_folder_path)
                return action.path == root_action.path or is_child_action
            else:
                parent_folder_path = action.path if action.path.endswith("/") else action.path + "/"
                is_parent_action = action.path_type == StorageLifecycleRestoringSynchronizer.PATH_TYPE_FOLDER and root_action.path.startswith(parent_folder_path)
                return action.path == root_action.path or is_parent_action

        if statuses is None:
            statuses = []
        return [a for a in actions
                if _has_relation(a, search_down)
                and root_action.started > a.started
                and root_action is not a
                and (not statuses or a.status in statuses)]

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
