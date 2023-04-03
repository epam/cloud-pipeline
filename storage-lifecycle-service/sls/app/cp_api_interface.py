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
class CloudPipelineDataSource:

    def load_storage(self, datastorage_id):
        pass

    def load_available_storages(self):
        pass

    def load_lifecycle_rules_for_storage(self, datastorage_id):
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

    def load_entity_permissions(self, entity_id, entity_class):
        pass

    def _load_default_lifecycle_rule_notification(self):
        pass
