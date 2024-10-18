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

from pipeline.api import PipelineAPI
from pipeline.hpc.param import GridEngineParameters
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger, ResilientLogger
from pipeline.utils.profile import suffix_non_unique, build_environment_profiles

PROFILE_QUEUE_FORMAT = 'sge_profile_{}_queue.sh'
PROFILE_QUEUE_PATTERN = '^sge_profile_(.+)_queue\\.sh$'
PROFILE_AUTOSCALING_FORMAT = 'sge_profile_{}_autoscaling.sh'
PROFILE_AUTOSCALING_PATTERN = '^sge_profile_(.+)_autoscaling\\.sh$'


def generate_sge_profiles():
    logging_dir = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOG_DIR', default=os.getenv('LOG_DIR', '/var/log'))
    logging_level_run = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_LEVEL_RUN', default='INFO')
    logging_level_file = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_LEVEL_FILE', default='DEBUG')
    logging_level_console = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_LEVEL_CONSOLE', default='INFO')
    logging_format = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_FORMAT', default='%(asctime)s:%(levelname)s: %(message)s')
    logging_task = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_TASK', default='GenerateSGEProfiles')
    logging_file = os.getenv('CP_CAP_SGE_PROFILE_GENERATION_LOGGING_FILE', default='generate_sge_profiles.log')

    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']

    cap_scripts_dir = os.getenv('CP_CAP_SCRIPTS_DIR', '/common/cap_scripts')
    default_queue_disabled = os.getenv('CP_CAP_SGE_DISABLE_DEFAULT_QUEUE', 'false').lower() == 'true'

    logging_formatter = logging.Formatter(logging_format)

    logging_logger_root = logging.getLogger()
    logging_logger_root.setLevel(logging.WARNING)

    logging_logger = logging.getLogger(name=logging_task)
    logging_logger.setLevel(logging_level_file)

    if not logging_logger.handlers:
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(logging_level_console)
        console_handler.setFormatter(logging_formatter)
        logging_logger.addHandler(console_handler)

        file_handler = logging.FileHandler(os.path.join(logging_dir, logging_file))
        file_handler.setLevel(logging_level_file)
        file_handler.setFormatter(logging_formatter)
        logging_logger.addHandler(file_handler)

    api = PipelineAPI(api_url=api_url, log_dir=logging_dir)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level_run, inner=logger)
    logger = LocalLogger(logger=logging_logger, inner=logger)
    logger = ResilientLogger(inner=logger, fallback=LocalLogger(logger=logging_logger))

    params = GridEngineParameters()
    profiles = _generate_profiles(default_queue_disabled, logger)
    _write_profiles(params, profiles, cap_scripts_dir, logger)


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
    common_profile['CP_CAP_SGE_HOSTLIST_NAME'] = os.getenv('CP_CAP_SGE_HOSTLIST_NAME', '@allhosts')
    common_profile['CP_CAP_SGE_QUEUE_STATIC'] = 'true'
    common_profile['CP_CAP_SGE_QUEUE_DEFAULT'] = 'true'
    common_profile['CP_CAP_AUTOSCALE_TASK'] = 'SGEAutoscaling'
    common_profile['CP_CAP_SLURM'] = 'false'
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
        profile['CP_CAP_SLURM'] = 'false'
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


def _write_profiles(params, profiles, cap_scripts_dir, logger):
    logger.debug('')
    logger.debug('Writing grid engine profile scripts...')
    for profile_index, profile in profiles.items():
        logger.debug('')
        logger.debug('Profile {} environment:'.format(profile_index))
        for param_name in sorted(profile):
            param_value = profile.get(param_name)
            logger.debug('{}={}'.format(param_name, param_value))
        profile_queue_name = profile.get('CP_CAP_SGE_QUEUE_NAME')
        queue_profile_name = PROFILE_QUEUE_FORMAT.format(profile_queue_name)
        queue_profile_path = os.path.join(cap_scripts_dir, queue_profile_name)
        autoscaling_profile_name = PROFILE_AUTOSCALING_FORMAT.format(profile_queue_name)
        autoscaling_profile_path = os.path.join(cap_scripts_dir, autoscaling_profile_name)
        _write_queue_profile(profile, profile_queue_name, params,
                             queue_profile_path, autoscaling_profile_path, logger)
        _write_autoscaling_profile(profile, profile_queue_name, params,
                                   queue_profile_path, autoscaling_profile_path, logger)


def _write_autoscaling_profile(profile, profile_queue_name, params,
                               queue_profile_path, autoscaling_profile_path, logger):
    if os.path.exists(autoscaling_profile_path):
        logger.debug('Skipping grid engine profile {queue_name} writing '
                     'because it is already written...'.format(queue_name=profile_queue_name))
        return
    with open(autoscaling_profile_path, 'w') as f:
        f.write("""# Grid engine {queue_name} profile.
#
# Please use this configuration file to dynamically modify
# the corresponding grid engine queue's autoscaling.
#
# The autoscaling process restarts only if some configuration parameters change.
# Otherwise autoscaling proceeds the same.
#
# Notice that all existing additional workers are not affected
# and not yet initialized additional workers may be stopped.
#
# Below there is a list of all the available configuration parameters.
# You can change already configured parameter values or
# uncomment additional ones and set their values.
#
#
# See also
# {queue_profile_path}
# {autoscaling_profile_path}

""".format(queue_name=profile_queue_name,
           queue_profile_path=queue_profile_path,
           autoscaling_profile_path=autoscaling_profile_path))
        for param in params.autoscaling.as_list():
            param_value = profile.get(param.name)
            if param.help:
                f.write('# ' + param.help.replace('\n', '\n# ') + '\n')
            if param_value:
                f.write('export {}="{}"\n\n'.format(param.name, param_value))
            else:
                f.write('# export {}=""\n\n'.format(param.name))


def _write_queue_profile(profile, profile_queue_name, params,
                         queue_profile_path, autoscaling_profile_path, logger):
    if os.path.exists(queue_profile_path):
        logger.debug('Skipping grid engine queue profile {queue_name} writing '
                     'because it is already written...'.format(queue_name=profile_queue_name))
        return
    with open(queue_profile_path, 'w') as f:
        f.write("""# Grid engine {queue_name} queue profile.
#
# This configuration file contains grid engine queue configuration.
# It cannot be used to dynamically modify grid engine queue.
#
# In order to dynamically modify grid engine queue's autoscaling
# use the following command:
#
#     sge configure
#
#
# See also
# {queue_profile_path}
# {autoscaling_profile_path}

""".format(queue_name=profile_queue_name,
           queue_profile_path=queue_profile_path,
           autoscaling_profile_path=autoscaling_profile_path))
        for param in params.queue.as_list():
            param_value = profile.get(param.name)
            if param.help:
                f.write('# ' + param.help.replace('\n', '\n# ') + '\n')
            if param_value:
                f.write('export {}="{}"\n\n'.format(param.name, param_value))
            else:
                f.write('# export {}=""\n\n'.format(param.name))
        queue_params = params.queue.as_dict()
        autoscaling_params = params.autoscaling.as_dict()
        for param_name in sorted(profile):
            if param_name in queue_params or param_name in autoscaling_params:
                continue
            param_value = profile.get(param_name)
            f.write('export {}="{}"\n'.format(param_name, param_value))
        f.write("""
. {autoscaling_profile_path}
""".format(autoscaling_profile_path=autoscaling_profile_path))


if __name__ == '__main__':
    generate_sge_profiles()
