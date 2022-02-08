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
import platform

from pipeline.common.common import execute
from pipeline.utils.path import mkdir

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


def _create_lin_user(username, password, home_dir=None, skip_existing=False, logger=None):
    try:
        execute('id -u {username}'.format(username=username))
        if not skip_existing:
            raise RuntimeError('User {} already exists.'.format(username))
    except:
        if home_dir:
            mkdir(os.path.dirname(home_dir))
            home_dir_arg = '-d "{home_dir}" '.format(home_dir=home_dir)
        else:
            home_dir_arg = ''
        execute(('useradd -m -s /bin/bash ' + home_dir_arg + ' -p $(openssl passwd -1 "{password}") "{username}"')
                .format(username=username, password=password),
                logger=logger)


def create_user(username, password, groups='', home_dir=None, skip_existing=False, logger=None):
    current_platform = platform.system()
    if current_platform == 'Windows':
        _create_win_user(username, password, skip_existing=skip_existing)
        for group in groups.split(','):
            _add_win_user_to_group(username, platform.node(), group, skip_existing=skip_existing)
    else:
        _create_lin_user(username, password, home_dir=home_dir, skip_existing=skip_existing, logger=logger)
