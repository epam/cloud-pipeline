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

from sls.model.rule_model import LifecycleRuleParser


class CloudPipelineDataSource:

    def __init__(self):
        pass

    def load_datastorage(self, datastorage_id):
        pass

    def load_lifecycle_rules_for_storage(self, datastorage_id):
        pass

    def load_lifecycle_rule(self, datastorage_id, rule_id):
        pass

    def create_lifecycle_rule_execution(self, datastorage_id, rule_id, execution):
        pass

    def load_lifecycle_rule_executions(self, datastorage_id, rule_id):
        pass

    def load_lifecycle_rule_executions_by_path(self, datastorage_id, rule_id, path):
        pass

    def update_status_lifecycle_rule_execution(self, datastorage_id, execution_id, status):
        pass

    def delete_lifecycle_rule_execution(self, datastorage_id, execution_id):
        pass

    def load_default_lifecycle_rule_notification(self):
        pass

    def send_notification(self, subject, body, to_user, copy_users, parameters):
        pass

    def load_role(self, role_id):
        pass

    def load_user_by_name(self, username):
        pass


class RESTApiCloudPipelineDataSource:

    DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE = "DATASTORAGE_LIFECYCLE_ACTION"

    def __init__(self, api):
        self.api = api
        self.parser = LifecycleRuleParser(self._load_default_lifecycle_rule_notification())

    def load_available_storages(self):
        return self.api.load_available_storages()

    def load_lifecycle_rules_for_storage(self, datastorage_id):
        rules_json = self.api.load_lifecycle_rules_for_storage(datastorage_id)
        return [self.parser.parse_rule(rule) for rule in (rules_json if rules_json else [])]

    def load_lifecycle_rule(self, datastorage_id, rule_id):
        rule_json = self.api.load_lifecycle_rule(datastorage_id, rule_id)
        return self.parser.parse_rule(rule_json)

    def create_lifecycle_rule_execution(self, datastorage_id, rule_id, execution):
        return self.parser.parse_execution(
            self.api.create_lifecycle_rule_execution(datastorage_id, rule_id, execution)
        )

    def load_lifecycle_rule_executions(self, datastorage_id, rule_id):
        executions_json = self.api.load_lifecycle_rule_executions(datastorage_id, rule_id)
        return [self.parser.parse_execution(execution) for execution in (executions_json if executions_json else [])]

    def load_lifecycle_rule_executions_by_path(self, datastorage_id, rule_id, path):
        executions_json = self.api.load_lifecycle_rule_executions_by_path(datastorage_id, rule_id, path)
        return [self.parser.parse_execution(execution) for execution in (executions_json if executions_json else [])]

    def update_status_lifecycle_rule_execution(self, datastorage_id, execution_id, status):
        return self.parser.parse_execution(
            self.api.update_status_lifecycle_rule_execution(datastorage_id, execution_id, status)
        )

    def delete_lifecycle_rule_execution(self, datastorage_id, execution_id):
        return self.parser.parse_execution(
            self.api.delete_lifecycle_rule_execution(datastorage_id, execution_id)
        )

    def send_notification(self, subject, body, to_user, copy_users, parameters):
        return self.api.create_notification(subject, body, to_user, copy_users, parameters)

    def load_role(self, role_id):
        return self.api.load_role(role_id)

    def load_regions(self):
        return self.api.get_regions()

    def load_user_by_name(self, username):
        return self.api.load_user_by_name(username)

    def load_user(self, user_id):
        return self.api.load_user(user_id)

    def _load_default_lifecycle_rule_notification(self):
        default_lifecycle_notification_template = next(
            filter(
                lambda t: t["name"] == self.DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE,
                self.api.load_notification_templates()
            ), None
        )

        default_lifecycle_notification_settings = next(
            filter(
                lambda t: t["type"] == self.DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE,
                self.api.load_notification_settings()
            ), None
        )
        default_lifecycle_rule_prolong_days = self.api.get_preference("storage.lifecycle.prolong.days")
        default_lifecycle_rule_notify_before_days = self.api.get_preference("storage.lifecycle.notify.before.days")
        if not default_lifecycle_notification_settings or not default_lifecycle_notification_template \
                or not default_lifecycle_rule_prolong_days or not default_lifecycle_rule_notify_before_days:
            return None

        return {
            "id": default_lifecycle_notification_template["id"],
            "informedUserIds": default_lifecycle_notification_settings["informedUserIds"] if "informedUserIds" in default_lifecycle_notification_settings else [],
            "keepInformedAdmins": default_lifecycle_notification_settings["keepInformedAdmins"] if "keepInformedAdmins" in default_lifecycle_notification_settings else False,
            "keepInformedOwners": default_lifecycle_notification_settings["keepInformedOwner"] if "keepInformedOwner" in default_lifecycle_notification_settings else False,
            "enabled": default_lifecycle_notification_settings["enabled"] if "enabled" in default_lifecycle_notification_settings else False,
            "subject": default_lifecycle_notification_template["subject"] if "subject" in default_lifecycle_notification_template else "",
            "body": default_lifecycle_notification_template["body"] if "body" in default_lifecycle_notification_template else "",
            "prolongDays": int(default_lifecycle_rule_prolong_days["value"]),
            "notifyBeforeDays": int(default_lifecycle_rule_notify_before_days["value"])
        }
