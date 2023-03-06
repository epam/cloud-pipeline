# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import shutil
import stat
import subprocess
import sys
import time
import uuid
from abc import abstractmethod, ABCMeta
from os.path import dirname

from src.utilities.platform_utilities import is_windows
from src.version import __bundle_info__

import click

from src.api.preferenceapi import PreferenceAPI
from src.config import Config, is_frozen

MS_IN_SEC = 1000


class AbstractMount(object):
    __metaclass__ = ABCMeta

    def get_mount_webdav_cmd(self, config, mountpoint, options, custom_options, web_dav_url, mode, threading=False,
                             log_level=None, show_archive=False):
        additional_args = self._append_arguments(['--webdav', web_dav_url], threading, log_level, custom_options,
                                                 show_archive)
        return self._get_mount_cmd(config, mountpoint, options, additional_args, mode)

    def get_mount_storage_cmd(self, config, mountpoint, options, custom_options, bucket, mode, threading=False,
                              log_level=None, show_archive=False):
        additional_args = self._append_arguments(['--bucket', bucket], threading, log_level, custom_options,
                                                 show_archive)
        return self._get_mount_cmd(config, mountpoint, options, additional_args, mode)

    def _append_arguments(self, args, threading, log_level, custom_options, show_archive):
        if threading:
            args.append('--threads')
        if log_level:
            args.extend(['--logging-level', log_level])
        if show_archive:
            args.append('--show-archive')
        if custom_options:
            args.extend(self._build_custom_option_arguments(custom_options))
        return args

    def _build_custom_option_arguments(self, custom_options):
        arguments = []
        for option_string in custom_options.split(','):
            chunks = option_string.split('=')
            arguments.append('--' + chunks[0])
            if len(chunks) > 1:
                arguments.append(chunks[1])
        return arguments

    @abstractmethod
    def _get_mount_cmd(self, config, mountpoint, options, additional_arguments, mode):
        pass

    def get_python_path(self):
        return None


class FrozenMount(AbstractMount):

    def _get_mount_cmd(self, config, mountpoint, options, additional_arguments, mode):
        mount_bin = self.get_mount_executable(config)
        config_folder = os.path.dirname(Config.get_home_dir_config_path())
        return self.resolve_mount_cmd(config_folder, mount_bin, mountpoint, options, additional_arguments, mode)

    def resolve_mount_cmd(self, config_folder, mount_bin, mountpoint, options, additional_arguments, mode):
        mount_cmd = '%s --mountpoint %s %s --mode %d' % (mount_bin, mountpoint, ' '.join(additional_arguments), mode)
        mount_script = os.path.join(config_folder, 'pipe-fuse-script' + str(uuid.uuid4()))
        if options:
            mount_cmd += ' -o ' + options
        if is_windows():
            mount_script += '.ps1'
            with open(mount_script, 'w') as script:
                script.write(mount_cmd)
            return ['powershell', '-file', mount_script]
        else:
            with open(mount_script, 'w') as script:
                # run pipe-fuse
                script.write(mount_cmd + '\n')
                # delete tmp pipe-fuse directory if it is "one-file" bundle
                if self._needs_pipe_fuse_copy():
                    script.write('rm -rf %s\n' % os.path.dirname(mount_bin))
                # self delete launch script
                script.write('rm -- "$0"\n')
            st = os.stat(mount_script)
            os.chmod(mount_script, st.st_mode | stat.S_IEXEC)
            return ['bash', mount_script]

    # The pipe-fuse "one-file" distr is copied to the ~/.pipe/pipe-fuseUID
    # This is required, as it shall keep running, when the "pipe" process exits and cleans it's temp dir
    # Otherwise (i.e. "one-folder") - packed binary is used directly
    def get_mount_executable(self, config):
        mount_packed = config.build_inner_module_path('mount')
        config_folder = os.path.dirname(Config.get_home_dir_config_path())
        mount_bin = os.path.join(mount_packed, 'pipe-fuse')

        if self._needs_pipe_fuse_copy():
            mount_tmp_folder = os.path.join(config_folder, 'pipe-fuse' + str(uuid.uuid4()))
            shutil.copytree(mount_packed, mount_tmp_folder)
            return os.path.join(mount_tmp_folder, 'pipe-fuse')
        else:
            return mount_bin

    def _needs_pipe_fuse_copy(self):
        return __bundle_info__['bundle_type'] == 'one-file'


