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

import pytz
import datetime
from ..config import Config


def server_date_representation(date_string):
    if not date_string:
        return None
    date = None
    try:
        date = datetime.datetime.strptime(date_string, '%Y-%m-%d %H:%M:%S.%f')
    except ValueError:
        pass
    if date is None:
        try:
            date = datetime.datetime.strptime(date_string, '%Y-%m-%dT%H:%M:%SZ')
        except ValueError:
            pass
    if date is not None:
        date_with_time_zone = pytz.utc.localize(date, is_dst=None)
        return date_with_time_zone.astimezone(Config.instance().timezone()).strftime('%Y-%m-%d %H:%M:%S')
    return None


def parse_date_parameter(date_string):
    # accepts string with date in local timezone and converts it to another string of UTC date
    if not date_string:
        return None
    date = None
    error = '"{}" does not match format "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-dd"'.format(date_string)
    try:
        date = datetime.datetime.strptime(date_string, '%Y-%m-%d %H:%M:%S')
        error = None
    except ValueError:
        try:
            date = datetime.datetime.strptime(date_string, '%Y-%m-%d')
            error = None
        except ValueError:
            pass
    if error:
        raise RuntimeError(error)
    date_with_time_zone = Config.instance().timezone().localize(date, is_dst=None)
    return date_with_time_zone.astimezone(pytz.utc).strftime('%Y-%m-%d %H:%M:%S.%f')
