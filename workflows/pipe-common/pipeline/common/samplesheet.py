# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import csv


class SampleSheetParser:

    _SAMPLE_ID = "Sample_ID"
    _SAMPLE_NAME = "Sample_Name"
    _SAMPLE_PROJECT = "Sample_Project"


    def __init__(self, path, columns=None):
        self.path = path
        if columns:
            self.columns = columns
        else:
            self.columns = [self._SAMPLE_ID, self._SAMPLE_NAME, self._SAMPLE_PROJECT]

    def parse_sample_sheet(self):
        samples = []
        columns_to_idx = {}
        with open(self.path, 'rb') as f:
            reader = csv.reader(f)
            data_section_started = False
            read_header = False
            for row in reader:
                if len(row) == 0:
                    continue
                if read_header:
                    for idx, field in enumerate(row):
                        for column in self.columns:
                            if field == column:
                                columns_to_idx[column] = idx
                    if not (len(columns_to_idx) == len(self.columns)):
                        raise RuntimeError("Malformed sample sheet: {}.".format(self.path))
                    data_section_started = True
                    read_header = False
                    continue

                if data_section_started:
                    try:
                        sample = {}
                        for column, idx in columns_to_idx.items():
                            sample[column] = row[idx]
                        samples.append(sample)
                    except Exception as e:
                        print("Malformed sample sheet: {}. {}.".format(self.path, e.message))
                        continue

                if row[0] == "[Data]":
                    read_header = True
                    continue
        return samples

    def print_samples(self, print_delimiter=' ', print_headers=True, quote_result=False):
        samples = self.parse_sample_sheet()
        sample_text = print_delimiter.join(self.columns) + '\n' if print_headers else ''
        for sample in samples:
            sample_text += self.format_sample_line(sample, print_delimiter, quote_result=quote_result) + '\n'
        
        print(sample_text)

    def format_sample_line(self, sample, delimiter, quote_result=False):
        sample_text = ''
        for column in self.columns:
            column_value = ''
            if column in sample:
                column_value = sample[column]
            if quote_result:
                sample_text += '"{}"'.format(sample[column]) + delimiter
            else:
                sample_text += sample[column] + delimiter
        return sample_text
