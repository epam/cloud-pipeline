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
import os

from sls.util.date_utils import parse_timestamp


class StorageLifecycleRestoreNotification:

    def __init__(self, enabled, recipients, notify_users):
        self.enabled = enabled
        self.recipients = recipients
        self.notify_users = notify_users

    @staticmethod
    def parse_from_dict(obj_dict):
        if "enabled" not in obj_dict:
            raise RuntimeError("Lifecycle restore notification object doesn't have 'enabled' flag!")
        enabled = obj_dict["enabled"]
        recipients = None
        notify_users = False
        if obj_dict["enabled"]:
            if "recipients" not in obj_dict or len(obj_dict["recipients"]) < 1:
                raise RuntimeError("Lifecycle restore notification object with 'enabled' = true, "
                                   "should have 'recipients' list in place!")
            for recipient in obj_dict["recipients"]:
                if "name" not in recipient or "principal" not in recipient:
                    raise RuntimeError("Wrong format of 'recipient' object, should have 'name' and 'principal'")
            recipients = obj_dict["recipients"]
            notify_users = obj_dict.get('notifyUsers', False)
        return StorageLifecycleRestoreNotification(enabled, recipients, notify_users)


class StorageLifecycleRestoreAction:

    def __init__(self, action_id, datastorage_id, user_actor_id, path, path_type, restore_versions, restore_mode,
                 days, started, updated, status, restored_till, notification):

        self.action_id = action_id
        self.datastorage_id = datastorage_id
        self.user_actor_id = user_actor_id
        self.path = path
        self.path_type = path_type
        self.restore_versions = restore_versions
        self.restore_mode = restore_mode
        self.days = days
        self.started = started
        self.updated = updated
        self.status = status
        self.restored_till = restored_till
        self.notification = notification

    @staticmethod
    def parse_from_dict(obj_dict):

        _required_fields_names = [
            "id", "datastorageId", "userActorId", "path", "type",
            "days", "started", "updated", "status", "notification"
        ]

        for _required_field in _required_fields_names:
            if _required_field not in obj_dict:
                raise RuntimeError("Cannot parse StorageLifecycleRestoreAction. "
                                   "Field: '{}' is not present in dictionary object!".format(_required_field))

        return StorageLifecycleRestoreAction(
            obj_dict["id"], obj_dict["datastorageId"], obj_dict["userActorId"], obj_dict["path"], obj_dict["type"],
            restore_versions=obj_dict["restoreVersions"] if "restoreVersions" in obj_dict else False,
            restore_mode=obj_dict["restoreMode"] if "restoreMode" in obj_dict else "STANDARD",
            days=obj_dict["days"],
            started=parse_timestamp(obj_dict["started"]),
            updated=parse_timestamp(obj_dict["updated"]),
            status=obj_dict["status"],
            restored_till=parse_timestamp(obj_dict["restoredTill"]) if "restoredTill" in obj_dict else None,
            notification=StorageLifecycleRestoreNotification.parse_from_dict(obj_dict["notification"])
        )

    def copy(self):
        return StorageLifecycleRestoreAction(
            self.action_id, self.datastorage_id, self.user_actor_id, self.path, self.path_type,
            restore_versions=self.restore_versions,
            restore_mode=self.restore_mode,
            days=self.days,
            started=self.started,
            updated=self.updated,
            status=self.status,
            restored_till=self.restored_till,
            notification=self.notification
        )
