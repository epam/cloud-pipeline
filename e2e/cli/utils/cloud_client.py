# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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


class CloudClient(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def describe_instance(self, run_id):
        pass

    @abstractmethod
    def get_private_ip(self, instance):
        pass

    @abstractmethod
    def terminate_instance(self, run_id):
        pass

    @abstractmethod
    def node_price_type_should_be(self, run_id, spot):
        pass
