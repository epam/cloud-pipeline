# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
import shutil
import subprocess
import tempfile
from collections import namedtuple

import click
import psutil
import sys
import time

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger
from scripts.generate_sge_profiles import generate_sge_profiles, \
    PROFILE_QUEUE_FORMAT, PROFILE_AUTOSCALING_FORMAT, PROFILE_QUEUE_PATTERN

Profile = namedtuple('Profile', 'name,path_queue,path_autoscaling')

PROFILE_NAME_REMOVAL_PATTERN = r'[^a-zA-Z0-9.]+'


class GridEngineProfileError(RuntimeError):
    pass


class ManagementTask:
    CREATE = 'CREATE'
    CONFIGURE = 'CONFIGURE'
    LIST = 'LIST'


def manage(task, profile_name=None):
    logging_dir = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOG_DIR', default=os.getenv('LOG_DIR', '/var/log'))
    logging_level = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_LEVEL', default='INFO')
    logging_level_local = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_LEVEL_LOCAL', default='DEBUG')
    logging_level_console = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_LEVEL_CONSOLE', default='INFO')
    logging_format = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_FORMAT', default='%(asctime)s:%(levelname)s: %(message)s')
    logging_task = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_TASK', default='ConfigureSGEProfiles')
    logging_file = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_FILE', default='configure_sge_profiles_interactively.log')

    api_url = os.environ['API']
    run_id = os.environ['RUN_ID']
    runs_root = os.getenv('CP_RUNS_ROOT_DIR', default='/runs')
    pipeline_name = os.getenv('PIPELINE_NAME', default='DefaultPipeline')
    run_dir = os.getenv('RUN_DIR', default=os.path.join(runs_root, pipeline_name + '-' + run_id))
    common_repo_dir = os.getenv('COMMON_REPO_DIR', default=os.path.join(run_dir, 'CommonRepo'))
    cap_scripts_dir = os.getenv('CP_CAP_SCRIPTS_DIR', default='/common/cap_scripts')
    autoscaling_script_path = os.path.join(common_repo_dir, 'scripts', 'autoscale_sge.py')
    queue_profile_regexp = re.compile(PROFILE_QUEUE_PATTERN)

    logging_formatter = logging.Formatter(logging_format)
    logging_logger = logging.getLogger()
    if not logging_logger.handlers:
        logging_logger.setLevel(logging_level_local)

        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(logging_level_console)
        console_handler.setFormatter(logging_formatter)
        logging_logger.addHandler(console_handler)

        file_handler = logging.FileHandler(os.path.join(logging_dir, logging_file))
        file_handler.setLevel(logging_level_local)
        file_handler.setFormatter(logging_formatter)
        logging_logger.addHandler(file_handler)

    api = PipelineAPI(api_url=api_url, log_dir=logging_dir)
    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level, inner=logger)
    logger = LocalLogger(inner=logger)

    try:
        if task == ManagementTask.CREATE:
            logger.info('Initiating grid engine profiles creation...')
            editor = _find_editor(logger)
            profile_name = _preprocess_profile_name(profile_name, logger)
            profile = _find_profile(cap_scripts_dir, queue_profile_regexp, profile_name, logger)
            if profile:
                logger.warning('Grid engine {} profile already exists.'.format(profile_name))
                raise GridEngineProfileError()
            _generate_profile(profile_name, logger)
            profile = _find_profile(cap_scripts_dir, queue_profile_regexp, profile_name, logger)
            if not profile:
                logger.warning('Grid engine {} profile does not exist.'.format(profile_name))
                raise GridEngineProfileError()
            _configure_profile(profile, editor, logger)
            _create_queue(profile, logger)
            _restart_autoscaler(profile, autoscaling_script_path, logger)
        elif task == ManagementTask.LIST:
            logger.info('Initiating grid engine profiles listing...')
            profiles = _collect_profiles(cap_scripts_dir, queue_profile_regexp, logger)
            for profile in profiles:
                logger.info('Grid engine profile has been found: {}'.format(profile.name))
        else:
            logger.info('Initiating grid engine profiles configuration...')
            editor = _find_editor(logger)
            profile = _find_profile(cap_scripts_dir, queue_profile_regexp, profile_name, logger)
            if not profile:
                logger.warning('Grid engine {} profile does not exist.'.format(profile_name))
                raise GridEngineProfileError()
            modified_profile = _configure_profile(profile, editor, logger)
            if modified_profile:
                _restart_autoscaler(modified_profile, autoscaling_script_path, logger)
    except KeyboardInterrupt:
        logger.warning('Interrupted.')
    except GridEngineProfileError:
        exit(1)


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
        logger.warning('Grid engine profiles configuration requires a text editor to be installed locally.\n'
                       'Please set VISUAL/EDITOR environment variable or install vi/vim/nano using one of the commands below.\n\n'
                       'yum install -y nano\n'
                       'apt-get install -y nano\n\n')
        raise GridEngineProfileError()
    return editor


