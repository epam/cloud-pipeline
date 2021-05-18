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

import json
import pathlib
import os
import platform
import subprocess

_CREATE_SHORTCUT_PSHELL_SCRIPT_TEMPLATE = '''
$s=(New-Object -COM WScript.Shell).CreateShortcut(\'{}\\Desktop\\Cloud-Data.lnk\');
$s.TargetPath=\'{}\\cloud-data.exe\';
$s.Save()
'''


def _run_powershell(cmd):
    completed = subprocess.run(["powershell", "-Command", cmd], capture_output=False)
    return completed


def _create_cloud_data_shortcut(node_user_home, cloud_data_parent_dir):
    cloud_data_dir = os.path.join(cloud_data_parent_dir, 'cloud-data-app')
    _run_powershell(_CREATE_SHORTCUT_PSHELL_SCRIPT_TEMPLATE.format(node_user_home, cloud_data_dir))


def _create_cloud_data_config(node_user_home, edge_url, username, token):
    webdav_config = {
        'ignoreCertificateErrors': True,
        'username': username,
        'password': token,
        'server': "{}/webdav/{}".format(edge_url, username),
        'version': ''
    }
    cloud_data_config_folder = os.path.join(node_user_home, '.pipe-webdav-client')
    pathlib.Path(cloud_data_config_folder).mkdir(parents=True, exist_ok=True)
    with open(os.path.join(cloud_data_config_folder, 'webdav.config'), 'w') as outfile:
        json.dump(webdav_config, outfile)


def configure_cloud_data_win(cloud_data_parent_dir, edge_url, node_user, dav_user, token):
    current_platform = platform.system()
    if current_platform == 'Windows':
        user_home_dir = '{}\\{}\\{}'.format(os.getenv('SystemDrive', 'c:'), 'Users', node_user)
        _create_cloud_data_config(user_home_dir, edge_url, dav_user, token)
        _create_cloud_data_shortcut(user_home_dir, cloud_data_parent_dir)
    else:
        raise RuntimeError('Cloud-Data installation is not supported on {platform} platform.'
                           .format(platform=current_platform))
