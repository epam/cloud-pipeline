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
from pipeline.utils.scheduler import schedule_powershell_script_on_logon


def schedule(username, cloud_data_config_dir, script):
    user_default_home_folder = os.path.join('C:\\Users', username.lower())
    replacement_dict = {
        '<USER_DEFAULT_HOME>': user_default_home_folder,
        '<CLOUD_DATA_CONFIG_DIR>': cloud_data_config_dir
    }
    schedule_powershell_script_on_logon(username, 'CloudDataInstallationFinalizer', script, replacement_dict)
