#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import datetime

from pipeline.hpc.logger import Logger


class WorkerValidator:

    def validate(self):
        pass


class WorkerValidatorHandler:

    def is_valid(self, host):
        pass


class GracePeriodWorkerValidatorHandler(WorkerValidatorHandler):

    def __init__(self, inner, grace_period, clock):
        self._inner = inner
        self._grace_period = datetime.timedelta(seconds=grace_period)
        self._clock = clock
        self._unavailable_hosts = {}

    def is_valid(self, host):
        if self._inner.is_valid(host):
            unavailability_period_start = self._unavailable_hosts.pop(host, None)
            if unavailability_period_start:
                Logger.warn('Additional host %s has become available.' % host, crucial=True)
            return True
        now = self._clock.now()
        if host not in self._unavailable_hosts:
            Logger.warn('Additional host %s has become unavailable.' % host, crucial=True)
            self._unavailable_hosts[host] = now
        unavailability_period_start = self._unavailable_hosts[host]
        if now >= unavailability_period_start + self._grace_period:
            Logger.warn('Additional host %s became unavailable more than %s seconds ago.'
                        % (host, self._grace_period.seconds),
                        crucial=True)
            return False
        return True
