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


class GridEngineEvent(object):
    pass


class InstanceEvent(GridEngineEvent):

    def __init__(self, instance_type, date):
        self._instance_type = instance_type
        self._date = date

    @property
    def instance_type(self):
        return self._instance_type

    @property
    def date(self):
        return self._date

    def __eq__(self, other):
        return type(self) == type(other) and self.__dict__ == other.__dict__

    def __repr__(self):
        return str(self.__dict__)


class InsufficientInstanceEvent(InstanceEvent):

    def __init__(self, instance_type, date):
        super(InsufficientInstanceEvent, self).__init__(instance_type, date)


class FailingInstanceEvent(InstanceEvent):

    def __init__(self, instance_type, date):
        super(FailingInstanceEvent, self).__init__(instance_type, date)


class AvailableInstanceEvent(InstanceEvent):

    def __init__(self, instance_type, date):
        super(AvailableInstanceEvent, self).__init__(instance_type, date)


class GridEngineEventManager:

    def __init__(self, ttl, clock):
        self._ttl = timedelta(seconds=ttl)
        self._clock = clock
        self._events = []

    def register(self, event):
        self._events.append(event)

    def get(self):
        now = self._clock.now()
        for event in list(self._events):
            if event.date < now - self._ttl:
                self._events.remove(event)
                continue
        return list(self._events)
