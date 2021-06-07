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

from pipeline.utils.scheduler import schedule_powershell_script_on_logon


def schedule(username, edge_host, edge_port, token, script):
    replacement_dict = {
        '<USER_NAME>': username,
        '<USER_TOKEN>': token,
        '<EDGE_HOST>': edge_host,
        '<EDGE_PORT>': edge_port
    }
    schedule_powershell_script_on_logon(username, 'CloudPipelineDriveMapping', script, replacement_dict, True)
