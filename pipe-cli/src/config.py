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
import pytz
import sys
import tzlocal
from pypac import api as PacAPI
from pypac.resolver import ProxyResolver as PacProxyResolver

from .utilities import time_zone_param_type, network_utilities

# Setup pipe executable path 
# Both frozen and plain distributions: https://stackoverflow.com/a/42615559
if getattr(sys, 'frozen', False):
    PIPE_PATH = sys._MEIPASS
else:
    PIPE_PATH = os.path.dirname(os.path.abspath(__file__))

PROXY_TYPE_PAC = "pac"
PROXY_PAC_DEFAULT_URL = "https://google.com"

PROXY_NTLM_APS_PATH = os.path.join(PIPE_PATH, "ntlmaps/ntlmaps")

class ConfigNotFoundError(Exception):
    def __init__(self):
        super(ConfigNotFoundError, self).__init__('Unable to locate configuration or it is incomplete. '
                                                  'You can configure pipe by running "pipe configure"')

class ProxyInvalidConfig(Exception):
    def __init__(self, details):
        super(ProxyInvalidConfig, self).__init__('Invalid proxy configuration is provided: {}'.format(details))


class Config(object):
    """Provides a wrapper for a pipe command configuration"""

    def __init__(self):
        self.api = os.environ.get('API')
        self.access_key = os.environ.get('API_TOKEN')
        self.tz = time_zone_param_type.LOCAL_ZONE
        self.proxy = None
        self.proxy_ntlm = None
        self.proxy_ntlm_user = None
        self.proxy_ntlm_domain = None
        self.proxy_ntlm_pass = None

        if self.api and self.access_key:
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
                        self.proxy_ntlm_pass = data['proxy_ntlm_pass']
                    except:
                        self.proxy_ntlm_pass = None
        else:
            raise ConfigNotFoundError()

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
            ntlm_aps_proxy_url = network_utilities.start_ntlm_aps(PROXY_NTLM_APS_PATH,
                                                                self.proxy_ntlm_domain,
                                                                self.proxy_ntlm_user,
                                                                self.proxy_ntlm_pass,
                                                                self.proxy)
            return {'http': ntlm_aps_proxy_url,
                    'https': ntlm_aps_proxy_url,
                    'ftp': ntlm_aps_proxy_url}
        else:
            return {'http': self.proxy,
                    'https': self.proxy,
                    'ftp': self.proxy}

    @classmethod
    def store(cls, access_key, api, timezone, proxy, 
              proxy_ntlm, proxy_ntlm_user, proxy_ntlm_domain, proxy_ntlm_pass):

        if proxy == PROXY_TYPE_PAC and proxy_ntlm:
            raise ProxyInvalidConfig('NTLM proxy authentication cannot be used for the PAC proxy type'
                                     'Remove the NTLM parameters or change the PAC to the proxy URL')

        config = {'api': api, 
                  'access_key': access_key, 
                  'tz': timezone, 
                  'proxy': proxy, 
                  'proxy_ntlm': proxy_ntlm, 
                  'proxy_ntlm_user': proxy_ntlm_user, 
                  'proxy_ntlm_domain': proxy_ntlm_domain,
                  'proxy_ntlm_pass': proxy_ntlm_pass }
        config_file = cls.config_path()

        with open(config_file, 'w+') as config_file_stream:
            json.dump(config, config_file_stream)

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
