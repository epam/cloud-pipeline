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

import functools
import operator
from collections import Counter

from pipeline.hpc.logger import Logger
from pipeline.hpc.resource import ResourceSupply, IntegralDemand, FractionalDemand


class InstanceSelectionError(RuntimeError):
    pass


class InstanceDemand:

    def __init__(self, instance, owner):
        """
        Execution instance demand.
        """
        self.instance = instance
        self.owner = owner

    def __eq__(self, other):
        return self.__dict__ == other.__dict__

    def __repr__(self):
        return str(self.__dict__)


class GridEngineInstanceSelector:

    def select(self, demands):
        pass


class CpuCapacityInstanceSelector(GridEngineInstanceSelector):

    def __init__(self, instance_provider, reserved_supply):
        """
        CPU capacity instance selector.

        CPU capacity is a number of job CPU requirements a single instance can process.
        If a bigger instance can process more job CPU requirements then it will be selected.

        :param instance_provider: Cloud Pipeline instance provider.
        :param reserved_supply: Instance reserved resource supply.
        """
        self.instance_provider = instance_provider
        self.reserved_supply = reserved_supply

    def select(self, demands):
        instances = self.instance_provider.provide()
        remaining_demands = demands
        while remaining_demands:
            best_capacity = 0
            best_instance = None
            best_remaining_demands = None
            best_fulfilled_demands = None
            for instance in instances:
                supply = ResourceSupply.of(instance) - self.reserved_supply
                current_remaining_demands, current_fulfilled_demands = self._apply(remaining_demands, supply)
                current_fulfilled_demand = functools.reduce(operator.add, current_fulfilled_demands, IntegralDemand())
                current_capacity = current_fulfilled_demand.cpu
                if current_capacity > best_capacity:
                    best_capacity = current_capacity
                    best_instance = instance
                    best_remaining_demands = current_remaining_demands
                    best_fulfilled_demands = current_fulfilled_demands
            remaining_demands = best_remaining_demands
            if not best_instance:
                Logger.info('There are no available instance types.')
                break
            best_instance_owner = self._resolve_owner(best_fulfilled_demands)
            Logger.info('Selecting %s instance using %s/%s cpu for %s user...'
                        % (best_instance.name, best_capacity, best_instance.cpu, best_instance_owner))
            yield InstanceDemand(best_instance, best_instance_owner)

    def _apply(self, demands, supply):
        remaining_supply = supply
        remaining_demands = []
        fulfilled_demands = []
        for i, demand in enumerate(demands):
            if not remaining_supply:
                remaining_demands.extend(demands[i:])
                break
            if isinstance(demand, IntegralDemand):
                current_remaining_supply, remaining_demand = remaining_supply.subtract(demand)
                if remaining_demand:
                    remaining_demands.append(demand)
                else:
                    fulfilled_demands.append(demand)
                    remaining_supply = current_remaining_supply
            elif isinstance(demand, FractionalDemand):
                current_remaining_supply, remaining_demand = remaining_supply.subtract(demand)
                if remaining_demand:
                    remaining_demands.append(remaining_demand)
                fulfilled_demand = demand - remaining_demand
                fulfilled_demands.append(fulfilled_demand)
                remaining_supply = current_remaining_supply
            else:
                raise InstanceSelectionError('Unsupported demand type %s.', type(demand))
        return remaining_demands, fulfilled_demands

    def _resolve_owner(self, demands):
        owner_cpus_counter = sum([Counter({demand.owner: demand.cpu}) for demand in demands], Counter())
        return owner_cpus_counter.most_common()[0][0]


class NaiveCpuCapacityInstanceSelector(GridEngineInstanceSelector):

    def __init__(self, instance_provider, reserved_supply):
        """
        Naive CPU capacity instance selector.

        CPU capacity is a number of job CPU requirements a single instance can process.
        If a bigger instance can process more job CPU requirements then it will be selected.
        Handles resource demands as if they were fractional.

        :param instance_provider: Cloud Pipeline instance provider.
        :param reserved_supply: Instance reserved resource supply.
        """
        self.instance_provider = instance_provider
        self.instance_selector = CpuCapacityInstanceSelector(instance_provider, reserved_supply)

    def select(self, demands):
        fractional_demands = [demand if isinstance(demand, FractionalDemand)
                              else FractionalDemand(cpu=demand.cpu,
                                                    gpu=demand.gpu,
                                                    mem=demand.mem,
                                                    owner=demand.owner)
                              for demand in demands]
        return self.instance_selector.select(fractional_demands)


class BackwardCompatibleInstanceSelector(GridEngineInstanceSelector):

    def __init__(self, instance_provider, reserved_supply, batch_size):
        """
        Backward compatible CPU capacity instance selector.

        Non batch autoscaling works the same way as in the previous versions of grid engine autoscaler.
        Batch autoscaling uses cpu capacity strategy to select instances.

        :param instance_provider: Cloud Pipeline instance provider.
        :param reserved_supply: Instance reserved resource supply.
        :param batch_size: Scaling up batch size.
        """
        if batch_size > 1:
            self.instance_selector = CpuCapacityInstanceSelector(instance_provider, reserved_supply)
        else:
            self.instance_selector = NaiveCpuCapacityInstanceSelector(instance_provider, reserved_supply)

    def select(self, demands):
        return self.instance_selector.select(demands)
