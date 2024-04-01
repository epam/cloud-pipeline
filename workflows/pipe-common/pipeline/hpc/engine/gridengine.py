# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
from datetime import datetime

from pipeline.hpc.logger import Logger
from pipeline.hpc.valid import WorkerValidatorHandler


def _perform_command(action, msg, error_msg, skip_on_failure):
    Logger.info(msg)
    try:
        action()
    except Exception as e:
        Logger.warn(error_msg)
        if not skip_on_failure:
            raise RuntimeError(error_msg, e)


class GridEngineType:

    SGE = "SGE"
    SLURM = "SLURM"
    KUBE = "KUBE"

    def __init__(self):
        pass


class AllocationRuleParsingError(RuntimeError):
    pass


class AllocationRule:
    ALLOWED_VALUES = ['$pe_slots', '$fill_up', '$round_robin']

    def __init__(self, value):
        if value in AllocationRule.ALLOWED_VALUES:
            self.value = value
        else:
            raise AllocationRuleParsingError('Wrong AllocationRule value, only %s is available!' % AllocationRule.ALLOWED_VALUES)

    @staticmethod
    def pe_slots():
        return AllocationRule('$pe_slots')

    @staticmethod
    def fill_up():
        return AllocationRule('$fill_up')

    @staticmethod
    def round_robin():
        return AllocationRule('$round_robin')

    @staticmethod
    def fractional_rules():
        return [AllocationRule.round_robin(), AllocationRule.fill_up()]

    @staticmethod
    def integral_rules():
        return [AllocationRule.pe_slots()]

    def __eq__(self, other):
        if not isinstance(other, AllocationRule):
            # don't attempt to compare against unrelated types
            return False
        return other.value == self.value


class GridEngineJobState:
    RUNNING = 'running'
    PENDING = 'pending'
    SUSPENDED = 'suspended'
    ERROR = 'errored'
    DELETED = 'deleted'
    COMPLETED = 'completed'
    UNKNOWN = 'unknown'

    @staticmethod
    def from_letter_code(code, state_to_codes):
        for state, codes in state_to_codes.items():
            if code in codes:
                return state
        return GridEngineJobState.UNKNOWN


class GridEngineJob:

    def __init__(self, id, root_id, name, user, state, datetime, hosts=None,
                 cpu=0, gpu=0, mem=0, requests=None,
                 pe='local'):
        self.id = id
        self.root_id = root_id
        self.name = name
        self.user = user
        self.state = state
        self.datetime = datetime
        self.hosts = hosts if hosts else []
        self.cpu = cpu
        self.gpu = gpu
        self.mem = mem
        self.requests = requests or {}
        self.pe = pe

    def __repr__(self):
        return str(self.__dict__)


class GridEngine(WorkerValidatorHandler):

    def get_jobs(self):
        pass

    def disable_host(self, host):
        """
        Disables host to prevent receiving new jobs from the queue.
        This command does not abort currently running jobs.

        :param host: Host to be enabled.
        """
        pass

    def enable_host(self, host):
        """
        Enables host to make it available to receive new jobs from the queue.

        :param host: Host to be enabled.
        """
        pass

    def get_pe_allocation_rule(self, pe):
        """
        Returns allocation rule of the pe

        :param pe: Parallel environment to return allocation rule.
        """
        pass

    def delete_host(self, host, skip_on_failure=False):
        """
        Completely deletes host from GE:
        1. Shutdown host execution daemon.
        2. Removes host from queue settings.
        3. Removes host from host group.
        4. Removes host from administrative hosts.
        5. Removes host from GE.

        :param host: Host to be removed.
        :param skip_on_failure: Specifies if the host killing should be continued even if some of
        the commands has failed.
        """
        pass

    def get_host_supplies(self):
        pass

    def get_host_supply(self, host):
        pass

    def get_engine_type(self):
        pass

    def is_valid(self, host):
        """
        Validates host in GE checking corresponding execution host availability and its states.

        :param host: Host to be checked.
        :return: True if execution host is valid.
        """
        return True

    def kill_jobs(self, jobs, force=False):
        """
        Kills jobs in GE.

        :param jobs: Grid engine jobs.
        :param force: Specifies if this command should be performed with -f flag.
        """
        pass


class GridEngineDemandSelector:

    def select(self, jobs):
        pass


class GridEngineJobValidator:

    def validate(self, jobs):
        pass


class GridEngineLaunchAdapter:

    def get_worker_init_task_name(self):
        pass

    def get_worker_launch_params(self):
        pass


class GridEngineResourceParser:

    def __init__(self, datatime_format, cpu_unit=None, cpu_modifiers=None, mem_unit=None, mem_modifiers=None):
        self._datetime_format = datatime_format
        self._cpu_unit = cpu_unit
        self._cpu_modifiers = cpu_modifiers or {}
        self._mem_unit = mem_unit
        self._mem_modifiers = mem_modifiers or {}

    def parse_date(self, timestamp):
        return datetime.strptime(timestamp, self._datetime_format)

    def parse_cpu(self, quantity):
        return self._parse_resource(quantity, modifiers=self._cpu_modifiers, unit=self._cpu_unit)

    def parse_mem(self, quantity):
        return self._parse_resource(quantity, modifiers=self._mem_modifiers, unit=self._mem_unit)

    def _parse_resource(self, quantity, modifiers, unit):
        if not quantity:
            return 0
        if len(quantity) > 1 and quantity[-2:] in modifiers:
            value, value_unit = int(quantity[:-2]), quantity[-2:]
        elif len(quantity) > 0 and quantity[-1:] in modifiers:
            value, value_unit = int(quantity[:-1]), quantity[-1:]
        else:
            value, value_unit = int(quantity), ''
        modifier = modifiers[value_unit]
        output = float(value * modifier)
        return int(math.ceil(output / modifiers.get(unit, 1)))
