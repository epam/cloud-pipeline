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

from datetime import timedelta

import math

from pipeline.hpc.event import InstanceEvent, AvailableInstanceEvent, FailingInstanceEvent, InsufficientInstanceEvent
from pipeline.hpc.logger import Logger


class InstanceAvailabilityManager:

    def __init__(self, event_manager, clock, unavail_delay,
                 unavail_count_insufficient, unavail_count_failure):
        self._event_manager = event_manager
        self._clock = clock
        self._unavailability_delay = timedelta(seconds=unavail_delay)
        self._unavailability_count_insufficient = unavail_count_insufficient
        self._unavailability_count_failure = unavail_count_failure

    def get_unavailable(self):
        instance_type_counts = {}
        for event in list(self._event_manager.get()):
            if not isinstance(event, InstanceEvent):
                continue
            insufficient_count, failure_count, _ = instance_type_counts.get(event.instance_type) \
                                                   or (0, 0, None)
            if isinstance(event, AvailableInstanceEvent):
                failure_count = 0
                insufficient_count = 0
            if isinstance(event, FailingInstanceEvent):
                failure_count += 1
            if isinstance(event, InsufficientInstanceEvent):
                insufficient_count += 1
            instance_type_counts[event.instance_type] = insufficient_count, failure_count, event
        now = self._clock.now()
        for instance_type, (insufficient_count, failure_count, last_event) in instance_type_counts.items():
            if insufficient_count < self._unavailability_count_insufficient \
                    and failure_count < self._unavailability_count_failure:
                continue
            availability_date = last_event.date + self._unavailability_delay
            if availability_date < now:
                continue
            Logger.warn('Circuit breaking %s instance type because it is unavailable %s more minutes...'
                        % (instance_type, int(math.ceil((availability_date - now).total_seconds() / 60))))
            yield instance_type
