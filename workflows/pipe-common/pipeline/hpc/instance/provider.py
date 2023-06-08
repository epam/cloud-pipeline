#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.


class Instance:

    def __init__(self, name, price_type, cpu, gpu, mem):
        """
        Execution instance.
        """
        self.name = name
        self.price_type = price_type
        self.cpu = cpu
        self.gpu = gpu
        self.mem = mem

    @staticmethod
    def from_cp_response(instance):
        return Instance(name=instance['name'],
                        price_type=instance['termType'],
                        cpu=int(instance['vcpu']),
                        gpu=int(instance['gpu']),
                        mem=int(instance['memory']))

    def __eq__(self, other):
        return self.__dict__ == other.__dict__

    def __repr__(self):
        return str(self.__dict__)


class GridEngineInstanceProvider:

    def provide(self):
        pass


class DescendingInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner):
        self._inner = inner

    def provide(self):
        return sorted(self._inner.provide(),
                      key=lambda instance: instance.cpu,
                      reverse=True)


class AvailableInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner, availability_manager):
        self._inner = inner
        self._availability_manager = availability_manager

    def provide(self):
        return list(self._provide())

    def _provide(self):
        instances = self._inner.provide()
        unavailable_instance_types = list(self._availability_manager.get_unavailable())
        for instance in instances:
            if instance.name in unavailable_instance_types:
                continue
            yield instance


class SizeLimitingInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner, max_instance_cores):
        self.inner = inner
        self.instance_cores = max_instance_cores

    def provide(self):
        return [instance for instance in self.inner.provide()
                if instance.cpu <= self.instance_cores]


class FamilyInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner, instance_cloud_provider, instance_family, common_utils):
        self.inner = inner
        self.instance_cloud_provider = instance_cloud_provider
        self.instance_family = instance_family
        self.common_utils = common_utils

    def provide(self):
        return sorted([instance for instance in self.inner.provide()
                       if self._is_part_of_family(instance.name)],
                      key=lambda instance: instance.cpu)

    def _is_part_of_family(self, instance_type):
        return self.common_utils.extract_family_from_instance_type(self.instance_cloud_provider, instance_type) \
            == self.instance_family


class DefaultInstanceProvider(GridEngineInstanceProvider):

    def __init__(self, inner, instance_type):
        self.inner = inner
        self.instance_type = instance_type

    def provide(self):
        return [instance for instance in self.inner.provide()
                if instance.name == self.instance_type]
