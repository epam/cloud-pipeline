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
from pipeline import PipelineAPI

from sls.model.rule_model import LifecycleRuleParser, StorageLifecycleNotification
from sls.model.restore_model import StorageLifecycleRestoreAction


def configure_cp_data_source(cp_api_url, cp_api_token, api_log_dir, logger, data_source_type="RESTApi"):
    data_source = None
    if data_source_type is "RESTApi":
        if not cp_api_url:
            raise RuntimeError("Cloud Pipeline data source cannot be configured! Please specify --cp-api-url")
        if cp_api_token:
            os.environ["API_TOKEN"] = cp_api_token
        if not os.getenv("API_TOKEN"):
            raise RuntimeError("Cloud Pipeline data source cannot be configured! "
                               "Please specify --cp-api-token or API_TOKEN environment variable")
        api = PipelineAPI(cp_api_url, api_log_dir)
        data_source = RESTApiCloudPipelineDataSource(api, logger)
    return data_source


class CloudPipelineDataSource:

    def load_storage(self, datastorage_id):
        pass

    def load_available_storages(self):
        pass

    def load_lifecycle_rules_for_storage(self, datastorage_id):
        pass

    def load_lifecycle_rule(self, datastorage_id, rule_id):
        pass

    def create_lifecycle_rule_execution(self, datastorage_id, rule_id, execution):
        pass

    def load_lifecycle_rule_executions(self, datastorage_id, rule_id, path=None, status=None):
        pass

    def update_status_lifecycle_rule_execution(self, datastorage_id, execution_id, status):
        pass

    def delete_lifecycle_rule_execution(self, datastorage_id, execution_id):
        pass

    def send_notification(self, subject, body, to_user, copy_users, parameters):
        pass

    def load_role(self, role_id):
        pass

    def load_role_by_name(self, role_name):
        pass

    def load_user_by_name(self, username):
        pass

    def load_user(self, user_id):
        pass

    def load_preference(self, preference_name):
        pass

    def load_regions(self):
        pass

    def _load_default_lifecycle_rule_notification(self):
        pass


class RESTApiCloudPipelineDataSource(CloudPipelineDataSource):

    DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE = "DATASTORAGE_LIFECYCLE_ACTION"

    def __init__(self, api, logger):
        self.api = api
        self.logger = logger
        self.parser = LifecycleRuleParser(self._load_default_lifecycle_rule_notification())

    def load_available_storages(self):
        return self.api.load_available_storages()

    def load_storage(self, datastorage_id):
        return self.api.find_datastorage(datastorage_id)

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

    def load_lifecycle_rule_executions(self, datastorage_id, rule_id, path=None, status=None):
        executions_json = self.api.load_lifecycle_rule_executions(datastorage_id, rule_id, path, status)
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

    def load_role_by_name(self, role_name):
        return self.api.load_role_by_name(role_name)

    def load_regions(self):
        return self.api.get_regions()

    def load_user_by_name(self, username):
        return self.api.load_user_by_name(username)

    def load_user(self, user_id):
        return self.api.load_user(user_id)

    def load_preference(self, preference_name):
        return self.api.get_preference(preference_name)

    def filter_restore_actions(self, datastorage_id, filter_obj):
        api_response_object = self.api.filter_lifecycle_restore_action(datastorage_id, filter_obj)
        return [StorageLifecycleRestoreAction.parse_from_dict(obj_dict) for obj_dict in api_response_object] if api_response_object else []

    def update_restore_action(self, action):
        data = {
            "id": action.action_id,
            "datastorageId": action.datastorage_id,
            "path": action.path,
            "pathType": action.path_type,
            "status": action.status,
            "restoredTill": action.restored_till
        }
        self.api.update_lifecycle_restore_action(action.datastorage_id, data)

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
        default_lifecycle_rule_prolong_days = self.load_preference("storage.lifecycle.prolong.days")
        default_lifecycle_rule_notify_before_days = self.load_preference("storage.lifecycle.notify.before.days")
        if not default_lifecycle_notification_settings or not default_lifecycle_notification_template \
                or not default_lifecycle_rule_prolong_days or not default_lifecycle_rule_notify_before_days:
            return None

        recipients = [{"name": "ROLE_ADMIN", "principal": False}] if "keepInformedAdmins" in default_lifecycle_notification_settings else []
        if "informedUserIds" in default_lifecycle_notification_settings:
            for user_id in default_lifecycle_notification_settings["informedUserIds"]:
                try:
                    user = self.api.load_user(int(user_id))
                    recipients.append({
                        "name": user["userName"],
                        "principal": True
                    })
                except Exception as e:
                    self.logger.log("Fail to load user by id: {}. Will skip it!".format(str(user_id)))

        return StorageLifecycleNotification(
            notify_before_days=int(default_lifecycle_rule_notify_before_days["value"]),
            prolong_days=int(default_lifecycle_rule_prolong_days["value"]),
            recipients=recipients,
            enabled=default_lifecycle_notification_settings["enabled"] if "enabled" in default_lifecycle_notification_settings else False,
            subject=default_lifecycle_notification_template["subject"] if "subject" in default_lifecycle_notification_template else "",
            body=default_lifecycle_notification_template["body"] if "body" in default_lifecycle_notification_template else None
        )
