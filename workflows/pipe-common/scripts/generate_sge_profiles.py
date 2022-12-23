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

import logging
import os
import sys
import traceback

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger
from pipeline.utils.profile import suffix_non_unique, build_environment_profiles


def generate_sge_profiles():
    logging_dir = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOG_DIR', default=os.getenv('LOG_DIR', '/var/log'))
    logging_level = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_LEVEL', default='INFO')
    logging_level_local = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_LEVEL_LOCAL', default='DEBUG')
    logging_format = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_FORMAT', default='%(asctime)s:%(levelname)s: %(message)s')
    logging_task = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_TASK', default='GenerateSGEProfiles')
    logging_file = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_FILE', default='generate_sge_profiles.log')

    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']

    cap_scripts_dir = os.getenv('CP_CAP_SCRIPTS_DIR', '/common/cap_scripts')
    default_queue_disabled = os.getenv('CP_CAP_SGE_DISABLE_DEFAULT_QUEUE', 'false').lower() == 'true'

    logging_formatter = logging.Formatter(logging_format)

    logging.getLogger().setLevel(logging_level_local)

    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging_level_local)
    console_handler.setFormatter(logging_formatter)
    logging.getLogger().addHandler(console_handler)

    file_handler = logging.FileHandler(os.path.join(logging_dir, logging_file))
    file_handler.setLevel(logging_level_local)
    file_handler.setFormatter(logging_formatter)
    logging.getLogger().addHandler(file_handler)

    api = PipelineAPI(api_url=api_url, log_dir=logging_dir)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level, inner=logger)
    logger = LocalLogger(inner=logger)

    profiles = _generate_profiles(default_queue_disabled, logger)
    params = _get_parameters(logger)
    _write_profiles(profiles, params, cap_scripts_dir, logger)


def _get_parameters(logger):
    try:
        from scripts.autoscale_sge import GridEngineParameters
        return GridEngineParameters().as_dict()
    except KeyboardInterrupt:
        raise
    except Exception:
        logger.warning('Grid engine parameter definitions retrieving has failed.')
        logger.warning(traceback.format_exc())
        return {}


def _generate_profiles(default_queue_disabled, logger):
    common_profile, secondary_profiles = build_environment_profiles(prefixes=['CP_CAP_SGE', 'CP_CAP_AUTOSCALE'],
                                                                    logger=logger)
    enhanced_secondary_profiles = _enhance_secondary_profiles(common_profile, secondary_profiles)
    enhanced_common_profile = _enhance_common_profile(common_profile)
    if default_queue_disabled:
        return enhanced_secondary_profiles
    return _merge_dicts({'common': enhanced_common_profile}, enhanced_secondary_profiles)


def _enhance_common_profile(common_profile):
    common_profile['CP_CAP_SGE_QUEUE_NAME'] = os.getenv('CP_CAP_SGE_QUEUE_NAME', 'main.q')
    common_profile['CP_CAP_SGE_QUEUE_STATIC'] = 'true'
    common_profile['CP_CAP_SGE_QUEUE_DEFAULT'] = 'true'
    common_profile['CP_CAP_AUTOSCALE_TASK'] = 'GridEngineAutoscaling'
    return common_profile


def _enhance_secondary_profiles(common_profile, profiles):
    for profile_index, profile in profiles.items():
        if 'CP_CAP_SGE_QUEUE_NAME' not in profile:
            if 'CP_CAP_AUTOSCALE_INSTANCE_TYPE' in profile:
                profile['CP_CAP_SGE_QUEUE_NAME'] = '{}.q'.format(profile['CP_CAP_AUTOSCALE_INSTANCE_TYPE'])
            elif 'CP_CAP_AUTOSCALE_HYBRID' in profile:
                profile['CP_CAP_SGE_QUEUE_NAME'] = '{}.family.q'.format(
                    profile.get('CP_CAP_AUTOSCALE_HYBRID_FAMILY', os.environ['instance_size']))
            else:
                profile['CP_CAP_SGE_QUEUE_NAME'] = '{}.q'.format(os.environ['instance_size'])
        if 'CP_CAP_AUTOSCALE' not in profile:
            profile['CP_CAP_AUTOSCALE'] = 'false'
    profile_indexes = profiles.keys()
    profile_queues = [profiles[index]['CP_CAP_SGE_QUEUE_NAME'] for index in profile_indexes]
    unique_profile_queues = suffix_non_unique(profile_queues, suffix_func=_append_suffix)
    for profile_index, profile in profiles.items():
        profile['CP_CAP_SGE_QUEUE_NAME'] = unique_profile_queues[profile_indexes.index(profile_index)]
        profile['CP_CAP_SGE_HOSTLIST_NAME'] = '@{}'.format(profile['CP_CAP_SGE_QUEUE_NAME'])
    for profile_index in profiles.keys():
        profile = dict(common_profile)
        profile.update(profiles[profile_index])
        profiles[profile_index] = profile
    return profiles


def _append_suffix(item, suffix):
    return '{}.{}.q'.format(item if not item.endswith('.q') else item[:-2], suffix)


def _merge_dicts(left, right):
    profiles = dict()
    profiles.update(left)
    profiles.update(right)
    return profiles


def _write_profiles(profiles, params, cap_scripts_dir, logger):
    logger.debug('')
    logger.debug('Writing grid engine profile scripts...')
    for profile_index, profile in profiles.items():
        logger.debug('')
        logger.debug('Profile {} environment:'.format(profile_index))
        for param_name in sorted(profile):
            param_value = profile.get(param_name)
            logger.debug('{}={}'.format(param_name, param_value))
        profile_queue_name = profile.get('CP_CAP_SGE_QUEUE_NAME')
        profile_script_name = 'sge_profile_{}.sh'.format(profile_queue_name)
        profile_script_path = os.path.join(cap_scripts_dir, profile_script_name)
        with open(profile_script_path, 'w') as f:
            f.write("""# Grid Engine {} queue profile.
#
# Please use this configuration file to modify
# the corresponding grid engine queue's autoscaling.
#
# Below you can find all the available configuration parameters.
# Change already existing parameters or configure additional ones
# by uncommenting the corresponding lines and setting proper values.

""".format(profile_queue_name))
            for param_name in sorted(params):
                param = params.get(param_name)
                param_help = param.help
                param_value = profile.get(param_name)
                if param_help:
                    f.write('# ' + param_help.replace('\n', '\n# ') + '\n')
                if param_value:
                    f.write('export {}="{}"\n\n'.format(param_name, param_value))
                else:
                    f.write('# export {}=""\n\n'.format(param_name))
            for param_name in sorted(profile):
                if param_name in params:
                    continue
                param_value = profile.get(param_name)
                f.write('export {}="{}"\n'.format(param_name, param_value))


if __name__ == '__main__':
    generate_sge_profiles()
