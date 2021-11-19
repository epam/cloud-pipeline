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

import datetime as datetime

DATE_FORMAT = "%Y-%m-%d"
DATE_TIME_FORMAT = "%Y-%m-%d %H:%M:%S.%f"


def validate_date(date_str):
    if date_str is None:
        return date_str
    try:
        datetime.datetime.strptime(date_str, DATE_FORMAT)
    except ValueError:
        try:
            datetime.datetime.strptime(date_str, DATE_TIME_FORMAT)
        except ValueError:
            raise AttributeError("Date value is not in format yyyy-mm-dd or 'yyyy-mm-dd HH:MM:ss.SSS'")
    return date_str