def _preprocess_profile_name(profile_name, logger):
    logging.debug('Preprocessing profile name...')
    profile_name = re.sub(PROFILE_NAME_REMOVAL_PATTERN, '', profile_name.lower())
    if not profile_name:
        logger.warning('Grid engine profile name should consist only of alphanumeric characters and dots.')
        raise GridEngineProfileError()
    if not profile_name.endswith('.q'):
        profile_name = profile_name + '.q'
    return profile_name


def _find_profile(cap_scripts_dir, queue_profile_regexp, profile_name, logger):
    profiles = _collect_profiles(cap_scripts_dir, queue_profile_regexp, logger)
    for profile in profiles:
        if profile.name == profile_name:
            return profile


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


def _generate_profile(profile_name, logger):
    profile_index = str(int(time.time()))
    logger.debug('Creating grid engine {} profile...'.format(profile_name))
    os.environ['CP_CAP_SGE_QUEUE_NAME_{}'.format(profile_index)] = profile_name
    generate_sge_profiles()
    logger.info('Grid engine {} profile has been created.'.format(profile_name))


def _configure_profile(profile, editor, logger):
    tmp_profile_path = tempfile.mktemp()
    logger.debug('Copying grid engine {} profile to {}...'.format(profile.name, tmp_profile_path))
    shutil.copy2(profile.path_autoscaling, tmp_profile_path)
    logger.debug('Modifying temporary grid engine {} profile...'.format(profile.name))
    subprocess.check_call([editor, tmp_profile_path])
    profile_changes = _compare_profiles(profile.name, profile.path_autoscaling, tmp_profile_path, logger)
    if not profile_changes:
        logger.info('Grid engine {} profile has not been changed.'.format(profile.name))
        return None
    logger.info('Grid engine {} profile has been changed:\n{}'.format(profile.name, profile_changes))
    logger.debug('Persisting grid engine {} profile changes...'.format(profile.name))
    shutil.move(tmp_profile_path, profile.path_autoscaling)
    return profile


def _compare_profiles(profile_name, before_path, after_path, logger):
    logger.debug('Extracting changes from grid engine {} profile...'
                 .format(profile_name, after_path))
    try:
        return subprocess.check_output(['diff', before_path, after_path], stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError as e:
        return e.output


def _create_queue(profile, logger):
    logger.debug('Creating grid engine {} queue...'.format(profile.name))
    subprocess.check_call("""
source "{autoscaling_profile_path}"
sge_setup_queue
    """.format(autoscaling_profile_path=profile.path_queue),
                          shell=True)
    logger.info('Grid engine {} queue has been created.'.format(profile.name))


def _restart_autoscaler(profile, autoscaling_script_path, logger):
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
            raise GridEngineProfileError()
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
    logger.info('Grid engine {} autoscaling has been launched.'.format(profile.name))


@click.group()
def cli():
    """
    Grid engine profiles management utility.

    It allows to interactively manage grid engine profiles (queues).

    Examples:

    I. Create new grid engine profile

        sge create queue.q

    II. Configure an existing grid engine profile

        sge configure queue.q

    III. List all existing grid engine profiles

        sge list

    """
    pass


@cli.command()
@click.argument('name', required=True, type=str)
def create(name):
    """
    Creates profiles.

    It creates a new grid engine profile (queue) and provides a text editor to configure it.

    Depending on the value of CP_CAP_AUTOSCALE parameter (true/false)
    the corresponding queue's autoscaler may be started.

    Examples:

        sge create queue.q

    """
    manage(task=ManagementTask.CREATE, profile_name=name)


@cli.command()
@click.argument('name', required=True, type=str)
def configure(name):
    """
    Configures profiles.

    It provides a text editor to configure an existing grid engine profile (queue).

    Depending on the value of CP_CAP_AUTOSCALE parameter (true/false)
    the corresponding queue's autoscaler may be started/restarted/stopped.

    Examples:

        sge configure queue.q

    """
    manage(task=ManagementTask.CONFIGURE, profile_name=name)


@cli.command(name='list')
def ls():
    """
    Lists profiles.

    It lists all existing grid engine profiles (queues).

    Examples:

        sge list

    """
    manage(task=ManagementTask.LIST)


if __name__ == '__main__':
    cli(sys.argv[1:])