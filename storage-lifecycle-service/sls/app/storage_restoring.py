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

    RUNNING_STATUSES = ["INITIALIZED", "RUNNING"]
    ACTIVE_STATUSES = ["SUCCEEDED", "INITIALIZED", "RUNNING"]
    TERMINAL_STATUSES = ["SUCCEEDED", "CANCELLED", "FAILED"]

    def _sync_storage(self, storage):
        ongoing_restore_actions = self.cp_data_source.filter_restore_actions(
            storage.id,
            filter_obj={"statuses": self.RUNNING_STATUSES}
        )

        restore_action_paths = self._build_restore_action_path(ongoing_restore_actions)
        for path in restore_action_paths:
            restore_action_hierarchy.get_parent_actions(path)

    @staticmethod
    def _build_restore_action_path(ongoing_restore_actions):
        result = set()
        for action in ongoing_restore_actions:
            result.add(action.path)
        return sorted(result)

