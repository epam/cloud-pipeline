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

from pipeline.utils.reg import set_local_machine_dword_value


_win_policies_reg_path = 'SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System'
_win_personalization_reg_path = 'SOFTWARE\\Policies\\Microsoft\\Windows\\Personalization'
_win_start_menu_reg_path = 'SOFTWARE\\Microsoft\\PolicyManager\\default\\Start'


def configure_system_settings_win():
    set_local_machine_dword_value(_win_policies_reg_path, 'disablecad', 1)
    set_local_machine_dword_value(_win_personalization_reg_path, 'NoLockScreen', 1)
    set_local_machine_dword_value(_win_start_menu_reg_path + '\\HideShutDown', 'value', 1)
    set_local_machine_dword_value(_win_start_menu_reg_path + '\\HideRestart', 'value', 1)
