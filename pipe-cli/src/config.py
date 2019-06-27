# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import os
import sys

import click
import pytz
import tzlocal
from pypac import api as PacAPI
from pypac.resolver import ProxyResolver as PacProxyResolver
from urlparse import urlparse

from .utilities import time_zone_param_type

PROXY_TYPE_PAC = "pac"
PROXY_PAC_DEFAULT_URL = "https://google.com"


class ConfigNotFoundError(Exception):
    def __init__(self):
        super(ConfigNotFoundError, self).__init__('Unable to locate configuration or it is incomplete. '
                                                  'You can configure pipe by running "pipe configure"')


class Config(object):
    """Provides a wrapper for a pipe command configuration"""

    def __init__(self):
        self.api = os.environ.get('API')
        self.access_key = os.environ.get('API_TOKEN')
        self.tz = time_zone_param_type.LOCAL_ZONE
        self.proxy = None
        self.http_proxy = None
        self.https_proxy = None
        self.username = None
        self.password = None
        self.proxy_ntlm_enabled = None
        self.ntlm_http_proxy = None
        self.ntlm_https_proxy = None
        self.proxy_ntlm_domain = None
        if self.api and self.access_key:
            return

        config_file = Config.config_path()
        if os.path.exists(config_file):
            self.read_from_config_file(config_file)
        else:
            raise ConfigNotFoundError()

    def is_proxy_enabled(self):
        return self.proxy or self.proxy_ntlm_enabled

    def resolve_proxy(self, target_url=None):
        if not self.proxy:
            if self.proxy_ntlm_enabled:
                proxies = {}
                if self.http_proxy:
                    proxies.update({'http': self.http_proxy})
                    if not self.https_proxy:
                        proxies.update({'https': self.http_proxy})
                        return proxies
                if self.https_proxy:
                    proxies.update({'https': self.https_proxy})
                    if not self.http_proxy:
                        proxies.update({'http': self.https_proxy})
                        return proxies
                return proxies
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
        else:
            return {
                'http': self.proxy,
                'https': self.proxy,
                'ftp': self.proxy
            }

    @classmethod
    def store(cls, access_key, api, timezone, proxy, proxy_ntlm, proxy_ntlm_user, proxy_ntlm_pass, proxy_ntlm_domain):
        config = {'api': api, 'access_key': access_key, 'tz': timezone}
        if proxy_ntlm:
            config.update({'proxy_ntlm_enabled': True})
            http_proxy, https_proxy = cls.init_original_proxy(proxy)
            if http_proxy:
                cls.validate_url(http_proxy)
                config.update({'ntlm_http_proxy': http_proxy})
            if https_proxy:
                cls.validate_url(https_proxy)
                config.update({'ntlm_https_proxy': https_proxy})
            if not proxy_ntlm_user:
                click.echo('Username is required for NTLM proxy auth', err=True)
                sys.exit(1)
            config.update({'username': proxy_ntlm_user})
            if not proxy_ntlm_pass:
                click.echo('Password is required for NTLM proxy auth', err=True)
                sys.exit(1)
            config.update({'password': proxy_ntlm_pass})
            if not proxy_ntlm_domain:
                click.echo('NT domain is required for NTLM proxy auth', err=True)
                sys.exit(1)
            config.update({'proxy_ntlm_domain': proxy_ntlm_domain})
        else:
            config.update({'proxy': proxy})

        cls.write_to_config_file(config)

    @classmethod
    def write_to_config_file(cls, config):
        config_file = cls.config_path()
        with open(config_file, 'w+') as config_file_stream:
            json.dump(config, config_file_stream)

    def read_from_config_file(self, config_file):
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
            if 'http_proxy' in data:
                self.http_proxy = data['http_proxy']
            if 'https_proxy' in data:
                self.https_proxy = data['https_proxy']
            if 'username' in data:
                self.username = data['username']
            if 'password' in data:
                self.password = data['password']
            if 'proxy_ntlm_enabled' in data:
                self.proxy_ntlm_enabled = data['proxy_ntlm_enabled']
            if 'ntlm_http_proxy' in data:
                self.ntlm_http_proxy = data['ntlm_http_proxy']
            if 'ntlm_https_proxy' in data:
                self.ntlm_https_proxy = data['ntlm_https_proxy']
            if 'proxy_ntlm_domain' in data:
                self.proxy_ntlm_domain = data['proxy_ntlm_domain']

    @classmethod
    def config_path(cls):
        home = os.path.expanduser("~")
        config_folder = os.path.join(home, '.pipe')
        if not os.path.exists(config_folder):
            os.makedirs(config_folder)
        config_file = os.path.join(config_folder, 'config.json')
        return config_file

    @classmethod
    def instance(cls):
        return cls()

    def timezone(self):
        if self.tz == 'utc':
            return pytz.utc
        return tzlocal.get_localzone()

    @classmethod
    def init_original_proxy(cls, proxy):
        if proxy:
            if proxy.startswith("https"):
                return None, proxy
            else:
                return proxy, None
        http_proxy = os.environ.get('http_proxy')
        https_proxy = os.environ.get('https_proxy')
        if not http_proxy and not https_proxy:
            click.echo('Failed to configure NTLM proxy: original proxy is not specified. Please, add proxy URL to '
                       '--proxy option or specify at least one environment variable "http_proxy" or "https_proxy"',
                       err=True)
            sys.exit(1)
        return http_proxy, https_proxy

    @staticmethod
    def validate_url(url):
        parsed_url = urlparse(url)
        if not parsed_url.port or not parsed_url.hostname or not parsed_url.scheme:
            click.echo('Failed to parse URL %s' % url, err=True)
            sys.exit(1)
