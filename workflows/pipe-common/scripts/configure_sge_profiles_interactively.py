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

from collections import namedtuple

import logging
import os
import psutil
import re
import shutil
import subprocess
import sys
import tempfile

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger
from scripts.generate_sge_profiles import PROFILE_QUEUE_FORMAT, PROFILE_AUTOSCALING_FORMAT, PROFILE_QUEUE_PATTERN

Profile = namedtuple('Profile', 'name,path_queue,path_autoscaling')


class SGEProfileConfigurationError(RuntimeError):
    pass


def configure_sge_profiles_interactively():
    logging_dir = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOG_DIR', default=os.getenv('LOG_DIR', '/var/log'))
    logging_level = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_LEVEL', default='INFO')
    logging_level_local = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_LEVEL_LOCAL', default='DEBUG')
    logging_format = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_FORMAT', default='%(asctime)s:%(levelname)s: %(message)s')
    logging_task = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_TASK', default='ConfigureSGEProfiles')
    logging_file = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_FILE', default='configure_sge_profiles_interactively.log')

    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']
    parent_run_id = os.getenv('parent_id')
    runs_root = os.getenv('CP_RUNS_ROOT_DIR', default='/runs')
    pipeline_name = os.getenv('PIPELINE_NAME', default='DefaultPipeline')
    run_dir = os.getenv('RUN_DIR', default=os.path.join(runs_root, pipeline_name + '-' + run_id))
    common_repo_dir = os.getenv('COMMON_REPO_DIR', default=os.path.join(run_dir, 'CommonRepo'))
    cap_scripts_dir = os.getenv('CP_CAP_SCRIPTS_DIR', default='/common/cap_scripts')
    autoscaling_script_path = os.path.join(common_repo_dir, 'scripts', 'autoscale_sge.py')
    queue_profile_regexp = re.compile(PROFILE_QUEUE_PATTERN)

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

    try:
        if parent_run_id:
            logger.warning('Grid Engine autoscaling profiles shall be configured from cluster parent run.\n'
                           'Please use the following commands to configure grid engine autoscaling profiles:\n\n'
                           'ssh pipeline-{}\n'
                           'sge_configure'.format(parent_run_id))
            raise SGEProfileConfigurationError()
        logger.debug('Initiating grid engine autoscaling profiles configuration...')
        editor = _find_editor(logger)
        profiles = list(_collect_profiles(cap_scripts_dir, queue_profile_regexp, logger))
        modified_profiles = list(_configure_profiles(profiles, editor, logger))
        _restart_autoscalers(modified_profiles, autoscaling_script_path, logger)
    except SGEProfileConfigurationError:
        exit(1)
    except KeyboardInterrupt:
        logger.warning('Interrupted.')


def _find_editor(logger):
    logger.debug('Searching for editor...')
    default_editor = os.getenv('VISUAL', os.getenv('EDITOR'))
    fallback_editors = ['nano', 'vi', 'vim']
    editors = [default_editor] + fallback_editors if default_editor else fallback_editors
    editor = None
    for potential_editor in editors:
        try:
            subprocess.check_output('command -v ' + potential_editor, shell=True, stderr=subprocess.STDOUT)
            editor = potential_editor
            logger.debug('Editor {} has been found.'.format(editor))
            break
        except subprocess.CalledProcessError as e:
            logger.debug('Editor {} has not been found because {}.'
                         .format(potential_editor, e.output or 'the tool is not installed'))

    if not editor:
        logger.warning('Grid Engine autoscaling profiles configuration requires a text editor to be installed locally.\n'
                       'Please set VISUAL/EDITOR environment variable or install vi/vim/nano using one of the commands below.\n\n'
                       'yum install -y nano\n'
                       'apt-get install -y nano')
        raise SGEProfileConfigurationError()
    return editor


def _collect_profiles(cap_scripts_dir, profile_regexp, logger):
    logger.debug('Collecting existing profiles...')
    for profile_name in os.listdir(cap_scripts_dir):
        profile_match = profile_regexp.match(profile_name)
        if not profile_match:
            continue
        queue_name = profile_match.group(1)
        logger.debug('Profile {} has been collected.'.format(queue_name))
        yield Profile(name=queue_name,
                      path_queue=os.path.join(cap_scripts_dir, PROFILE_QUEUE_FORMAT.format(queue_name)),
                      path_autoscaling=os.path.join(cap_scripts_dir, PROFILE_AUTOSCALING_FORMAT.format(queue_name)))


def _configure_profiles(profiles, editor, logger):
    for profile in profiles:
        tmp_profile_path = tempfile.mktemp()
        logger.debug('Copying grid engine {} autoscaling profile to {}...'.format(profile.name, tmp_profile_path))
        shutil.copy2(profile.path_autoscaling, tmp_profile_path)
        logger.debug('Modifying temporary grid engine {} autoscaling profile...'.format(profile.name))
        subprocess.check_call([editor, tmp_profile_path])
        profile_changes = _compare_profiles(profile.name, profile.path_autoscaling, tmp_profile_path, logger)
        if not profile_changes:
            logger.info('Grid Engine {} autoscaling profile has not been changed.'.format(profile.name))
            continue
        logger.info('Grid Engine {} autoscaling profile has been changed:\n{}'.format(profile.name, profile_changes))
        logger.debug('Persisting grid engine {} autoscaling profile changes...'.format(profile.name))
        shutil.move(tmp_profile_path, profile.path_autoscaling)
        yield profile


def _compare_profiles(profile_name, before_path, after_path, logger):
    logger.debug('Extracting changes from grid engine {} autoscaling profile...'
                 .format(profile_name, after_path))
    try:
        return subprocess.check_output(['diff', before_path, after_path], stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        return e.output


def _restart_autoscalers(profiles, autoscaling_script_path, logger):
    for profile in profiles:
        _stop_autoscaler(profile, autoscaling_script_path, logger)
        _launch_autoscaler(profile, autoscaling_script_path, logger)


def _stop_autoscaler(profile, autoscaling_script_path, logger):
    logger.debug('Searching for {} autoscaler processes...'.format(profile.name))
    for proc in _get_processes(logger, autoscaling_script_path, CP_CAP_SGE_QUEUE_NAME=profile.name):
        logger.debug('Stopping process #{}...'.format(proc.pid))
        proc.terminate()


def _get_processes(logger, *args, **kwargs):
    for proc in psutil.process_iter():
        try:
            proc_cmdline = proc.cmdline()
            proc_environ = proc.environ()
        except KeyboardInterrupt:
            raise
        except Exception:
            logger.error('Please use root account to configure grid engine profiles.')
            raise SGEProfileConfigurationError()
        if all(arg in proc_cmdline for arg in args) \
                and all(proc_environ.get(k) == v for k, v in kwargs.items()):
            yield proc


def _launch_autoscaler(profile, autoscaling_script_path, logger):
    logger.debug('Launching grid engine queue {} autoscaling...'.format(profile.name))
    subprocess.check_call("""
source "{autoscaling_profile_path}"
if check_cp_cap "CP_CAP_AUTOSCALE"; then
    nohup "$CP_PYTHON2_PATH" "{autoscaling_script_path}" >"$LOG_DIR/.nohup.autoscaler.$CP_CAP_SGE_QUEUE_NAME.log" 2>&1 &
fi
    """.format(autoscaling_profile_path=profile.path_queue,
               autoscaling_script_path=autoscaling_script_path),
                          shell=True)
    logger.info('Grid Engine {} autoscaling has been launched.'.format(profile.name))


if __name__ == '__main__':
    configure_sge_profiles_interactively()
