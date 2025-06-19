# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

import os
import re
from datetime import datetime
import time

def get_json_attr(event_json, attr_name, default=None):
    return event_json[attr_name] \
        if event_json and attr_name in event_json \
        else default


def parse_timestamp(timestamp):
    if not timestamp or timestamp == "-":
        return None
    timestamp_sec = timestamp / 1000
    return (datetime.fromtimestamp(timestamp_sec)
            .strftime("%Y-%m-%d %H:%M:%S.000Z"))

def parse_duration_field_from_trace_file(duration_str):
    if not duration_str or duration_str == "-":
        return None
    if duration_str.isdigit():
        return int(duration_str)
    else:
        return duration_str_to_int(duration_str)

def parse_time_field_from_trace_file(datetime_str):
    if not datetime_str or datetime_str == "-":
        return None
    if datetime_str.isdigit():
        return int(datetime_str)
    else:
        return date_to_timestamp(datetime_str, "%Y-%m-%d %H:%M:%S.%f")

def date_to_timestamp(datetime_str, format_str):
    import time

    def to_mil_seconds(date):
        return time.mktime(date.timetuple()) * 1000

    if datetime_str is None:
        return None
    return to_mil_seconds(datetime.strptime(datetime_str, format_str))


def get_array_element_or_default(array, index, default=None):
    if index == -1 or len(array) < index + 1:
        return default
    return array[index]


def get_required_env(env_name, default_value=None):
    env = os.getenv(env_name, None)
    if not env:
        if default_value:
            return default_value
        raise RuntimeError("Env Variable: {} should be provided! Exiting!".format(env_name))
    return env


def parse_int_str(value):
    if not value or value == "-":
        return None
    return int(value)


def parse_percentage_str(value):
    if not value or  value == "-":
        return None
    parsed_value = re.search('(\d+(.\d+)?) ?%?', value.strip())
    if parsed_value:
        return parsed_value.group(1)


def duration_str_to_int(duration_str):
    total_ms = 0
    pattern = r'(\d+(\.\d+)?)([dhms])'  # Allow optional decimals in the numbers
    matches = re.findall(pattern, duration_str)

    for value, _, unit in matches:
        value = float(value)  # Convert the value to float to handle decimal numbers

        if unit == 'd':
            total_ms += value * 24 * 60 * 60 * 1000  # days to ms
        elif unit == 'h':
            total_ms += value * 60 * 60 * 1000  # hours to ms
        elif unit == 'm':
            total_ms += value * 60 * 1000  # minutes to ms
        elif unit == 's':
            total_ms += value * 1000  # seconds to ms
        elif unit == 'ms':
            total_ms += value  # milliseconds already in ms

    return int(total_ms)  # Return the result as an integer


def parse_memory_str(value):
    if not value or  value == "-":
        return None
    parsed_value = re.search('(\d+(.\d+)?) ?(GB|MB|KB)?', value.strip())
    if parsed_value:
        multiplier = parsed_value.group(3)
        value = float(parsed_value.group(1))
        result = value
        if multiplier == "GB":
            result = value * 1024 * 1024 * 1024
        elif multiplier == "MB":
            result = value * 1024 * 1024
        elif multiplier == "KB":
            result = value * 1024
        return int(result)