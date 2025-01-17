import os
from datetime import datetime

def get_json_attr(event_json, attr_name, default=None):
    return event_json[attr_name] \
        if event_json and attr_name in event_json \
        else default


def parse_timestamp(timestamp):
    if not timestamp:
        return None
    timestamp_sec = timestamp / 1000
    return (datetime.fromtimestamp(timestamp_sec)
            .strftime("%Y-%m-%dT%H:%M:%SZ"))


def get_array_element_or_default(array, index, default=None):
    if len(array) < index + 1:
        return default
    return array[index]

def get_required_env(env_name):
    env = os.getenv(env_name, None)
    if not env:
        raise RuntimeError("Env Variable: {} should be provided! Exiting!".format(env_name))
    return env
