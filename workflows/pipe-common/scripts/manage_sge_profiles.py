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

import functools
import logging
import os
import re
import shutil
import subprocess
import tarfile
import tempfile
from collections import namedtuple

import click
import psutil
import sys
import time

from pipeline.api import PipelineAPI
from pipeline.log.logger import LocalLogger, RunLogger, TaskLogger, LevelLogger, ExplicitLogger, PrintLogger
from pipeline.utils.path import mkdir
from pipeline.utils.ssh import LocalExecutor, LoggingExecutor, ExecutorError
from scripts.generate_sge_profiles import generate_sge_profiles, \
    PROFILE_QUEUE_FORMAT, PROFILE_AUTOSCALING_FORMAT, PROFILE_QUEUE_PATTERN

Profile = namedtuple('Profile', 'name,path_queue,path_autoscaling')

PROFILE_NAME_REMOVAL_PATTERN = r'[^a-zA-Z0-9.]+'


class GridEngineProfileError(RuntimeError):
    pass


class ResilientGridEngineProfileManager:

    def __init__(self, inner, logger):
        self._inner = inner
        self._logger = logger

    def __getattr__(self, name):
        if not hasattr(self._inner, name):
            return None
        attr = getattr(self._inner, name)
        if not callable(attr):
            return attr
        return self._wrap(attr)

    def _wrap(self, attr):
        @functools.wraps(attr)
        def _wrapped_attr(*args, **kwargs):
            try:
                return attr(*args, **kwargs)
            except KeyboardInterrupt:
                self._logger.warning('Interrupted.')
            except GridEngineProfileError:
                exit(1)
            except Exception:
                self._logger.warning('Grid engine profiles management has failed.', trace=True)
                exit(1)
        return _wrapped_attr


