# Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

import re


def parse_date(date_str):
    if date_str and not (re.match("\\d\\d\\d\\d-\\d\\d-\\d\\d \\d\\d:\\d\\d:\\d\\d\\.\\d\\d\\d", date_str) or
                         re.match("\\d\\d\\d\\d-\\d\\d-\\d\\d", date_str)):
        raise AttributeError("Date value is not in format yyyy-mm-dd or 'yyyy-mm-dd HH:mm:ss.SSS'")
    return date_str
