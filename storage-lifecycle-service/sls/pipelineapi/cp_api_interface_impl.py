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

from sls.app.cp_api_interface import CloudPipelineDataSource
from sls.pipelineapi.model.common_model import CloudPipelineNotification
from sls.pipelineapi.model.archive_rule_model import LifecycleRuleParser, StorageLifecycleNotification
from sls.pipelineapi.model.restore_action_model import StorageLifecycleRestoreAction


def configure_pipeline_api(cp_api_url, cp_api_token, api_log_dir, logger, data_source_type="RESTApi"):
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


class RESTApiCloudPipelineDataSource(CloudPipelineDataSource):

    DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE = "DATASTORAGE_LIFECYCLE_ACTION"

    def __init__(self, api, logger):
        self.api = api
        self.logger = logger
        self.parser = LifecycleRuleParser()

    def load_available_storages(self):
        return self.api.load_available_storages()

    def load_storages_with_lifecycle(self, lifecycle_type):
        return self.api.load_storages_with_lifecycle(lifecycle_type)

    def load_storage(self, datastorage_id):
        return self.api.find_datastorage(datastorage_id)

    def load_lifecycle_rules_for_storage(self, datastorage_id):
        rules_json = self.api.load_lifecycle_rules_for_storage(datastorage_id)
        if rules_json:
            default_notification = self._load_default_lifecycle_rule_notification()
            return [self.parser.parse_rule(rule, default_notification) for rule in rules_json if rules_json]
        else:
            return []

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
            "type": action.path_type,
            "status": action.status,
            "restoredTill": action.restored_till
        }
        return self.api.update_lifecycle_restore_action(action.datastorage_id, data)

    def load_notification(self, notification_type):
        notification_template = next(
            filter(
                lambda t: t["name"] == notification_type,
                self.api.load_notification_templates()
            ), None
        )

        notification_settings = next(
            filter(
                lambda t: t["type"] == notification_type,
                self.api.load_notification_settings()
            ), None
        )

        if not notification_template or not notification_settings:
            raise RuntimeError("Failed to load notification with type: {}, template: {} settings: {}"
                               .format(notification_type, notification_template, notification_settings))

        return CloudPipelineNotification.build_from_dicts(notification_template, notification_settings)

    def load_entity_permissions(self, entity_id, entity_class):
        return self.api.get_entity_permissions(entity_id, entity_class)

    def _load_default_lifecycle_rule_notification(self):
        notification = self.load_notification(self.DATASTORAGE_LIFECYCLE_ACTION_NOTIFICATION_TYPE)
        default_rule_prolong_days = self.load_preference("storage.lifecycle.prolong.days")
        default_rule_notify_before_days = self.load_preference("storage.lifecycle.notify.before.days")
        if not notification or not default_rule_prolong_days or not default_rule_notify_before_days:
            return None

        recipients = [{"name": "ROLE_ADMIN", "principal": False}] if notification.settings.keep_informed_admins else []
        for user_id in notification.settings.informed_user_ids:
            try:
                user = self.api.load_user(int(user_id))
                recipients.append({
                    "name": user["userName"],
                    "principal": True
                })
            except Exception as e:
                self.logger.log("Fail to load user by id: {}. Will skip it!".format(str(user_id)))

        return StorageLifecycleNotification(
            notify_before_days=int(default_rule_notify_before_days["value"]),
            prolong_days=int(default_rule_prolong_days["value"]),
            recipients=recipients,
            enabled=notification.settings.enabled,
            subject=notification.template.subject if notification.template.subject else "",
            body=notification.template.body if notification.template.body else "",
            notify_users=False
        )
