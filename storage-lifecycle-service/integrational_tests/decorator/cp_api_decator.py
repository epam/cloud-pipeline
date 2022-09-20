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

from sls.app.cp_api_interface import CloudPipelineDataSource


class MockedNotificationRESTApiCloudPipelineDataSource(CloudPipelineDataSource):

    def __init__(self, cp_source):
        self.cp_source = cp_source

    def load_storage(self, datastorage_id):
        return self.cp_source.load_storage(datastorage_id)

    def load_available_storages(self):
        return self.cp_source.load_available_storages()

    def load_lifecycle_rules_for_storage(self, datastorage_id):
        return self.cp_source.load_lifecycle_rules_for_storage(datastorage_id)

    def create_lifecycle_rule_execution(self, datastorage_id, rule_id, execution):
        return self.cp_source.create_lifecycle_rule_execution(datastorage_id, rule_id, execution)

    def load_lifecycle_rule_executions(self, datastorage_id, rule_id, path=None, status=None):
        return self.cp_source.load_lifecycle_rule_executions(datastorage_id, rule_id, path, status)

    def update_status_lifecycle_rule_execution(self, datastorage_id, execution_id, status):
        return self.cp_source.update_status_lifecycle_rule_execution(datastorage_id, execution_id, status)

    def delete_lifecycle_rule_execution(self, datastorage_id, execution_id):
        return self.cp_source.delete_lifecycle_rule_execution(datastorage_id, execution_id)

    def send_notification(self, subject, body, to_user, copy_users, parameters):
        return {
            "mocked": "true"
        }

    def load_role(self, role_id):
        return self.cp_source.load_role(role_id)

    def load_role_by_name(self, role_name):
        return self.cp_source.load_role_by_name(role_name)

    def load_user_by_name(self, username):
        return self.cp_source.load_user_by_name(username)

    def load_user(self, user_id):
        return self.cp_source.load_user(user_id)

    def load_preference(self, preference_name):
        return self.cp_source.load_preference(preference_name)

    def load_regions(self):
        return self.cp_source.load_regions()

    def _load_default_lifecycle_rule_notification(self):
        return self.cp_source._load_default_lifecycle_rule_notification()
