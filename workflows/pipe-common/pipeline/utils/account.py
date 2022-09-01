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

from pipeline.common.common import execute
from pipeline.utils.plat import is_windows

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


def _create_lin_user(username, password, uid=None, gid=None, home_dir=None, groups=None, skip_existing=False,
                     logger=None):
    try:
        execute('id {username}'.format(username=username))
        if not skip_existing:
            raise RuntimeError('User {} already exists.'.format(username))
    except Exception:
        uid_arg = '-u "{uid}" '.format(uid=uid) if uid else ''
        gid_arg = '-g "{gid}" '.format(gid=gid) if gid else ''
        groups_arg = '-G "{groups}" '.format(groups=groups) if groups else ''
        home_dir_arg = '-d "{home_dir}" '.format(home_dir=home_dir) if home_dir else ''
        if _is_command_available('useradd', logger) and _is_command_available('groupadd', logger):
            if gid:
                execute(('groupadd "{groupname}" -p $(openssl passwd -1 "{password}") '
                        + gid_arg)
                        .format(groupname=username, password=password))
            execute(('useradd -s "/bin/bash" "{username}" -p $(openssl passwd -1 "{password}") '
                     + uid_arg + gid_arg + home_dir_arg + groups_arg)
                    .format(username=username, password=password),
                    logger=logger)
            return
        if _is_command_available('adduser', logger) and _is_command_available('addgroup', logger):
            if gid:
                execute(('addgroup "{groupname}" -p $(openssl passwd -1 "{password}") '
                        + gid_arg)
                        .format(groupname=username, password=password))
            execute(('adduser -s "/bin/bash" "{username}" -p $(openssl passwd -1 "{password}") '
                     + uid_arg + gid_arg + home_dir_arg + groups_arg)
                    .format(username=username, password=password),
                    logger=logger)
            return
        raise RuntimeError('User {username} cannot be created because both useradd/groupadd and adduser/addgroup '
                           'commands are not available.'
                           .format(username=username))


def _is_command_available(command, logger):
    try:
        execute('command -v %s' % command, logger=logger)
        return True
    except Exception:
        logger.warning('Command %s is not available' % command)
        return False


def create_user(username, password, uid=None, gid=None, groups='', home_dir=None, skip_existing=False, logger=None):
    if is_windows():
        _create_win_user(username, password, skip_existing=skip_existing)
        for group in groups.split(','):
            _add_win_user_to_group(username, platform.node(), group, skip_existing=skip_existing)
    else:
        _create_lin_user(username, password, uid=uid, gid=gid, groups=groups, home_dir=home_dir,
                         skip_existing=skip_existing, logger=logger)
