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


def _create_win_user(username, password):
    import win32net
    import win32netcon
    win32net.NetUserAdd(None, 1, {
        'name': username,
        'password': password,
        'priv': win32netcon.USER_PRIV_USER,
        'flags': win32netcon.UF_NORMAL_ACCOUNT | win32netcon.UF_SCRIPT | win32netcon.UF_DONT_EXPIRE_PASSWD
    })


def _add_win_user_to_group(username, domain, group):
    import win32net
    win32net.NetLocalGroupAddMembers(None, group, 3, [{
        'domainandname': domain + '\\' + username
    }])


def create_user(username, password, groups):
    current_platform = platform.system()
    if current_platform == 'Windows':
        _create_win_user(username, password)
        for group in groups.split(','):
            _add_win_user_to_group(username, platform.node(), group)
    else:
        raise RuntimeError('Creating user is not supported on {platform} platform.'
                           .format(platform=current_platform))
