# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
import datetime

import pytz

ISO_DATE_TIME_FORMAT = "%Y-%m-%d %H:%M:%S.%f"
ISO_DATE_FORMAT = "%Y-%m-%d"


def is_date_after_that(date, to_check):
    return date <= to_check


def is_date_before_that(date, to_check):
    return date > to_check


def parse_timestamp(timestamp_string):
    return pytz.utc.localize(datetime.datetime.strptime(timestamp_string, ISO_DATE_TIME_FORMAT))


def parse_date(date_string):
    return datetime.datetime.strptime(date_string, ISO_DATE_FORMAT).date()


def current_date_string():
    return datetime.datetime.now().date().strftime(ISO_DATE_FORMAT)


def is_date_before_now(date):
    return date <= datetime.datetime.now().date()


def str_date(date):
    return date.strftime(ISO_DATE_FORMAT)
