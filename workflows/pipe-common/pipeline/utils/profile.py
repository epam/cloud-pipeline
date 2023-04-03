# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

_PROFILE_PATTERN = '^({}(_\D+)*)_(\d+)$'
_COMMON_PATTERN = '^({}(_\D+)*)(?!_\d+)$'


def build_environment_profiles(prefixes, logger):
    common_profile, profiles = _build_profiles(prefixes, logger)
    _log_profiles(common_profile, profiles, logger)
    return common_profile, profiles


def _build_profiles(prefixes, logger):
    logger.debug('')
    logger.debug('Building environment profiles...')
    common_profile = {}
    profiles = {}
    for key, value in os.environ.items():
        prefix = None
        for possible_prefix in prefixes:
            if key.startswith(possible_prefix):
                prefix = possible_prefix
                break
        if not prefix:
            continue
        profile_match = re.match(_PROFILE_PATTERN.format(prefix), key)
        if profile_match:
            profile_variable = profile_match.group(1)
            profile_index = profile_match.group(3)
            profile = profiles[profile_index] = profiles.get(profile_index) or {}
            profile[profile_variable] = value
            logger.debug('Found profile {} variable: {}={}'.format(profile_index, profile_variable, value))
            continue
        common_match = re.match(_COMMON_PATTERN.format(prefix), key)
        if common_match:
            if key.endswith('_PARAM_TYPE'):
                continue
            common_variable = common_match.group(1)
            common_profile[common_variable] = value
            logger.debug('Found common variable: {}={}'.format(common_variable, value))
            continue
        logger.debug('Skipped variable: {}={}'.format(key, value))
    return common_profile, profiles


def _log_profiles(common_profile, profiles, logger):
    logger.debug('')
    logger.debug('Printing environment profiles...')
    _log_profile('common', common_profile, logger)
    for profile_index, profile in profiles.items():
        _log_profile(profile_index, profile, logger)


def _log_profile(profile_index, profile, logger):
    logger.debug('')
    logger.debug('Profile {} environment:'.format(profile_index))
    for key in sorted(profile):
        value = profile.get(key)
        logger.debug('{}={}'.format(key, value))


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