class SourceMount(AbstractMount):

    def _get_mount_cmd(self, config, mountpoint, options, additional_arguments, mode):
        python_exec = sys.executable
        script_folder = dirname(Config.get_base_source_dir())
        script_path = os.path.join(script_folder, 'mount/pipe-fuse.py')
        mount_cmd = [python_exec, script_path, '--mountpoint', mountpoint, '--mode', str(mode)]
        mount_cmd.extend(additional_arguments)
        if options:
            mount_cmd.extend(['-o', options])
        return mount_cmd

    def get_python_path(self):
        return dirname(Config.get_base_source_dir())


class Mount(object):

    def mount_storages(self, mountpoint, file=False, bucket=None, options=None, custom_options=None, quiet=False,
                       log_file=None, log_level=None, threading=False, mode=700, timeout=1000, show_archive=False):
        config = Config.instance()
        username = self.normalize_username(config.get_current_user())
        mount = FrozenMount() if is_frozen() else SourceMount()
        if file:
            web_dav_url = PreferenceAPI.get_preference('base.dav.auth.url').value
            web_dav_url = web_dav_url.replace('auth-sso/', username + '/')
            self.mount_dav(mount, config, mountpoint, options, custom_options, web_dav_url, mode,
                           log_file=log_file, log_level=log_level, threading=threading, timeout=timeout,
                           show_archive=show_archive)
        else:
            self.mount_storage(mount, config, mountpoint, options, custom_options, bucket, mode,
                               log_file=log_file, log_level=log_level, threading=threading, timeout=timeout,
                               show_archive=show_archive)

    # Split username by @ to get only first part of the user name,
    # because webdav trail user name as well to get get user folder name
    def normalize_username(self, username):
        return username.split('@')[0]

    def mount_dav(self, mount, config, mountpoint, options, custom_options, web_dav_url, mode,
                  log_file=None, log_level=None, threading=False, timeout=1000, show_archive=False):
        mount_cmd = mount.get_mount_webdav_cmd(config, mountpoint, options, custom_options, web_dav_url, mode,
                                               log_level=log_level, threading=threading, show_archive=show_archive)
        python_path = mount.get_python_path()
        self.run(config, mount_cmd, python_path=python_path, log_file=log_file, mount_timeout=timeout)

    def mount_storage(self, mount, config, mountpoint, options, custom_options, bucket, mode,
                      log_file=None, log_level=None, threading=False, timeout=1000, show_archive=False):
        mount_cmd = mount.get_mount_storage_cmd(config, mountpoint, options, custom_options, bucket, mode,
                                                log_level=log_level, threading=threading, show_archive=show_archive)
        python_path = mount.get_python_path()
        self.run(config, mount_cmd, python_path=python_path, log_file=log_file, mount_timeout=timeout)

    def run(self, config, mount_cmd, mount_timeout=5*MS_IN_SEC, python_path=None, log_file=None):
        output_file = log_file if log_file else os.devnull
        with open(output_file, 'w') as output:
            mount_environment = os.environ.copy()
            mount_environment['API'] = config.api
            mount_environment['API_TOKEN'] = config.get_token()
            if python_path:
                mount_environment['PYTHONPATH'] = python_path
            if config.proxy:
                mount_environment['http_proxy'] = config.proxy
                mount_environment['https_proxy'] = config.proxy
                mount_environment['ftp_proxy'] = config.proxy
            mount_aps_proc = subprocess.Popen(mount_cmd,
                                              stdout=output,
                                              stderr=subprocess.STDOUT,
                                              env=mount_environment)
            time.sleep(mount_timeout / MS_IN_SEC)
            if mount_aps_proc.poll() is not None:
                click.echo('Failed to mount storages. Mount command exited with return code: %d'
                           % mount_aps_proc.returncode, err=True)
                sys.exit(1)
