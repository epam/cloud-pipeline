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

import subprocess

_NOMACHINE_DIR = 'C:\\Program Files (x86)\\NoMachine'
_NOMACHINE_SERVER_CONFIG = _NOMACHINE_DIR + '\\etc\\server.cfg'
_NOMACHINE_SERVER_EXECUTABLE = _NOMACHINE_DIR + '\\bin\\nxserver.exe'


def configure_nomachine_win(nomachine_server_parameters):
    with open(_NOMACHINE_SERVER_CONFIG, 'a') as f:
        f.write('\n')
        for nomachine_server_parameter_key_value in nomachine_server_parameters.split(','):
            key, value = nomachine_server_parameter_key_value.split('=', 1)
            f.write('{} {}\n'.format(key, value))
    subprocess.check_call([_NOMACHINE_SERVER_EXECUTABLE, '--restart'])
