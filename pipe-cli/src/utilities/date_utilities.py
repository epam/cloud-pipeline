# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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


DATE_TIME_PARAMETER_FORMAT = '%Y-%m-%d %H:%M:%S'
DATE_TIME_SERVER_FORMAT = '%Y-%m-%d %H:%M:%S.%f'


def server_date_representation(date_string):
    if not date_string:
        return None
    date = None
    try:
        date = datetime.datetime.strptime(date_string, DATE_TIME_SERVER_FORMAT)
    except ValueError:
        pass
    if date is None:
        try:
            date = datetime.datetime.strptime(date_string, '%Y-%m-%dT%H:%M:%SZ')
        except ValueError:
            pass
    if date is not None:
        date_with_time_zone = pytz.utc.localize(date, is_dst=None)
        return date_with_time_zone.astimezone(Config.instance().timezone()).strftime(DATE_TIME_PARAMETER_FORMAT)
    return None


def parse_date_parameter(date_string):
    # accepts string with date in local timezone and converts it to another string of UTC date
    if not date_string:
        return None
    date = _parse_date(date_string)
    utc_date = to_uts(date)
    return _to_string(utc_date)


def to_local(date):
    return date.astimezone(Config.instance().timezone())


def to_uts(date):
    if not date:
        return None
    date_with_time_zone = Config.instance().timezone().localize(date, is_dst=None)
    return date_with_time_zone.astimezone(pytz.utc)


def parse_parameter_date_to_utc(date_string):
    date = _parse_date(date_string)
    return to_uts(date)


def _parse_date(date_string):
    if not date_string:
        return None
    date = None
    error = '"{}" does not match format "yyyy-MM-dd HH:mm:ss" or "yyyy-MM-dd"'.format(date_string)
    try:
        date = datetime.datetime.strptime(date_string, DATE_TIME_PARAMETER_FORMAT)
        error = None
    except ValueError:
        try:
            date = datetime.datetime.strptime(date_string, '%Y-%m-%d')
            error = None
        except ValueError:
            pass
    if error:
        raise RuntimeError(error)
    return date


def _to_string(date, date_format=DATE_TIME_SERVER_FORMAT):
    if not date:
        return None
    return date.strftime(date_format)


def now_utc():
    return to_uts(datetime.datetime.now())


def minus_day(date):
    return date - datetime.timedelta(days=1)


def format_date(date, date_format=DATE_TIME_PARAMETER_FORMAT):
    return _to_string(date, date_format)
