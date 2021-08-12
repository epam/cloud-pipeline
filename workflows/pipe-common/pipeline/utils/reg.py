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

from pipeline.utils.platform import assert_windows

assert_windows('Registry package')

import winreg


def _get_user_sid(username):
    import win32security
    desc = win32security.LookupAccountName(None, username)
    return win32security.ConvertSidToStringSid(desc[0])


def set_value(root, path, type, name, value):
    with winreg.CreateKey(root, path) as key:
        winreg.SetValueEx(key, name, 0, type, value)


def set_local_machine_str_value(path, name, value):
    set_value(winreg.HKEY_LOCAL_MACHINE, path, winreg.REG_SZ, name, value)


def set_local_machine_multi_str_value(path, name, value):
    set_value(winreg.HKEY_LOCAL_MACHINE, path, winreg.REG_MULTI_SZ, name, value)


def set_local_machine_dword_value(path, name, value):
    set_value(winreg.HKEY_LOCAL_MACHINE, path, winreg.REG_DWORD, name, value)


def set_user_dword_value(username, path, name, value):
    set_value(winreg.HKEY_USERS, '{}\\{}'.format(_get_user_sid(username), path), winreg.REG_DWORD, name, value)


def set_user_string_value(username, path, name, value):
    set_value(winreg.HKEY_USERS, '{}\\{}'.format(_get_user_sid(username), path), winreg.REG_SZ, name, value)
