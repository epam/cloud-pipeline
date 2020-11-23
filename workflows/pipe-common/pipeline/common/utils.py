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

import logging
import os
import re
import subprocess

_ENV_VAR_PLACEHOLDER = '${%s}'
_ENV_VAR_PATTERN = r'\${(\w*|_)}'
_ENV_VAR_NAME_PATTERN_GROUP = 1
_PROFILE_PATTERN = '^({}(_\D+)*)_(\d+)$'
_COMMON_PATTERN = '^({}(_\D+)*)(?!_\d+)$'


def replace_all_system_variables_in_path(path):
    if not path:
        return ''

    try:
        # Try to evaluate any expression in the path. E.g. for the complex: s3://bucket/$(date)/...
        return subprocess.check_output('echo {}'.format(path), shell=True).strip()
    except:
        # If it subprocess fails - try a simplier option with environment variables only
        # Note, that any unset variables won't be substituted with empty value, e.g.:
        # 1. unset a
        #    > os.path.expandvars('$a')
        #    > '$a'
        # 2. export a=aaa
        #    > os.path.expandvars('$a')
        #    > 'aaa'
        return os.path.expandvars(path)


def get_path_without_first_delimiter(path):
    return path[1:] if path.startswith('/') else path


def get_path_with_trailing_delimiter(path):
    return path if path.endswith('/') else path + '/'


def get_path_without_trailing_delimiter(path):
    return path if not path.endswith('/') else path[:-1]


def build_environment_profiles(prefix):
    logging.info('Searching for environment profiles...')
    common_profile = {}
    profiles = {}
    for key, value in os.environ.items():
        if not key.startswith(prefix):
            continue
        profile_match = re.match(_PROFILE_PATTERN.format(prefix), key)
        if profile_match:
            profile_variable = profile_match.group(1)
            profile_index = profile_match.group(3)
            profile = profiles[profile_index] = profiles.get(profile_index) or {}
            profile[profile_variable] = value
            logging.info('Found profile %s variable: %s=%s', profile_index, profile_variable, value)
            continue
        common_match = re.match(_COMMON_PATTERN.format(prefix), key)
        if common_match:
            common_variable = common_match.group(1)
            common_profile[common_variable] = value
            logging.info('Found common variable: %s=%s', common_variable, value)
            continue
        logging.warn('Skipped variable: %s=%s', key, value)

    logging.info('Building environment profiles...')
    logging.info('Profile common environment:')
    for key, value in common_profile.items():
        logging.info('%s=%s', key, value)
    complete_profiles = {}
    for profile_index, profile in profiles.items():
        complete_profile = complete_profiles[profile_index] = complete_profiles.get(profile_index) or {}
        complete_profile.update(common_profile)
        complete_profile.update(profile)
        complete_profile['{}_PROFILE'.format(prefix)] = profile_index
        logging.info('Profile %s environment:', profile_index)
        for key, value in complete_profile.items():
            logging.info('%s=%s', key, value)

    return common_profile, complete_profiles


def suffix_non_unique(items, suffix_func=lambda item, suffix: item + suffix):
    from collections import Counter
    from itertools import tee, count
    unique_items = list(items)
    not_unique_items = [item for item, occurrences in Counter(items).items() if occurrences > 1]
    item_suffix_generators = dict(zip(not_unique_items, tee(count(1), len(not_unique_items))))
    for index, item in enumerate(items):
        if item in item_suffix_generators:
            suffix = str(next(item_suffix_generators[item]))
            unique_items[index] = suffix_func(unique_items[index], suffix)
    return unique_items
