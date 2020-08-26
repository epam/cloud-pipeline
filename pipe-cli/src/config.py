# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

from functools import update_wrapper
import base64
import click
import json
import jwt
import os
import pytz
import sys
import tzlocal
import platform
from pypac import api as PacAPI
from pypac.resolver import ProxyResolver as PacProxyResolver

from .utilities import time_zone_param_type, network_utilities
from .utilities.access_token_validation import check_token


OWNER_ONLY_PERMISSION = 0o600
PROXY_TYPE_PAC = "pac"
PROXY_PAC_DEFAULT_URL = "https://google.com"
ALL_ERRORS = Exception


def is_frozen():
    return getattr(sys, 'frozen', False)


def silent_print_config_info():
    config = Config.instance(raise_config_not_found_exception=False)
    if config is not None and config.initialized:
        click.echo()
        config.validate(print_info=True)


class ConfigNotFoundError(Exception):
    def __init__(self):
        super(ConfigNotFoundError, self).__init__('Unable to locate configuration or it is incomplete. '
                                                  'You can configure pipe by running "pipe configure"')


class ProxyInvalidConfig(Exception):
    def __init__(self, details):
        super(ProxyInvalidConfig, self).__init__('Invalid proxy configuration is provided: {}'.format(details))


class Config(object):
    """Provides a wrapper for a pipe command configuration"""

    __USER_TOKEN__ = None

    def __init__(self, raise_config_not_found_exception=True):
        self.initialized = False
        self.api = os.environ.get('API')
        self.access_key = os.environ.get('API_TOKEN')
        self.tz = time_zone_param_type.LOCAL_ZONE
        self.proxy = None
        self.proxy_ntlm = None
        self.proxy_ntlm_user = None
        self.proxy_ntlm_domain = None
        self.proxy_ntlm_pass = None

        if self.api and self.access_key:
            self.initialized = True
            return

        config_file = Config.config_path()
        if os.path.exists(config_file):
            with open(config_file, 'r') as config_file_stream:
                data = json.load(config_file_stream)
                if 'api' in data:
                    self.api = data['api']
                if 'access_key' in data:
                    self.access_key = data['access_key']
                if 'tz' in data:
                    self.tz = data['tz']
                if 'proxy' in data:
                    self.proxy = data['proxy']
                if 'proxy_ntlm' in data:
                    self.proxy_ntlm = data['proxy_ntlm']
                if 'proxy_ntlm_user' in data:
                    self.proxy_ntlm_user = data['proxy_ntlm_user']
                if 'proxy_ntlm_domain' in data:
                    self.proxy_ntlm_domain = data['proxy_ntlm_domain']
                if 'proxy_ntlm_pass' in data:
                    try:
                        self.proxy_ntlm_pass = Config.decode_password(data['proxy_ntlm_pass'])
                    except:
                        self.proxy_ntlm_pass = None
                if self.api and self.access_key:
                    self.initialized = True
                if 'codec' in data:
                    self.change_encoding(data['codec'])
        elif raise_config_not_found_exception:
            raise ConfigNotFoundError()
        self.validate_pac_proxy(self.proxy)

    def validate(self, print_info=False):
        check_token(self.access_key, self.tz, print_info=print_info)

    @classmethod
    def validate_pac_proxy(cls, proxy):
        if proxy and str(proxy).lower() == PROXY_TYPE_PAC and platform.system() != 'Windows':
            click.echo('"pac" (Proxy Auto Configuration) is not supported in the non-Windows environment. '
                       'Please set the proxy address explicitly or keep it empty (e.g. --proxy "")', err=True)
            exit(1)

    @classmethod
    def validate_access_token(cls, _func=None, quiet_flag_property_name=None):
        def decorator(f):
            @click.pass_context
            def validate_access_token_wrapper(ctx, *args, **kwargs):
                skip_validation = False
                if quiet_flag_property_name is not None and quiet_flag_property_name in ctx.params:
                    skip_validation = bool(ctx.params[quiet_flag_property_name])
                if not skip_validation:
                    config = Config.instance(raise_config_not_found_exception=False)
                    if config is not None and config.initialized:
                        config.validate()
                return ctx.invoke(f, *args, **kwargs)
            return update_wrapper(validate_access_token_wrapper, f)
        if _func is None:
            return decorator
        else:
            return decorator(_func)

    def resolve_proxy(self, target_url=None):
        if not self.proxy:
            return None
        elif self.proxy == PROXY_TYPE_PAC:
            pac_file = PacAPI.get_pac()
            if not pac_file:
                return None
            proxy_resolver = PacProxyResolver(pac_file)
            url_to_resolve = target_url
            if not url_to_resolve and self.api:
                url_to_resolve = self.api
            if not url_to_resolve:
                url_to_resolve = PROXY_PAC_DEFAULT_URL

            return proxy_resolver.get_proxy_for_requests(url_to_resolve)
        elif self.proxy_ntlm:
            ntlm_proxy = network_utilities.NTLMProxy.get_proxy(self.build_ntlm_module_path(),
                                                               self.proxy_ntlm_domain,
                                                               self.proxy_ntlm_user,
                                                               self.proxy_ntlm_pass,
                                                               self.proxy)
            ntlm_aps_proxy_url = ntlm_proxy.get_ntlm_aps_local_url()
            return {'http': ntlm_aps_proxy_url,
                    'https': ntlm_aps_proxy_url,
                    'ftp': ntlm_aps_proxy_url}
        else:
            return {'http': self.proxy,
                    'https': self.proxy,
                    'ftp': self.proxy}

    @classmethod
    def get_base_source_dir(cls):
        return sys._MEIPASS if is_frozen() else os.path.dirname(os.path.abspath(__file__))

    def build_inner_module_path(self, module):
        # Setup pipe executable path
        # Both frozen and plain distributions: https://stackoverflow.com/a/42615559
        pipe_path = self.get_base_source_dir()
        return os.path.join(pipe_path, module)

    def build_ntlm_module_path(self):
        return self.build_inner_module_path("ntlmaps/ntlmaps")

    @classmethod
    def store(cls, access_key, api, timezone, proxy,
              proxy_ntlm, proxy_ntlm_user, proxy_ntlm_domain, proxy_ntlm_pass, codec):
        check_token(access_key, timezone)
        if proxy == PROXY_TYPE_PAC and proxy_ntlm:
            raise ProxyInvalidConfig('NTLM proxy authentication cannot be used for the PAC proxy type'
                                     'Remove the NTLM parameters or change the PAC to the proxy URL')
        if proxy_ntlm and not is_frozen():
            raise ProxyInvalidConfig('NTLM proxy authentication is supported only for prebuilt CLI binaries.')
        if proxy_ntlm_pass:
            click.secho('Warning: NTLM proxy user password will be stored unencrypted.', fg='yellow')
        cls.validate_pac_proxy(proxy)
        config = {'api': api,
                  'access_key': access_key,
                  'tz': timezone,
                  'proxy': proxy,
                  'proxy_ntlm': proxy_ntlm,
                  'proxy_ntlm_user': proxy_ntlm_user,
                  'proxy_ntlm_domain': proxy_ntlm_domain,
                  'proxy_ntlm_pass': cls.encode_password(proxy_ntlm_pass),
                  'codec': codec
                  }
        config_file = cls.config_path()
        # create file
        with open(config_file, 'w+'):
            os.utime(config_file, None)
        # set permissions
        os.chmod(config_file, OWNER_ONLY_PERMISSION)
        # save
        with open(config_file, 'w+') as config_file_stream:
            json.dump(config, config_file_stream)

    @classmethod
    def change_encoding(cls, codec):
        if codec:
            try:
                reload(sys)
                sys.setdefaultencoding(codec)
            except NameError:
                pass

    @classmethod
    def config_path(cls):
        home = os.path.expanduser("~")
        config_folder = os.path.join(home, '.pipe')
        if not os.path.exists(config_folder):
            os.makedirs(config_folder)
        config_file = os.path.join(config_folder, 'config.json')
        return config_file

    @classmethod
    def get_string_from_base64(cls, data):
        if isinstance(data, bytes):
            return data.decode(sys.getdefaultencoding())
        return data

    @classmethod
    def encode_password(cls, raw_password):
        if not raw_password:
            return raw_password
        data = base64.b64encode(raw_password.encode(sys.getdefaultencoding()))
        return cls.get_string_from_base64(data)

    @classmethod
    def decode_password(cls, encoded_password):
        if not encoded_password:
            return encoded_password
        decoded = base64.b64decode(encoded_password.encode(sys.getdefaultencoding()))
        return cls.get_string_from_base64(decoded)

    @classmethod
    def instance(cls, raise_config_not_found_exception=True):
        return cls(raise_config_not_found_exception)

    def get_current_user(self):
        token = self.get_token()
        if not token:
            raise RuntimeError('Access token is not specified. Cannot get user info.')
        user_info = jwt.decode(token, verify=False)
        if 'sub' in user_info:
            return user_info['sub']
        raise RuntimeError('User information is not specified to access token is invalid.')

    def timezone(self):
        if self.tz == 'utc':
            return pytz.utc
        return tzlocal.get_localzone()

    def get_token(self):
        if self.__USER_TOKEN__:
            return self.__USER_TOKEN__
        return self.access_key
