# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Covers the local <-> UTC conversions of export_runs.py's dates. Run with `python3 -m unittest test_date_utilities`
# from inside e2e/export, with pytz and tzlocal installed. Needs time.tzset(), so not on Windows.

import os
import time
import unittest

import date_utilities


class DateUtilitiesTest(unittest.TestCase):

    def setUp(self):
        self._tz = os.environ.get('TZ')
        os.environ['TZ'] = 'Europe/Berlin'
        time.tzset()

    def tearDown(self):
        if self._tz is None:
            os.environ.pop('TZ', None)
        else:
            os.environ['TZ'] = self._tz
        time.tzset()

    def test_parses_a_local_date_to_utc(self):
        self.assertEqual('2026-07-01 10:00:00.000000', date_utilities.parse_date_parameter('2026-07-01 12:00:00'))
        self.assertEqual('2026-01-01 11:00:00.000000', date_utilities.parse_date_parameter('2026-01-01 12:00:00'))

    def test_parses_a_local_day_to_utc(self):
        self.assertEqual('2026-06-30 22:00:00.000000', date_utilities.parse_date_parameter('2026-07-01'))

    def test_rejects_an_unknown_format(self):
        with self.assertRaises(RuntimeError):
            date_utilities.parse_date_parameter('01.07.2026')


if __name__ == '__main__':
    unittest.main()
