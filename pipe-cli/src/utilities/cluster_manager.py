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

import math
import os
from src.api.cluster import Cluster

DEFAULT_CORE_TYPE = 'c4'
CORE_TYPE_DELIMITER = '.'

class ClusterManager(object):
    @classmethod
    def calculate_cluster_from_cores(cls, ncores, core_type=None):
        core_type = ClusterManager.get_core_type(core_type=core_type)
        
        instance_types_list = Cluster.list_instance_types()
        if len(instance_types_list) == 0:
            raise RuntimeError("No instance types found")
        return ClusterManager.get_instance_type(core_type, instance_types_list, ncores)

    @classmethod
    def get_core_type(cls, core_type=None):
        if core_type:
            return ClusterManager.parse_core_type(core_type)

        core_type_from_env = os.environ.get('instance_type')
        if not core_type_from_env:
            core_type_from_env = os.environ.get('instance_size')
        if not core_type_from_env:
            core_type_from_env = DEFAULT_CORE_TYPE

        return ClusterManager.parse_core_type(core_type_from_env)

    @classmethod
    def parse_core_type(cls, core_type):
        if CORE_TYPE_DELIMITER in core_type:
            return core_type.split(CORE_TYPE_DELIMITER)[0]

        return core_type

    @classmethod
    def get_cores_by_instance_type(cls, instance_cores, cores):
        result = instance_cores
        while cores > result:
            result += instance_cores
        return result

    @classmethod
    def get_instance_type (cls, core_type, instances, cores):
        instances = [x for x in instances if core_type in x.name]

        if len(instances) == 0:
            raise RuntimeError("No instances found for type {}".format(core_type))

        if cores == 0:
            return ClusterManager.get_instance_type_object('', 0, 0)

        instances_sorted = sorted(instances, key=lambda x: x.vcpu, reverse=True)
        instance_num = 0
        for instance_num in range(0, len(instances_sorted)-1):
            instance_type = instances_sorted[instance_num]
            instance_cores = instance_type.vcpu
            instance_name = instance_type.name
            if instance_cores > cores:
                continue
            elif instance_cores == cores:
                return ClusterManager.get_instance_type_object(instance_name, instance_cores, 1)
            else:
                total_cores = ClusterManager.get_cores_by_instance_type(instance_cores, cores)
                total_nodes = math.ceil(float(cores)/float(instance_cores))

                total_cores_free = total_cores - cores
                node_free_cores = total_cores_free / total_nodes
                if node_free_cores * 2 >= instances_sorted[instance_num+1].vcpu:
                    continue
                else:
                    break

        return ClusterManager.get_instance_type_object(instance_name, instance_cores, total_nodes)

    @classmethod
    def get_instance_type_object(cls, instance_name, instance_cores, total_nodes):
        return { "name": instance_name, "cores": instance_cores, "count": total_nodes }

