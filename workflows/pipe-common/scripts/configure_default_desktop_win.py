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
import shutil
import glob
from pipeline.utils.reg import set_user_string_value


_default_user_home_parent_dir = 'C:\\Users'
_default_win_wallpaper = 'C:\\Windows\\Web\\Wallpaper\\Windows\\img0.jpg'
_desktop_settings = 'Control Panel\\Desktop'
_excess_shortcut_suffixes = ['EC2*']
_this_pc_shortcut_path = 'AppData\\Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\System Tools\\computer.lnk'
_no_machine_shortcut = 'C:\\Users\\Public\\Desktop\\NoMachine.lnk'


def _set_default_wallpaper(username):
    set_user_string_value(username, _desktop_settings, 'WallPaper', _default_win_wallpaper)


def _remove_excess_shortcuts(desktop_folder):
    os.remove(_no_machine_shortcut)
    for suffix in _excess_shortcut_suffixes:
        matching_shortcuts = glob.glob(os.path.join(desktop_folder, suffix))
        for shortcut_to_remove in matching_shortcuts:
            os.remove(shortcut_to_remove)


def _copy_this_pc_shortcut(app_data_folder, desktop_folder):
    user_specific_this_pc_shortcut = os.path.join(app_data_folder, _this_pc_shortcut_path)
    shutil.copy(user_specific_this_pc_shortcut, os.path.join(desktop_folder, 'This PC.lnk'))


def configure_default_desktop_win(username):
    home_folder = os.path.join(_default_user_home_parent_dir, username)
    desktop_folder = os.path.join(home_folder, 'Desktop')
    _copy_this_pc_shortcut(home_folder, desktop_folder)
    _remove_excess_shortcuts(desktop_folder)
    _set_default_wallpaper(username)
