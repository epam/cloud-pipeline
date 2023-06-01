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

from datetime import datetime, timedelta

from mock import Mock, MagicMock

from scripts.autoscale_sge import GridEngineEventManager, AvailableInstanceEvent, InsufficientInstanceEvent, \
    FailingInstanceEvent

ttl = timedelta(minutes=30)
instance_type = 'm5.large'
date = datetime(2018, 12, 21, 11, 00, 00)

clock = Mock()
event_manager = GridEngineEventManager(ttl=ttl.total_seconds(), clock=clock)


def setup_function():
    clock.now = MagicMock(return_value=date)


def test_get_returns_only_recent_events():
    outdated_events = [FailingInstanceEvent(instance_type=instance_type, date=date - 3 * ttl),
                       AvailableInstanceEvent(instance_type=instance_type, date=date - 2 * ttl)]
    recent_events = [InsufficientInstanceEvent(instance_type=instance_type, date=date - ttl / 2),
                     AvailableInstanceEvent(instance_type=instance_type, date=date)]
    for event in recent_events + outdated_events:
        event_manager.register(event)

    events = event_manager.get()

    assert len(events) == 2
    for event in recent_events:
        assert event in events
