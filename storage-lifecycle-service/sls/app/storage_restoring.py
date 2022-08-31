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
from sls.app.storage_synchronizer import StorageLifecycleSynchronizer


class StorageLifecycleRestoringSynchronizer(StorageLifecycleSynchronizer):

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
        ongoing_restore_actions = self.cp_data_source.filter_restore_actions(
            storage.id,
            filter_obj={
                "datastorageId": storage.id,
                "statuses": self.RUNNING_STATUSES,
                "searchType": self.SEARCH_CHILD_RECURSIVELY
            }
        )
        if not ongoing_restore_actions or len(ongoing_restore_actions) == 0:
            return

        # Go through paths for which actions are defined.
        # Here we will do the next for each path:
        #  - Find effective (latest) one action that related to the path
        #  - Process this action according to its status
        #  - Find all action that can be effected by current one
        #    (f.e. actions for child folders and files that were launched before current one)
        #    In this case current one will override these and we need to cancel these actions
        actions_by_path = self._fetch_restore_action_paths_sorted(ongoing_restore_actions)
        for path, actions in actions_by_path:
            latest_running_action = next(
                filter(lambda a: a.status == self.RUNNING_STATUS, sorted(actions, key=lambda a: a.started, reverse=True)),
                None
            )
            if latest_running_action:
                self._process_action_and_update(storage, latest_running_action)

            effective_action = next(iter(sorted(actions, key=lambda a: a.started, reverse=True)), None)
            if effective_action and effective_action.status in self.ACTIVE_STATUSES:
                self._process_action_and_update(storage, effective_action)
                related_actions_to_cancel = self._fetch_actions_to_override(effective_action, ongoing_restore_actions)
                for action_to_cancel in related_actions_to_cancel:
                    self._update_action(action_to_cancel, self.CANCELLED_STATUS)

    def _process_action_and_update(self, storage, action):
        if action.status == self.INITIATED_STATUS:
            restore_result = self.cloud_bridge.run_restore_action(storage, action, self._get_restore_operation_id(action))
            if restore_result["status"]:
                self._update_action(action, self.RUNNING_STATUS)
        elif action.status == self.RUNNING_STATUS:
            restore_result = self.cloud_bridge.check_restore_action(storage, action)
            if restore_result["status"]:
                self._update_action(action, self.SUCCEEDED_STATUS, restored_till=restore_result["value"])

    def _update_action(self, action_to_update, status, restored_till=None):
        action_to_update.status = status
        if restored_till:
            action_to_update.restored_till = restored_till
        updated_action = self.cp_data_source.update_restore_action(action_to_update)
        if updated_action:
            self._merge_actions(action_to_update, updated_action)
        else:
            self.logger.log("Problem to update restore action with id: {}")
        return action_to_update

    @staticmethod
    def _fetch_actions_to_override(root_action, actions):
        return [a for a in actions if a.path.startswith(root_action.path) and root_action.started > a.started]

    @staticmethod
    def _fetch_restore_action_paths_sorted(_ongoing_restore_actions):
        _unique_paths = set()
        _actions_by_path = {}
        for action in _ongoing_restore_actions:
            _unique_paths.add(action.path)
            actions_for_path = []
            if action.path in _actions_by_path:
                actions_for_path = _actions_by_path[action.path]
            actions_for_path.append(action)

        return sorted(_actions_by_path)

    @staticmethod
    def _merge_actions(to_merge, updated):
        to_merge.status = updated.status
        to_merge.updated = updated.updated
        if updated.restored_till:
            to_merge.restored_till = updated.restored_till
        return to_merge

    @staticmethod
    def _get_restore_operation_id(action):
        return "restore_{}".format(action.path)
