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

import os.path
import subprocess

from pipeline.utils.reg import set_user_str_value


_nice_dcv_path = 'C:\\Program Files\\NICE\\DCV\\Server'
_nice_dcv_cli_path = _nice_dcv_path + '\\bin\\dcv.exe'
_nice_dcv_reg_path = 'Software\\GSettings\\com\\nicesoftware\\dcv'
_nice_dcv_security_reg_path = _nice_dcv_reg_path + '\\security'


def configure_nice_dcv_win(run_dir, user):
    set_user_str_value(_nice_dcv_security_reg_path, 'authentication', 'none', 'S-1-5-18')
    subprocess.check_call(['powershell', '-c', 'restart-service "DCV Server"'])
    nice_dcv_permissions_path = os.path.join(run_dir, 'nice_dcv_permissions.txt')
    with open(nice_dcv_permissions_path, 'w') as f:
        f.write("[permissions]\n%any% allow builtin\n")
    subprocess.check_call([_nice_dcv_cli_path, 'close-session', 'console'])
    subprocess.check_call([_nice_dcv_cli_path, 'create-session',
                           '--type', 'console',
                           '--name', 'console',
                           '--owner', user,
                           '--permissions-file', nice_dcv_permissions_path,
                           '--max-concurrent-clients', '1',
                           'console'])
