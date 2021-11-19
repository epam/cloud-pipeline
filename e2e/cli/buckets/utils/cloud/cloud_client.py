# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

from abc import ABCMeta, abstractmethod
from time import sleep


class CloudClient(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def object_exists(self, bucket_name, key):
        pass

    @abstractmethod
    def folder_exists(self, bucket_name, key):
        pass

    @abstractmethod
    def assert_policy(self, bucket_name, sts, lts, backup_duration):
        pass

    @abstractmethod
    def get_modification_date(self, path):
        pass

    @abstractmethod
    def get_versions(self, bucket_name, key):
        pass

    @abstractmethod
    def wait_for_bucket_creation(self, bucket_name):
        pass

    @abstractmethod
    def wait_for_bucket_deletion(self, bucket_name):
        pass

    def _wait_unless(self, is_ready, attempts=20, delay=3):
        for attempt in range(attempts):
            if is_ready():
                return True
            sleep(delay)
        return False
