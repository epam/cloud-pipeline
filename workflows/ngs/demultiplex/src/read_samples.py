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


import argparse
from pipeline import SampleSheetParser

SAMPLE_ID = "Sample_ID"
SAMPLE_NAME = "Sample_Name"
SAMPLE_PROJECT = "Sample_Project"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sample-sheet', required=True)
    args = parser.parse_args()
    samples = SampleSheetParser(args.sample_sheet, [SAMPLE_ID, SAMPLE_NAME, SAMPLE_PROJECT]).parse_sample_sheet()
    for sample in samples:
        print(sample[SAMPLE_NAME])


if __name__ == '__main__':
    main()
