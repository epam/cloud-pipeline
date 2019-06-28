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
import os
import sys
from time import sleep

import click
import socket
from functools import update_wrapper
try:
    from urllib.parse import urlparse
except ImportError:
     from urlparse import urlparse

from contextlib import closing

from .config import Config
from multiprocessing import Process

from .ntlmaps.main import main

PROXY_NTLM_DEFAULT_PORT = 3218
MAX_PORT_COUNT = 65536
MAX_REP_COUNT = 50
LOCALHOST = '127.0.0.1'


def convert_to_dict(raw_config):
    config = {'api': raw_config.api, 'access_key': raw_config.access_key, 'tz': raw_config.tz}
    if raw_config.proxy:
        config.update({'proxy': raw_config.proxy})
    if raw_config.proxy_ntlm_enabled:
        config.update({'proxy_ntlm_enabled': True})
    if raw_config.http_proxy:
        config.update({'http_proxy': raw_config.http_proxy})
    if raw_config.https_proxy:
        config.update({'https_proxy': raw_config.https_proxy})
    if raw_config.username:
        config.update({'username': raw_config.username})
    if raw_config.password:
        config.update({'password': raw_config.password})
    if raw_config.ntlm_http_proxy:
        config.update({'ntlm_http_proxy': raw_config.ntlm_http_proxy})
    if raw_config.ntlm_https_proxy:
        config.update({'ntlm_https_proxy': raw_config.ntlm_https_proxy})
    if raw_config.proxy_ntlm_domain:
        config.update({'proxy_ntlm_domain': raw_config.proxy_ntlm_domain})
    return config


def launch_ntlm_proxy_if_need(_func=None, quiet_flag_property_name=None):
    def decorator(f):
        @click.pass_context
        def launch_ntlm_proxy_if_need_wrapper(ctx, *args, **kwargs):
            quiet = True
            if quiet_flag_property_name is not None and quiet_flag_property_name in ctx.params:
                quiet = bool(ctx.params[quiet_flag_property_name])

            config = Config.instance()
            if config.proxy_ntlm_enabled:
                domain = config.proxy_ntlm_domain
                ntlm_proxy = NtlmProxy(config.username, config.password, domain, quiet)
                if config.ntlm_http_proxy:
                    config.http_proxy = ntlm_proxy.launch(config.ntlm_http_proxy, 'http')
                if config.ntlm_https_proxy:
                    config.https_proxy = ntlm_proxy.launch(config.ntlm_https_proxy, 'https')
                config.write_to_config_file(convert_to_dict(config))
            return ctx.invoke(f, *args, **kwargs)

        return update_wrapper(launch_ntlm_proxy_if_need_wrapper, f)

    if _func is None:
        return decorator
    else:
        return decorator(_func)


class NtlmProxy(object):

    def __init__(self, username, password, domain, quite=True):
        self.username = username
        self.password = password
        self.domain = domain
        self.quite = quite

    def launch(self, original_proxy, schema):
        port = self.find_free_port(original_proxy)
        url = "%s://%s:%d" % (schema, LOCALHOST, port)

        config = self.build_config(port, original_proxy)
        ntlm_process = Process(target=self.run_ntlmaps, args=(config,))
        ntlm_process.daemon = True
        ntlm_process.start()

        self.wait_for_service_boot_up(port, url)

        return url

    def find_free_port(self, original_proxy):
        port = PROXY_NTLM_DEFAULT_PORT
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
            result = sock.connect_ex((LOCALHOST, port))
            while result == 0 and port < MAX_PORT_COUNT:
                port = port + 1
                result = sock.connect_ex((LOCALHOST, port))
            if result == 0:
                click.echo('Failed to launch NTML proxy for %s: no free port is available' % original_proxy, err=True)
                sys.exit(1)
            if not self.quite:
                click.echo('A free port found %d' % port)
            return port

    @staticmethod
    def wait_for_service_boot_up(port, url):
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
            result = sock.connect_ex((LOCALHOST, port))
            rep = 0
            while result != 0 and rep < MAX_REP_COUNT:
                rep = rep + 1
                sleep(1)
                result = sock.connect_ex((LOCALHOST, port))
            if result != 0:
                click.echo('Failed to establish connection to %s. Exceeded retry count %d' % (url, MAX_REP_COUNT),
                           err=True)
                sys.exit(1)
            return port

    def run_ntlmaps(self, config):
        main(config)

    def build_config(self, port, original_proxy):
        parsed_proxy = urlparse(original_proxy)
        config = []
        config.append('--cmd=True')
        config.append("--port=%d" % port)
        config.append("--proxy-host=%s" % parsed_proxy.hostname)
        config.append("--proxy-port=%d" % parsed_proxy.port)
        config.append("--domain=%s" % self.domain)
        config.append("--username=%s" % self.username)
        config.append("--password=%s" % self.password)
        return config

    @staticmethod
    def write_to_ntlm_config(config, schema):
        config_file = NtlmProxy.config_path(schema)
        with open(config_file, 'w+') as f:
            for item in config:
                f.write("%s\n" % item)
        return config_file

    @staticmethod
    def config_path(schema):
        home = os.path.expanduser("~")
        config_folder = os.path.join(home, '.pipe')
        if not os.path.exists(config_folder):
            os.makedirs(config_folder)
        config_file = os.path.join(config_folder, '%s_ntlm_proxy.cfg' % schema)
        if os.path.isfile(config_file):
            os.remove(config_file)
        return config_file
