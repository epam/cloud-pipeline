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
import subprocess
from urllib import request
import zipfile

_DEFAULT_DISTRIBUTION_URL = \
    'https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/cloud-data/win/cloud-data-win-x64.zip'
_CREATE_SHORTCUT_PSHELL_SCRIPT_TEMPLATE = '''
$s=(New-Object -COM WScript.Shell).CreateShortcut(\'{}\\Desktop\\Cloud-Data.lnk\');
$s.TargetPath=\'{}\\cloud-data.exe\';
$s.Save()
'''


def download_cloud_data_zip(target_path):
    cloud_data_distr_url = os.getenv('CP_CLOUD_DATA_WIN_DISTRIBUTION_ZIP', _DEFAULT_DISTRIBUTION_URL)
    request.urlretrieve(cloud_data_distr_url, target_path)


def unpack_cloud_data(path_to_file, target_dir):
    with zipfile.ZipFile(path_to_file, 'r') as zip_ref:
        zip_ref.extractall(target_dir)
    os.remove(path_to_file)


def configure_cloud_data(cloud_data_installation_dir):
    user_home_dir = os.getenv('USERPROFILE')
    _run_powershell(_CREATE_SHORTCUT_PSHELL_SCRIPT_TEMPLATE.format(user_home_dir, cloud_data_installation_dir))


def _run_powershell(cmd):
    completed = subprocess.run(["powershell", "-Command", cmd], capture_output=False)
    return completed


if __name__ == '__main__':
    download_cloud_data_zip('c:\\etc\\cloud-data.zip')
    unpack_cloud_data('c:\\etc\\cloud-data.zip', 'c:\\host')
    configure_cloud_data('c:\\host\\cloud-data-app')
