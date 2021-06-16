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

import win32serviceutil
import win32security

from pipeline.utils.reg import set_user_dword_value, set_local_machine_dword_value

_microsoft_settings = 'SOFTWARE\\Microsoft'
_internet_settings = _microsoft_settings + '\\Windows\\CurrentVersion\\Internet Settings'
_internet_trusted_sources = _internet_settings + '\\ZoneMap\\EscDomains'
_internet_explorer_main_settings = _microsoft_settings + '\\Internet Explorer\\Main'
_windows_webdav_client_parameters = 'SYSTEM\\CurrentControlSet\\Services\\WebClient\\Parameters'


def get_user_sid(username):
    desc = win32security.LookupAccountName(None, username)
    return win32security.ConvertSidToStringSid(desc[0])


def configure_drive_mount_env_win(username, edge_host):
    user_sid = get_user_sid(username)
    set_user_dword_value(_internet_trusted_sources + '\\{}'.format(edge_host), 'https', 2, sid=user_sid)
    set_user_dword_value(_internet_trusted_sources + '\\internet', 'about', 2, sid=user_sid)
    set_user_dword_value(_internet_explorer_main_settings, 'DisableFirstRunCustomize', 1, sid=user_sid)
    set_user_dword_value(_internet_settings, 'WarnonZoneCrossing', 0, sid=user_sid)
    set_local_machine_dword_value(_windows_webdav_client_parameters, 'FileSizeLimitInBytes', 0xFFFFFFFF)
    win32serviceutil.RestartService('WebClient')
