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
import logging

def debug_log_proxy(proxy_config, proxy_name):
    _proxy_value = None
    _no_proxy_value = None
    if proxy_config and proxy_config.__dict__[proxy_name]:
        _proxy_value = proxy_config.__dict__[proxy_name]
    else:
        _proxy_value = os.getenv(proxy_name + '_proxy', None)
        _no_proxy_value = os.getenv('no_proxy', None)
    logging.debug('Effective {} proxy: {}'.format(proxy_name, _proxy_value))
    logging.debug('Effective no_proxy: {}'.format(_no_proxy_value))

def debug_log_proxies(proxy_config):
    _internal_config = proxy_config.proxies if hasattr(proxy_config, 'proxies') else proxy_config
    debug_log_proxy(_internal_config, 'http')
    debug_log_proxy(_internal_config, 'https')
