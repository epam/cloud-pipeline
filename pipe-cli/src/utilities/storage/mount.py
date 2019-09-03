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
from os.path import dirname
import platform
import shutil
import stat
import subprocess
import sys
import uuid
from abc import abstractmethod, ABCMeta

import click

from src.api.preferenceapi import PreferenceAPI
from src.config import Config, is_frozen


class AbstractMount:
    __metaclass__ = ABCMeta

    @abstractmethod
    def get_mount_cmd(self, config, mountpoint, options, web_dav_url):
        pass


class FrozenMount(object):

    def get_mount_cmd(self, config, mountpoint, options, web_dav_url):
        mount_bin = self.get_mount_executable(config)
        mount_script = self.create_mount_script(os.path.dirname(Config.config_path()), mount_bin,
                                                mountpoint, options, web_dav_url)
        return ['bash', mount_script]

    def create_mount_script(self, config_folder, mount_bin, mountpoint, options, web_dav_url):
        mount_cmd = '%s --mountpoint %s  --webdav %s' % (mount_bin, mountpoint, web_dav_url)
        if options:
            mount_cmd += '-o ' + options
        mount_script = os.path.join(config_folder, 'pipe-fuse-script' + str(uuid.uuid4()))
        with open(mount_script, 'w') as script:
            # run pipe-fuse
            script.write(mount_cmd + '\n')
            # delete tmp pipe-fuse
            script.write('rm -f %s\n' % mount_bin)
            # self delete launch script
            script.write('rm -- "$0"\n')
        st = os.stat(mount_script)
        os.chmod(mount_script, st.st_mode | stat.S_IEXEC)
        return mount_script

    def get_mount_executable(self, config):
        mount_packed = config.build_inner_module_path('mount/pipe-fuse')
        config_folder = os.path.dirname(Config.config_path())
        mount_bin = os.path.join(config_folder, 'pipe-fuse' + str(uuid.uuid4()))
        shutil.copy(mount_packed, mount_bin)
        return mount_bin


class SourceMount(object):

    def get_mount_cmd(self, config, mountpoint, options, web_dav_url):
        python_exec = sys.executable
        script_folder = dirname(Config.get_base_source_dir())
        script_path = os.path.join(script_folder, 'mount/pipe-fuse.py')
        mount_cmd = [python_exec, script_path, '--mountpoint', mountpoint, '--webdav', web_dav_url]
        if options:
            mount_cmd.extend(['-o', options])
        return mount_cmd


class Mount(object):

    def mount_storages(self, mountpoint, options=None, quiet=False):
        if platform.system() == 'Windows':
            click.echo('Mount command is not supported for Windows OS', err=True)
            sys.exit(1)
        config = Config.instance()
        username = config.get_current_user()
        web_dav_url = PreferenceAPI.get_preference('base.dav.auth.url').value
        web_dav_url = web_dav_url.replace('auth-sso/', username + '/')
        mount = FrozenMount() if is_frozen() else SourceMount()
        self.mount_dav(mount, config, mountpoint, options, web_dav_url)

    def mount_dav(self, mount, config, mountpoint, options, web_dav_url):
        mount_cmd = mount.get_mount_cmd(config, mountpoint, options, web_dav_url)
        self.run(config, mount_cmd)

    def run(self, config, mount_cmd):
        with open(os.devnull, 'w') as dev_null:
            mount_environment = os.environ.copy()
            mount_environment['API_TOKEN'] = config.access_key
            mount_aps_proc = subprocess.Popen(mount_cmd,
                                              stdout=dev_null, stderr=dev_null,
                                              env=mount_environment)
            if mount_aps_proc.poll() is not None:
                click.echo('Mount command exited with return code: %d' % mount_aps_proc.returncode, err=True)
                sys.exit(1)