class GridEngineProfileManager:

    def __init__(self, executor, logger, logger_warning, cap_scripts_dir, queue_profile_regexp,
                 autoscaling_script_path):
        self._executor = executor
        self._logger = logger
        self._logger_warning = logger_warning
        self._cap_scripts_dir = cap_scripts_dir
        self._queue_profile_regexp = queue_profile_regexp
        self._autoscaling_script_path = autoscaling_script_path

    def create(self, profile_name):
        self._logger.info('Initiating grid engine profiles creation...')
        editor = self._find_editor()
        profile_name = self._preprocess_profile_name(profile_name)
        profile = self._find_profile(self._cap_scripts_dir, self._queue_profile_regexp, profile_name)
        if profile:
            self._logger.warning('Grid engine {} profile already exists.'.format(profile_name))
            raise GridEngineProfileError()
        self._generate_profile(profile_name)
        profile = self._find_profile(self._cap_scripts_dir, self._queue_profile_regexp, profile_name)
        if not profile:
            self._logger.warning('Grid engine {} profile does not exist.'.format(profile_name))
            raise GridEngineProfileError()
        self._create_queue(profile)
        modified_profile = self._configure_profile(profile, editor)
        verified = True
        if modified_profile:
            verified = self._verify_profile(modified_profile, self._autoscaling_script_path)
            if verified:
                self._persist_profile(modified_profile, profile)
        self._launch_autoscaler(profile, self._autoscaling_script_path)
        if not verified:
            raise GridEngineProfileError()

    def configure(self, profile_name):
        self._logger.info('Initiating grid engine profile configuration...')
        editor = self._find_editor()
        profile = self._find_profile(self._cap_scripts_dir, self._queue_profile_regexp, profile_name)
        if not profile:
            self._logger.warning('Grid engine {} profile does not exist.'.format(profile_name))
            raise GridEngineProfileError()
        modified_profile = self._configure_profile(profile, editor)
        verified = True
        if modified_profile:
            self._stop_autoscaler(profile, self._autoscaling_script_path)
            verified = self._verify_profile(modified_profile, self._autoscaling_script_path)
            if verified:
                self._persist_profile(modified_profile, profile)
            self._launch_autoscaler(profile, self._autoscaling_script_path)
        if not verified:
            raise GridEngineProfileError()

    def restart(self, profile_name):
        self._logger.info('Initiating grid engine profile restart...')
        profile = self._find_profile(self._cap_scripts_dir, self._queue_profile_regexp, profile_name)
        if not profile:
            self._logger.warning('Grid engine {} profile does not exist.'.format(profile_name))
            raise GridEngineProfileError()
        self._stop_autoscaler(profile, self._autoscaling_script_path)
        self._launch_autoscaler(profile, self._autoscaling_script_path)

    def list(self):
        self._logger.info('Initiating grid engine profiles listing...')
        profiles = self._collect_profiles(self._cap_scripts_dir, self._queue_profile_regexp)
        for profile in profiles:
            self._logger.info('Grid engine profile has been found: {}'.format(profile.name))

    def export(self, profile_names, output_path):
        self._logger.info('Initiating grid engine profiles export...')
        if profile_names:
            profiles = list(self._get_profiles(profile_names))
        else:
            profiles = list(self._collect_profiles(self._cap_scripts_dir, self._queue_profile_regexp))
        tmp_dir = tempfile.mkdtemp()
        self._export(profiles, tmp_dir)
        self._archive(tmp_dir, output_path)

    def _export(self, profiles, export_dir):
        self._logger.info('Exporting to {}...'.format(export_dir))
        self._export_queues(export_dir)
        self._export_pes(export_dir)
        self._export_profiles(profiles, export_dir)

    def _export_queues(self, export_dir):
        self._logger.info('Exporting grid engine queues...')
        output_dir = os.path.join(export_dir, 'cqueues')
        mkdir(output_dir)
        self._executor.execute("""
for name in $(qconf -sql); do 
    qconf -sq "$name" > "{output_dir}/$name"
done
        """.format(output_dir=output_dir))

    def _export_pes(self, export_dir):
        self._logger.info('Exporting grid engine pes...')
        output_dir = os.path.join(export_dir, 'pe')
        mkdir(output_dir)
        self._executor.execute("""
for name in $(qconf -spl); do 
    qconf -sp "$name" > "{output_dir}/$name"
done
        """.format(output_dir=output_dir))

    def _export_profiles(self, profiles, export_dir):
        output_dir = os.path.join(export_dir, 'profiles')
        mkdir(output_dir)
        for profile in profiles:
            self._logger.info('Exporting grid engine {} profile...'.format(profile.name))
            path_queue = os.path.join(output_dir, os.path.basename(profile.path_queue))
            path_autoscaling = os.path.join(output_dir, os.path.basename(profile.path_autoscaling))
            shutil.copyfile(profile.path_queue, path_queue)
            shutil.copyfile(profile.path_autoscaling, path_autoscaling)

    def _archive(self, export_dir, output_path):
        self._logger.info('Archiving...')
        with tarfile.open(output_path, 'w:gz') as tar:
            for item in os.listdir(export_dir):
                item_path = os.path.join(export_dir, item)
                tar.add(item_path, arcname=os.path.basename(item_path))

    def _find_editor(self):
        self._logger.debug('Searching for editor...')
        default_editor = os.getenv('VISUAL', os.getenv('EDITOR'))
        fallback_editors = ['nano', 'vi', 'vim']
        editors = [default_editor] + fallback_editors if default_editor else fallback_editors
        editor = None
        for potential_editor in editors:
            try:
                subprocess.check_output('command -v ' + potential_editor, shell=True, stderr=subprocess.STDOUT)
                editor = potential_editor
                self._logger.debug('Editor {} has been found.'.format(editor))
                break
            except subprocess.CalledProcessError as e:
                self._logger.debug('Editor {} has not been found because {}.'
                                   .format(potential_editor, e.output or 'the tool is not installed'))
        if not editor:
            self._logger.warning('Grid engine profiles configuration requires a text editor to be installed locally.\n'
                                 'Please set VISUAL/EDITOR environment variable or install vi/vim/nano using one of the commands below.\n\n'
                                 'yum install -y nano\n'
                                 'apt-get install -y nano\n\n')
            raise GridEngineProfileError()
        return editor

    def _preprocess_profile_name(self, profile_name):
        self._logger.debug('Preprocessing profile name...')
        profile_name = re.sub(PROFILE_NAME_REMOVAL_PATTERN, '', profile_name.lower())
        if not profile_name:
            self._logger.warning('Grid engine profile name should consist only of alphanumeric characters and dots.')
            raise GridEngineProfileError()
        if not profile_name.endswith('.q'):
            profile_name = profile_name + '.q'
        return profile_name

    def _find_profile(self, cap_scripts_dir, queue_profile_regexp, profile_name):
        profiles = self._collect_profiles(cap_scripts_dir, queue_profile_regexp)
        for profile in profiles:
            if profile.name == profile_name:
                return profile

    def _get_profiles(self, profile_names):
        for profile_name in profile_names:
            profile = self._find_profile(self._cap_scripts_dir, self._queue_profile_regexp, profile_name)
            if not profile:
                self._logger.warning('Grid engine {} profile does not exist.'.format(profile_name))
                raise GridEngineProfileError()
            yield profile

    def _collect_profiles(self, cap_scripts_dir, profile_regexp):
        self._logger.debug('Collecting existing profiles...')
        for profile_name in os.listdir(cap_scripts_dir):
            profile_match = profile_regexp.match(profile_name)
            if not profile_match:
                continue
            queue_name = profile_match.group(1)
            self._logger.debug('Profile {} has been collected.'.format(queue_name))
            yield Profile(name=queue_name,
                          path_queue=os.path.join(cap_scripts_dir, PROFILE_QUEUE_FORMAT.format(queue_name)),
                          path_autoscaling=os.path.join(cap_scripts_dir, PROFILE_AUTOSCALING_FORMAT.format(queue_name)))

    def _generate_profile(self, profile_name):
        profile_index = str(int(time.time()))
        self._logger.debug('Creating grid engine {} profile...'.format(profile_name))
        os.environ['CP_CAP_SGE_QUEUE_NAME_{}'.format(profile_index)] = profile_name
        generate_sge_profiles()
        self._logger.info('Grid engine {} profile has been created.'.format(profile_name))

    def _configure_profile(self, profile, editor):
        tmp_profile = Profile(name=profile.name,
                              path_queue=tempfile.mktemp(),
                              path_autoscaling=tempfile.mktemp())
        self._logger.debug('Copying grid engine {} profile to {}...'.format(profile.name, tmp_profile.path_autoscaling))
        shutil.copy2(profile.path_autoscaling, tmp_profile.path_autoscaling)
        shutil.copy2(profile.path_queue, tmp_profile.path_queue)
        self._replace_in_file(tmp_profile.path_queue, profile.path_autoscaling, tmp_profile.path_autoscaling)
        self._logger.debug('Modifying temporary grid engine {} profile...'.format(profile.name))
        subprocess.check_call([editor, tmp_profile.path_autoscaling])
        self._logger.debug('Extracting changes from grid engine {} profile...'.format(profile.name))
        profile_changes = self._compare_file(profile.path_autoscaling, tmp_profile.path_autoscaling)
        if not profile_changes:
            self._logger.info('Grid engine {} profile has not been changed.'.format(profile.name))
            return None
        self._logger.info('Grid engine {} profile has been changed:\n{}'.format(profile.name, profile_changes))
        return tmp_profile

    def _replace_in_file(self, file_path, before, after):
        with open(file_path, 'r') as file:
            content = file.read()
        updated_content = re.sub(re.escape(before), re.escape(after), content)
        with open(file_path, 'w') as file:
            file.write(updated_content)

    def _compare_file(self, before_path, after_path):
        try:
            return subprocess.check_output(['diff', before_path, after_path], stderr=subprocess.STDOUT)
        except subprocess.CalledProcessError as e:
            return e.output

    def _persist_profile(self, modified_profile, profile):
        self._logger.debug('Persisting grid engine {} profile changes...'.format(profile.name))
        shutil.move(modified_profile.path_autoscaling, profile.path_autoscaling)

    def _create_queue(self, profile):
        self._logger.debug('Creating grid engine {} queue...'.format(profile.name))
        subprocess.check_call("""
source "{autoscaling_profile_path}"
sge_setup_queue
        """.format(autoscaling_profile_path=profile.path_queue),
                              shell=True)
        self._logger.info('Grid engine {} queue has been created.'.format(profile.name))

    def _stop_autoscaler(self, profile, autoscaling_script_path):
        self._logger.debug('Searching for {} autoscaler processes...'.format(profile.name))
        for proc in self._get_processes(autoscaling_script_path, CP_CAP_SGE_QUEUE_NAME=profile.name):
            self._logger.debug('Stopping process #{}...'.format(proc.pid))
            proc.terminate()

    def _get_processes(self, *args, **kwargs):
        for proc in psutil.process_iter():
            try:
                proc_cmdline = proc.cmdline()
                proc_environ = proc.environ()
            except Exception:
                self._logger.error('Please use root account to configure grid engine profiles.')
                raise GridEngineProfileError()
            if all(arg in proc_cmdline for arg in args) \
                    and all(proc_environ.get(k) == v for k, v in kwargs.items()):
                yield proc

    def _verify_profile(self, profile, autoscaling_script_path):
        self._logger.debug('Verifying grid engine queue {} autoscaling...'.format(profile.name))
        try:
            self._executor.execute("""
export CP_CAP_AUTOSCALE_LOGGING_LEVEL_RUN="ERROR"
export CP_CAP_AUTOSCALE_LOGGING_LEVEL_FILE="ERROR"
export CP_CAP_AUTOSCALE_LOGGING_LEVEL_CONSOLE="INFO"
export CP_CAP_AUTOSCALE_LOGGING_FORMAT="%(message)s"
export CP_CAP_AUTOSCALE_DRY_INIT="true"
source "{autoscaling_profile_path}"
"$CP_PYTHON2_PATH" "{autoscaling_script_path}"
            """.format(autoscaling_profile_path=profile.path_queue,
                       autoscaling_script_path=autoscaling_script_path),
                                   logger=self._logger_warning)
            self._logger.debug('Grid engine {} autoscaling has been verified.'.format(profile.name))
            return True
        except ExecutorError:
            self._logger.warning('Grid engine profile verification has failed. Reverting the changes...')
            return False

    def _launch_autoscaler(self, profile, autoscaling_script_path):
        self._logger.debug('Launching grid engine queue {} autoscaling...'.format(profile.name))
        self._executor.execute("""
source "{autoscaling_profile_path}"
nohup "$CP_PYTHON2_PATH" "{autoscaling_script_path}" >"$LOG_DIR/.nohup.autoscaler.$CP_CAP_SGE_QUEUE_NAME.log" 2>&1 &
        """.format(autoscaling_profile_path=profile.path_queue,
                   autoscaling_script_path=autoscaling_script_path))
        self._logger.info('Grid engine {} autoscaling has been launched.'.format(profile.name))


