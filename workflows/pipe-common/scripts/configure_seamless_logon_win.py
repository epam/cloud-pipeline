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

import base64
import platform

import win32crypt

from pipeline.utils.reg import set_local_machine_str_value, set_local_machine_multi_str_value, set_local_machine_dword_value


_pgina_credentials_provider_guid = 'd0befefb-3d2c-44da-bbad-3b2d04557246'
_win_credentials_providers_reg_path = 'SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Authentication\\Credential Providers'
_win_pgina_credentials_provider_reg_path = _win_credentials_providers_reg_path + '\\{' + _pgina_credentials_provider_guid + '}'
_win64_credentials_providers_reg_path = 'SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Authentication\\Credential Providers'
_win64_pgina_credentials_provider_reg_path = _win64_credentials_providers_reg_path + '\\{' + _pgina_credentials_provider_guid + '}'

_pgina_authenticate_all_plugin_guid = 'a394c1c6-81ff-4864-9b69-c82f07c0274c'
_pgina_local_machine_plugin_guid = '12fa152d-a2e3-4c8d-9535-5dcd49dfcb6d'
_pgina_single_user_login_plugin_guid = '81f8034e-e278-4754-b10c-7066656de5b7'
_pgina_reg_path = 'SOFTWARE\\pGina3'
_pgina_plugins_reg_path = _pgina_reg_path + '\\Plugins'
_pgina_single_user_login_plugin_reg_path = _pgina_plugins_reg_path + '\\' + _pgina_single_user_login_plugin_guid
_pgina_local_machine_plugin_reg_path = _pgina_plugins_reg_path + '\\' + _pgina_local_machine_plugin_guid


def configure_seamless_logon_win(username, password, groups, logon_title, logon_image_path):
    _enable_pgina()
    _configure_login_screen(logon_title, logon_image_path)
    _configure_singe_user_login_plugin(username, password)
    _configure_local_machine_plugin(groups)
    _enable_plugins()
    _configure_plugins_order()


def _enable_pgina():
    set_local_machine_dword_value(_win_pgina_credentials_provider_reg_path, 'Disabled', 0)
    set_local_machine_dword_value(_win64_pgina_credentials_provider_reg_path, 'Disabled', 0)


def _configure_login_screen(logon_title, logon_image_path):
    set_local_machine_str_value(_pgina_reg_path, 'HideUsernameField', 'True')
    set_local_machine_str_value(_pgina_reg_path, 'HidePasswordField', 'True')
    set_local_machine_str_value(_pgina_reg_path, 'Motd', logon_title)
    set_local_machine_str_value(_pgina_reg_path, 'TileImage', logon_image_path)


def _configure_singe_user_login_plugin(username, password):
    encrypted_password = base64.b64encode(win32crypt.CryptProtectData(password.encode('ascii'), Flags=0x4)).decode('ascii')
    set_local_machine_str_value(_pgina_single_user_login_plugin_reg_path, 'Username', username)
    set_local_machine_str_value(_pgina_single_user_login_plugin_reg_path, 'Password', encrypted_password)
    set_local_machine_str_value(_pgina_single_user_login_plugin_reg_path, 'Domain', platform.node())


def _configure_local_machine_plugin(groups):
    set_local_machine_multi_str_value(_pgina_local_machine_plugin_reg_path, 'MandatoryGroups', groups.split(','))


def _enable_plugins():
    set_local_machine_dword_value(_pgina_reg_path, _pgina_local_machine_plugin_guid, 10)
    set_local_machine_dword_value(_pgina_reg_path, _pgina_single_user_login_plugin_guid, 8)
    set_local_machine_dword_value(_pgina_reg_path, _pgina_authenticate_all_plugin_guid, 2)
    set_local_machine_dword_value(_pgina_reg_path, '0f52390b-c781-43ae-bd62-553c77fa4cf7', 0)
    set_local_machine_dword_value(_pgina_reg_path, 'a89df410-53ca-4fe1-a6ca-4479b841ca19', 0)
    set_local_machine_dword_value(_pgina_reg_path, 'b68cf064-9299-4765-ac08-acb49f93f892', 0)
    set_local_machine_dword_value(_pgina_reg_path, '16fc47c0-f17b-4d99-a820-edbf0b0c764a', 0)
    set_local_machine_dword_value(_pgina_reg_path, 'd73131d7-7af2-47bb-bbf4-4f8583b44962', 0)
    set_local_machine_dword_value(_pgina_reg_path, 'ec3221a6-621f-44ce-b77b-e074298d6b4e', 0)
    set_local_machine_dword_value(_pgina_reg_path, '350047a0-2d0b-4e24-9f99-16cd18d6b142', 0)
    set_local_machine_dword_value(_pgina_reg_path, '98477b3a-830d-4bee-b270-2d7435275f9c', 0)
    set_local_machine_dword_value(_pgina_reg_path, 'ced8d126-9121-4cd2-86de-3d84e4a2625e', 0)
    set_local_machine_dword_value(_pgina_reg_path, '534eaabf-ab1f-4b0e-9b28-dc77f3494a78', 0)


def _configure_plugins_order():
    set_local_machine_multi_str_value(_pgina_reg_path, 'IPluginAuthentication_Order',
                                      [_pgina_authenticate_all_plugin_guid,
                                       _pgina_local_machine_plugin_guid])
    set_local_machine_multi_str_value(_pgina_reg_path, 'IPluginAuthorization_Order',
                                      [_pgina_local_machine_plugin_guid])
    set_local_machine_multi_str_value(_pgina_reg_path, 'IPluginAuthenticationGateway_Order',
                                      [_pgina_single_user_login_plugin_guid,
                                       _pgina_local_machine_plugin_guid])
    set_local_machine_multi_str_value(_pgina_reg_path, 'IPluginChangePassword_Order', [])
    set_local_machine_multi_str_value(_pgina_reg_path, 'IPluginEventNotifications_Order', [])
