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

# Covers the reading of the billing export in scripts/send_statistics.py: the API returns the bytes of a CSV file.

import unittest

from mock import Mock

from scripts.send_statistics import _get_runs, _get_usage_costs, _read_content

RUNS_CSV = u'Run,Owner,Cost ($)\r\n1,user,1.5\r\n2,us\xe9r,2.25\r\n'.encode('utf-8')


class ReadContentTest(unittest.TestCase):

    def test_reads_the_rows_of_the_bytes_of_a_csv_file(self):
        rows = _read_content(RUNS_CSV, skip_first_row=False)

        self.assertEqual([{'Run': '1', 'Owner': 'user', 'Cost ($)': '1.5'},
                          {'Run': '2', 'Owner': u'us\xe9r', 'Cost ($)': '2.25'}], rows)

    def test_skips_the_first_row(self):
        rows = _read_content(b'Runs report\r\n' + RUNS_CSV)

        self.assertEqual(['1', '2'], [row['Run'] for row in rows])

    def test_reads_text_as_well(self):
        rows = _read_content(RUNS_CSV.decode('utf-8'), skip_first_row=False)

        self.assertEqual(['user', u'us\xe9r'], [row['Owner'] for row in rows])

    def test_usage_costs_are_the_sum_of_the_run_costs(self):
        api = Mock()
        api.billing_export.return_value = RUNS_CSV

        self.assertEqual(3.75, _get_usage_costs(_get_runs(api, '2026-11-01', '2026-11-11', 'user')))
        api.billing_export.assert_called_once_with('2026-11-01', '2026-11-11', {'owner': ['user']}, ['RUN'])


if __name__ == '__main__':
    unittest.main()
