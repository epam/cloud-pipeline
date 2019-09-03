import os
import platform
import shutil
import stat
import subprocess
import sys
import uuid

import click

from src.api.preferenceapi import PreferenceAPI
from src.config import Config, is_frozen


class Mount(object):

    def mount_storages(self, mountpoint, options=None, quiet=False):
        if platform.system() == 'Windows':
            click.echo('Mount command is not supported for Windows OS', err=True)
            sys.exit(1)
        config = Config.instance()
        username = config.get_current_user()
        web_dav_url = PreferenceAPI.get_preference('base.dav.auth.url').value
        web_dav_url = web_dav_url.replace('auth-sso/', username + '/')
        if is_frozen():
            self.run_mount_frozen(config, mountpoint, options, web_dav_url)
        else:
            # TODO implement for src dist
            pass

    def run_mount_frozen(self, config, mountpoint, options, web_dav_url):
        with open(os.devnull, 'w') as dev_null:
            mount_script = self.create_mount_script(config, mountpoint, options, web_dav_url)
            mount_environment = os.environ.copy()
            mount_environment['API_TOKEN'] = config.access_key
            mount_aps_proc = subprocess.Popen(['bash', mount_script],
                                              stdout=dev_null, stderr=dev_null,
                                              env=mount_environment)
            if mount_aps_proc.poll() is not None:
                click.echo('Mount command exited with return code: %d' % mount_aps_proc.returncode, err=True)
                sys.exit(1)

    def create_mount_script(self, config, mountpoint, options, web_dav_url):
        mount_bin = config.build_inner_module_path('mount/pipe-fuse')
        config_folder = os.path.dirname(Config.config_path())
        suffix = str(uuid.uuid4())
        mount_cp = os.path.join(config_folder, 'pipe-fuse' + suffix)
        shutil.copy(mount_bin, mount_cp)
        mount_cmd = '%s --mountpoint %s  --webdav %s' % (mount_cp, mountpoint, web_dav_url)
        if options:
            mount_cmd += '-o ' + options
        mount_script = os.path.join(config_folder, 'pipe-fuse-script' + suffix)
        with open(mount_script, 'w') as script:
            # run pipe-fuse
            script.write(mount_cmd + '\n')
            # delete tmp pipe-fuse
            script.write('rm -f %s\n' % mount_cp)
            # self delete launch script
            script.write('rm -- "$0"\n')
        st = os.stat(mount_script)
        os.chmod(mount_script, st.st_mode | stat.S_IEXEC)
        return mount_script
