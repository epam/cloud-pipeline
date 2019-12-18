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

import os
import shutil
import stat
import subprocess
import sys
import time
import uuid
from abc import abstractmethod, ABCMeta
from os.path import dirname
from src.version import __bundle_info__

import click

from src.api.preferenceapi import PreferenceAPI
from src.config import Config, is_frozen


class AbstractMount(object):
    __metaclass__ = ABCMeta

    @abstractmethod
    def get_mount_webdav_cmd(self, config, mountpoint, options, web_dav_url, threading=False):
        pass

    @abstractmethod
    def get_mount_storage_cmd(self, config, mountpoint, options, bucket, threading=False):
        pass


class FrozenMount(AbstractMount):

    def get_mount_webdav_cmd(self, config, mountpoint, options, web_dav_url, threading=False):
        additional_args = self._append_threading('--webdav ' + web_dav_url, threading)
        return self._get_mount_cmd(config, mountpoint, options, additional_args)

    def get_mount_storage_cmd(self, config, mountpoint, options, bucket, threading=False):
        additional_args = self._append_threading('--bucket ' + bucket, threading)
        return self._get_mount_cmd(config, mountpoint, options, additional_args)

    def _append_threading(self, args, threading):
        return args + ' --threads' if threading else args

    def _get_mount_cmd(self, config, mountpoint, options, additional_arguments):
        mount_bin = self.get_mount_executable(config)
        mount_script = self.create_mount_script(os.path.dirname(Config.config_path()), mount_bin,
                                                mountpoint, options, additional_arguments)
        return ['bash', mount_script]

    def create_mount_script(self, config_folder, mount_bin, mountpoint, options, additional_arguments):
        mount_cmd = '%s --mountpoint %s %s' % (mount_bin, mountpoint, additional_arguments)
        if options:
            mount_cmd += ' -o ' + options
        mount_script = os.path.join(config_folder, 'pipe-fuse-script' + str(uuid.uuid4()))
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
        return mount_script

    # The pipe-fuse "one-file" distr is copied to the ~/.pipe/pipe-fuseUID
    # This is required, as it shall keep running, when the "pipe" process exits and cleans it's temp dir
    # Otherwise (i.e. "one-folder") - packed binary is used directly
    def get_mount_executable(self, config):
        mount_packed = config.build_inner_module_path('mount')
        config_folder = os.path.dirname(Config.config_path())
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

    def get_mount_webdav_cmd(self, config, mountpoint, options, web_dav_url, threading=False):
        additional_args = self._append_threading(['--webdav', web_dav_url], threading)
        return self._get_mount_cmd(config, mountpoint, options, additional_args)

    def get_mount_storage_cmd(self, config, mountpoint, options, bucket, threading=False):
        additional_args = self._append_threading(['--bucket', bucket], threading)
        return self._get_mount_cmd(config, mountpoint, options, additional_args)

    def _append_threading(self, args, threading):
        if threading:
            args.append('--threads')
        return args

    def _get_mount_cmd(self, config, mountpoint, options, additional_arguments):
        python_exec = sys.executable
        script_folder = dirname(Config.get_base_source_dir())
        script_path = os.path.join(script_folder, 'mount/pipe-fuse.py')
        mount_cmd = [python_exec, script_path, '--mountpoint', mountpoint]
        mount_cmd.extend(additional_arguments)
        if options:
            mount_cmd.extend(['-o', options])
        return mount_cmd


class Mount(object):

    def mount_storages(self, mountpoint, file=False, bucket=None, options=None, quiet=False, log_file=None,
                       threading=False):
        config = Config.instance()
        username = config.get_current_user()
        mount = FrozenMount() if is_frozen() else SourceMount()
        if file:
            web_dav_url = PreferenceAPI.get_preference('base.dav.auth.url').value
            web_dav_url = web_dav_url.replace('auth-sso/', username + '/')
            self.mount_dav(mount, config, mountpoint, options, web_dav_url, log_file=log_file, threading=threading)
        else:
            self.mount_storage(mount, config, mountpoint, options, bucket, log_file=log_file, threading=threading)

    def mount_dav(self, mount, config, mountpoint, options, web_dav_url, log_file=None, threading=False):
        mount_cmd = mount.get_mount_webdav_cmd(config, mountpoint, options, web_dav_url, threading=threading)
        self.run(config, mount_cmd, log_file=log_file)

    def mount_storage(self, mount, config, mountpoint, options, bucket, log_file=None, threading=False):
        mount_cmd = mount.get_mount_storage_cmd(config, mountpoint, options, bucket, threading=threading)
        self.run(config, mount_cmd, log_file=log_file)

    def run(self, config, mount_cmd, mount_timeout=5, log_file=None):
        output_file = log_file if log_file else os.devnull
        with open(output_file, 'w') as output:
            mount_environment = os.environ.copy()
            mount_environment['API'] = config.api
            mount_environment['API_TOKEN'] = config.access_key
            if config.proxy:
                mount_environment['http_proxy'] = config.proxy
                mount_environment['https_proxy'] = config.proxy
                mount_environment['ftp_proxy'] = config.proxy
            mount_aps_proc = subprocess.Popen(mount_cmd,
                                              stdout=output,
                                              stderr=subprocess.STDOUT,
                                              env=mount_environment)
            time.sleep(mount_timeout)
            if mount_aps_proc.poll() is not None:
                click.echo('Failed to mount storages. Mount command exited with return code: %d'
                           % mount_aps_proc.returncode, err=True)
                sys.exit(1)
