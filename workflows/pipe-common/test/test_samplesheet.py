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

# Covers the sample sheet parser (pipeline/common/samplesheet.py) that scripts/sample_sheet_batch.py uses.

import os
import shutil
import tempfile
import unittest

from pipeline.common.samplesheet import SampleSheetParser

SAMPLE_SHEET = ('[Header]\r\n'
                'Experiment Name,run-1\r\n'
                '\r\n'
                '[Data]\r\n'
                'Sample_ID,Sample_Name,Sample_Project,Index\r\n'
                'S1,sample-1,project-a,ACGT\r\n'
                'S2,"sample, 2",project-b,TGCA\r\n')


class ParseSampleSheetTest(unittest.TestCase):

    def setUp(self):
        self.tmp_dir = tempfile.mkdtemp()
        self.path = os.path.join(self.tmp_dir, 'SampleSheet.csv')
        with open(self.path, 'wb') as f:
            f.write(SAMPLE_SHEET.encode('utf-8'))

    def tearDown(self):
        shutil.rmtree(self.tmp_dir)

    def test_returns_the_samples_of_the_data_section(self):
        samples = SampleSheetParser(self.path).parse_sample_sheet()

        self.assertEqual([{'Sample_ID': 'S1', 'Sample_Name': 'sample-1', 'Sample_Project': 'project-a'},
                          {'Sample_ID': 'S2', 'Sample_Name': 'sample, 2', 'Sample_Project': 'project-b'}],
                         samples)

    def test_returns_the_requested_columns(self):
        samples = SampleSheetParser(self.path, ['Sample_ID', 'Index']).parse_sample_sheet()

        self.assertEqual([{'Sample_ID': 'S1', 'Index': 'ACGT'}, {'Sample_ID': 'S2', 'Index': 'TGCA'}], samples)


if __name__ == '__main__':
    unittest.main()
