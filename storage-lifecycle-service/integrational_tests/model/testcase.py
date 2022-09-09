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

class TestCase:

    def __init__(self, cloud, platform, result):
        self.cloud = cloud
        self.platform = platform
        self.result = result


class TestCaseCloudState:

    def __init__(self, storages=None):
        self.storages = storages


class TestCasePlatformState:

    def __init__(self, storages=None):
        if storages is None:
            storages = []
        self.storages = storages


class TestCaseStorageCloudState:

    def __init__(self):
        self.storage_provider = None
        self.storage = None
        self.files = []

    def with_storage_name(self, storage):
        self.storage = storage
        return self

    def with_file(self, file):
        self.files.append(file)
        return self

    def with_storage_provider(self, storage_provider):
        self.storage_provider = storage_provider
        return self


class TestCaseFile:

    def __init__(self, path, storage_date_shift, storage_class, tags):
        self.path = path
        self.storage_date_shift = storage_date_shift
        self.storage_class = storage_class
        self.tags = tags


class TestCasePlatformStorageState:

    def __init__(self, datastorage_id, storage, rules=None, executions=None):
        self.datastorage_id = datastorage_id
        self.storage = storage
        if rules is None:
            rules = []
        if executions is None:
            executions = []

        self.rules = rules
        self.executions = executions

    def with_rule(self, rule):
        self.rules.append(rule)
        return self

    def with_execution(self, execution):
        self.executions.append(execution)
        return self


class TestCaseResult:

    def __init__(self):
        self.cloud = None
        self.platform = None

    def with_cloud_state(self, state):
        self.cloud = state
        return self

    def with_platform_state(self, state):
        self.platform = state
        return self

    def merge(self, to_merge):
        if not to_merge:
            return self
        if to_merge.cloud:
            if self.cloud:
                self.cloud.storages.extend(
                    to_merge.cloud.storages if to_merge.cloud.storages else [])
            else:
                self.cloud = to_merge.cloud
        if to_merge.platform:
            if self.platform:
                self.platform.storages.extend(
                    to_merge.platform.storages if to_merge.cloud.storages else [])
            else:
                self.platform = to_merge.platform
        return self
