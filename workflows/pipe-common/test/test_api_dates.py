# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Covers the UTC dates that pipeline/api/api.py sends to the API: the audit events, the task logs and the run
# statuses. The requests are stubs.

import datetime
import re
import shutil
import tempfile
import types
import unittest

from mock import patch

from pipeline.api import api as api_module
from pipeline.api import LogEntry, PipelineAPI, StatusEntry, TaskStatus

NOW = datetime.datetime(2026, 11, 11, 12, 34, 56, 789123)
# The time of a local zone, that is not UTC: a date that is built from it is not the UTC one
LOCAL_NOW = datetime.datetime(2026, 11, 11, 15, 34, 56, 789123)


def _fixed_time(tz):
    if tz is not None and tz.utcoffset(None) == datetime.timedelta(0):
        return NOW.replace(tzinfo=tz)
    return LOCAL_NOW.replace(tzinfo=tz)


class _FixedDatetime(datetime.datetime):

    @classmethod
    def now(cls, tz=None):
        return _fixed_time(tz)

    @classmethod
    def fromtimestamp(cls, timestamp, tz=None):
        return _fixed_time(tz)


class ApiDatesTest(unittest.TestCase):

    def setUp(self):
        log_dir = tempfile.mkdtemp()
        self.addCleanup(shutil.rmtree, log_dir)
        self.api = PipelineAPI(api_url='http://api/restapi', log_dir=log_dir, token=_Token())
        fixed_datetime_module = types.SimpleNamespace(datetime=_FixedDatetime, timezone=datetime.timezone,
                                                      timedelta=datetime.timedelta, tzinfo=datetime.tzinfo)
        patcher = patch.object(api_module, 'datetime', fixed_datetime_module)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_audit_event_has_the_utc_time_with_milliseconds(self):
        with patch.object(PipelineAPI, '_request') as request, patch.dict('os.environ', {'RUN_ID': '5'}):
            self.api.log_audit_event('MOUNT s3://bucket', 'user')

        events = request.call_args[1]['data']
        self.assertEqual('2026-11-11 12:34:56.789', events[0]['messageTimestamp'])
        self.assertEqual('[run_id #5] MOUNT s3://bucket', events[0]['message'])

    def test_task_log_has_the_utc_time_with_milliseconds(self):
        log_entry = LogEntry(5, TaskStatus.RUNNING, 'text', 'Task', 'instance')
        with patch.object(api_module.requests, 'post') as post:
            self.api.log_event(log_entry, omit_console=True)

        self.assertEqual('2026-11-11 12:34:56.789', log_entry.date)
        self.assertTrue(post.called)

    def test_run_status_has_the_utc_time_with_milliseconds(self):
        status_entry = StatusEntry(TaskStatus.SUCCESS)
        with patch.object(api_module.requests, 'post') as post:
            self.api.update_status(5, status_entry)

        self.assertEqual('2026-11-11 12:34:56.789', status_entry.endDate)
        self.assertIn('"endDate": "2026-11-11 12:34:56.789"', post.call_args[1]['data'])

    def test_new_entries_have_the_utc_time(self):
        self.assertTrue(re.match(r'^2026-11-11 12:34:56\.789123$', StatusEntry(TaskStatus.SUCCESS).endDate))
        self.assertTrue(re.match(r'^2026-11-11 12:34:56\.789123$',
                                 LogEntry(5, TaskStatus.RUNNING, 'text', 'Task', 'instance').date))


class _Token(object):

    def get(self):
        return 'token'


if __name__ == '__main__':
    unittest.main()
