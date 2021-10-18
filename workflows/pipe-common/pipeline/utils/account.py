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

import platform

_USER_ALREADY_EXISTS_WIN_ERROR = 2224
_USER_ALREADY_IN_GROUP_WIN_ERROR = 1378


def _create_win_user(username, password, skip_existing=False):
    import win32net
    import win32netcon
    import pywintypes
    try:
        win32net.NetUserAdd(None, 1, {
            'name': username,
            'password': password,
            'priv': win32netcon.USER_PRIV_USER,
            'flags': win32netcon.UF_NORMAL_ACCOUNT | win32netcon.UF_SCRIPT | win32netcon.UF_DONT_EXPIRE_PASSWD
        })
    except pywintypes.error as e:
        if e.winerror == _USER_ALREADY_EXISTS_WIN_ERROR and skip_existing:
            pass
        else:
            raise


def _add_win_user_to_group(username, domain, group, skip_existing=False):
    import win32net
    import pywintypes
    try:
        win32net.NetLocalGroupAddMembers(None, group, 3, [{
            'domainandname': domain + '\\' + username
        }])
    except pywintypes.error as e:
        if e.winerror == _USER_ALREADY_IN_GROUP_WIN_ERROR and skip_existing:
            pass
        else:
            raise


def create_user(username, password, groups, skip_existing=False):
    current_platform = platform.system()
    if current_platform == 'Windows':
        _create_win_user(username, password, skip_existing=skip_existing)
        for group in groups.split(','):
            _add_win_user_to_group(username, platform.node(), group, skip_existing=skip_existing)
    else:
        raise RuntimeError('Creating user is not supported on {platform} platform.'
                           .format(platform=current_platform))
