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
import re
import subprocess
import sys

import psutil

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger


class SGEProfileConfigurationError(RuntimeError):
    pass


class Queue:

    def __init__(self, name, profile_path):
        self._name = name
        self._profile_path = profile_path

    @property
    def name(self):
        return self._name

    @property
    def profile_path(self):
        return self._profile_path


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
    sge_autoscale_script_path = os.path.join(common_repo_dir, 'scripts', 'autoscale_sge.py')
    sge_profile_script_name_pattern = '^sge_profile_(.+)\\.sh$'
    sge_profile_script_regexp = re.compile(sge_profile_script_name_pattern)

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
            logger.error('Please use the following node to configure grid engine profiles: '
                         'pipeline-{}.'.format(parent_run_id))
            raise SGEProfileConfigurationError()

        logger.debug('Initiating grid engine profiles configuration...')
        editor = _select_editor(logger)
        queues = list(_collect_queues(cap_scripts_dir, sge_profile_script_regexp, logger))
        _reconfigure_queues(queues, editor, logger)
        _restart_autoscalers(queues, sge_autoscale_script_path, logger)
    except SGEProfileConfigurationError:
        exit(1)
    except KeyboardInterrupt:
        logger.warning('Interrupted.')


def _select_editor(logger):
    logger.debug('Selecting editor...')
    default_editor = os.getenv('VISUAL', os.getenv('EDITOR'))
    fallback_editors = ['nano', 'vi', 'vim']
    editors = [default_editor] + fallback_editors if default_editor else fallback_editors
    editor = None
    for potential_editor in editors:
        exit_code = subprocess.call('command -v ' + potential_editor, shell=True)
        if not exit_code:
            editor = potential_editor
            break
    if not editor:
        logger.error('No available text editor has been found. '
                     'Please install vi/vim/nano or set VISUAL/EDITOR environment variable. '
                     'Exiting...')
        raise SGEProfileConfigurationError()
    logger.debug('Editor {} has been selected.'.format(editor))
    return editor


def _collect_queues(cap_scripts_dir, sge_profile_script_regexp, logger):
    logger.debug('Collecting existing queues...')
    for sge_profile_script_name in os.listdir(cap_scripts_dir):
        sge_profile_script_match = sge_profile_script_regexp.match(sge_profile_script_name)
        if not sge_profile_script_match:
            continue
        queue_name = sge_profile_script_match.group(1)
        sge_profile_script_path = os.path.join(cap_scripts_dir, sge_profile_script_name)
        yield Queue(name=queue_name, profile_path=sge_profile_script_path)
        logger.debug('Queue {} has been collected.'.format(queue_name))


def _reconfigure_queues(queues, editor, logger):
    for queue in queues:
        logger.debug('Reconfiguring queue {} by editing {}...'.format(queue.name, queue.profile_path))
        subprocess.check_call([editor, queue.profile_path])
        logger.info('Queue {} has been reconfigured.'.format(queue.name))


def _restart_autoscalers(queues, sge_autoscale_script_path, logger):
    for queue in queues:
        _stop_autoscaler(queue, sge_autoscale_script_path, logger)
        _launch_autoscaler(queue, sge_autoscale_script_path, logger)


def _stop_autoscaler(queue, sge_autoscale_script_path, logger):
    logger.debug('Searching for {} autoscaler processes...'.format(queue.name))
    for proc in _get_processes(logger, sge_autoscale_script_path, CP_CAP_SGE_QUEUE_NAME=queue.name):
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


def _launch_autoscaler(queue, sge_autoscale_script_path, logger):
    logger.debug('Launching queue {} sge autoscaler process...'.format(queue.name))
    subprocess.check_call("""
source "{sge_profile_script_path}"
if check_cp_cap "CP_CAP_AUTOSCALE"; then
    nohup "$CP_PYTHON2_PATH" "{sge_autoscale_script_path}" >"$LOG_DIR/.nohup.autoscaler.$CP_CAP_SGE_QUEUE_NAME.log" 2>&1 &
fi
    """.format(sge_profile_script_path=queue.profile_path,
               sge_autoscale_script_path=sge_autoscale_script_path),
                          shell=True)
    logger.info('Queue {} sge autoscaler process has been launched.'.format(queue.name))


if __name__ == '__main__':
    configure_sge_profiles_interactively()
