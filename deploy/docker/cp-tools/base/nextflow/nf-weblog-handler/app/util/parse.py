import os
import re
from datetime import datetime
import time

def get_json_attr(event_json, attr_name, default=None):
    return event_json[attr_name] \
        if event_json and attr_name in event_json \
        else default


def parse_timestamp(timestamp):
    if not timestamp:
        return None
    timestamp_sec = timestamp / 1000
    return (datetime.fromtimestamp(timestamp_sec)
            .strftime("%Y-%m-%d %H:%M:%S.000Z"))


def date_to_timestamp(str_date, format_str):
    import time

    def to_mil_seconds(date):
        return time.mktime(date.timetuple()) * 1000

    if str_date is None:
        return None
    return to_mil_seconds(datetime.strptime(str_date, format_str))


def get_array_element_or_default(array, index, default=None):
    if index == -1 or len(array) < index + 1:
        return default
    return array[index]


def get_required_env(env_name):
    env = os.getenv(env_name, None)
    if not env:
        raise RuntimeError("Env Variable: {} should be provided! Exiting!".format(env_name))
    return env


def parse_int_str(value):
    if not value or value == "-":
        return None
    return int(value)


def parse_percentage_str(value):
    if not value or  value == "-":
        return None
    parsed_value = re.search('(\d+(.\d+)) ?%?', value.strip())
    if parsed_value:
        return parsed_value.group(1)


def parse_memory_str(value):
    if not value or  value == "-":
        return None
    parsed_value = re.search('(\d+(.\d+)) ?(GB|MB|KB)?', value.strip())
    if parsed_value:
        multiplier = parsed_value.group(2)
        value = parsed_value.group(1)
        if multiplier == "GB":
            return value * 1024 * 1024 * 1024
        elif multiplier == "MB":
            return value * 1024 * 1024
        elif multiplier == "KB":
            return value * 1024
        return value