def _get_manager():
    logging_level_run = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_LEVEL_RUN', default='INFO')
    logging_level_file = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_LEVEL_FILE', default='DEBUG')
    logging_level_console = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_LEVEL_CONSOLE', default='INFO')
    logging_format_file = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_FORMAT_FILE', default='%(asctime)s [%(levelname)s] %(message)s')
    logging_format_console = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_FORMAT_CONSOLE', default='%(message)s')
    logging_task = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOGGING_TASK', default='ConfigureSGEProfiles')
    logging_dir = os.getenv('CP_CAP_SGE_PROFILE_CONFIGURATION_LOG_DIR', default=os.getenv('LOG_DIR', '/var/log'))
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

    logging_logger_root = logging.getLogger()
    logging_logger_root.setLevel(logging.WARNING)

    logging_logger = logging.getLogger(name=logging_task)
    logging_logger.setLevel(logging.DEBUG)

    if not logging_logger.handlers:
        console_formatter = logging.Formatter(logging_format_console)
        console_handler = logging.StreamHandler(sys.stdout)
        console_handler.setLevel(logging_level_console)
        console_handler.setFormatter(console_formatter)
        logging_logger.addHandler(console_handler)

        file_formatter = logging.Formatter(logging_format_file)
        file_handler = logging.FileHandler(os.path.join(logging_dir, logging_file))
        file_handler.setLevel(logging_level_file)
        file_handler.setFormatter(file_formatter)
        logging_logger.addHandler(file_handler)

    api = PipelineAPI(api_url=api_url, log_dir=logging_dir)

    logger = RunLogger(api=api, run_id=run_id)
    logger = TaskLogger(task=logging_task, inner=logger)
    logger = LevelLogger(level=logging_level_run, inner=logger)
    logger = LocalLogger(logger=logging_logger, inner=logger)

    logger_warning = ExplicitLogger(level='WARNING', inner=logger)

    executor = LocalExecutor()
    executor = LoggingExecutor(logger=logger, inner=executor)

    manager = GridEngineProfileManager(executor=executor, logger=logger, logger_warning=logger_warning,
                                       cap_scripts_dir=cap_scripts_dir,
                                       queue_profile_regexp=queue_profile_regexp,
                                       autoscaling_script_path=autoscaling_script_path)
    manager = ResilientGridEngineProfileManager(inner=manager, logger=logger)
    return manager


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

    III. Restart an existing grid engine profile

        sge restart queue.q

    IV. List all existing grid engine profiles

        sge list

    V. Export all existing grid engine profiles

        sge export sge.export.tar.gz

    VI. Export certain existing grid engine profiles

        sge export queue1.q sge.export.tar.gz

        sge export queue1.q queue2.q sge.export.tar.gz

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
    manager = _get_manager()
    manager.create(profile_name=name)


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
    manager = _get_manager()
    manager.configure(profile_name=name)


@cli.command()
@click.argument('name', required=True, type=str)
def restart(name):
    """
    Restarts profiles.

    Depending on the value of CP_CAP_AUTOSCALE parameter (true/false)
    the corresponding queue's autoscaler may be restarted.

    Examples:

        sge restart queue.q

    """
    manager = _get_manager()
    manager.restart(profile_name=name)


@cli.command('list')
def ls():
    """
    Lists profiles.

    It lists all existing grid engine profiles (queues).

    Examples:

        sge list

    """
    manager = _get_manager()
    manager.list()


@cli.command()
@click.argument('names', required=False, type=str, nargs=-1)
@click.argument('output', required=True, type=str)
def export(names, output):
    """
    Export profiles.

    It exports either all or certain existing grid engine profiles (queues) to a single tar gz file.

    Examples:

        sge export sge.export.tar.gz

        sge export queue1.q sge.export.tar.gz

        sge export queue1.q queue2.q sge.export.tar.gz

    """
    manager = _get_manager()
    manager.export(profile_names=names, output_path=output)


if __name__ == '__main__':
    cli(sys.argv[1:])
