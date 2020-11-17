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

import atexit
import platform
from contextlib import closing
import os
import signal
import socket
import subprocess
import time

try:
    from urllib.parse import urlparse  # Python 3
except ImportError:
    from urlparse import urlparse  # Python 2


PROXY_NTLM_PORT_DEFAULT = 10101


class NTLMProxy(object):
    __instance = None

    def __init__(self, proxy_url, proxy_host, proxy_port, port, ntlmaps_bin, domain, user, password):
        if NTLMProxy.__instance is not None:
            raise Exception("This class is a singleton!")
        else:
            NTLMProxy.__instance = self
        self.proxy_url = proxy_url
        self.proxy_host = proxy_host
        self.proxy_port = proxy_port
        self.port = port

        ntlm_aps_cmd = [ntlmaps_bin,
                        "--domain", domain,
                        "--username", user,
                        "--password", password,
                        "--port", str(port),
                        "--downstream-proxy-host", proxy_host,
                        "--downstream-proxy-port", str(proxy_port)]

        with open(os.devnull, 'w') as dev_null:
            if platform.system() == 'Windows':
                # See https://docs.microsoft.com/ru-ru/windows/win32/procthread/process-creation-flags
                CREATE_NO_WINDOW = 0x08000000
                creationflags = CREATE_NO_WINDOW
            else:
                creationflags = 0
            ntlm_aps_proc = subprocess.Popen(ntlm_aps_cmd, stdout=dev_null, stderr=dev_null,
                                             creationflags=creationflags)
            self.proxy_pid = ntlm_aps_proc.pid

    def kill_ntlm_aps(self):
        if self.proxy_pid:
            try:
                os.kill(self.proxy_pid, signal.SIGTERM)
            except:
                pass

    def get_ntlm_aps_local_url(self):
        if self.port:
            return 'http://localhost:' + str(self.port)
        else:
            return None

    def wait_for_ntlm_aps(self, attempts=10):
        ntlm_aps_ready = False
        while not ntlm_aps_ready and attempts != 0:
            with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
                sock.settimeout(1)
                ntlm_aps_ready = sock.connect_ex(('localhost', self.port)) == 0
                attempts = attempts - 1
                time.sleep(1)
        return ntlm_aps_ready

    @staticmethod
    def try_port(port):
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            sock.bind(('0.0.0.0', port))
            result = True
        except:
            result = False
        sock.close()
        return result

    @staticmethod
    def find_free_port(start_from=10000, attempts=100):
        port = start_from
        while attempts != 0:
            if NTLMProxy.try_port(port):
                return port
            attempts = attempts - 1
            port = port + 1
        raise Exception('Unable to find free port after {} attempts'.format(attempts))

    @staticmethod
    def get_proxy(ntlmaps_bin, domain, user, password, downstream_proxy, port=None):
        if NTLMProxy.__instance is not None:
            return NTLMProxy.__instance

        if not port:
            port = NTLMProxy.find_free_port(PROXY_NTLM_PORT_DEFAULT)
        proxy_url = urlparse(downstream_proxy)
        proxy_host = proxy_url.hostname
        proxy_port = proxy_url.port

        if not proxy_host or not proxy_port:
            raise Exception('Cannot get proxy host or port from {}. '
                            'Make sure it is specified in the format: http://HOST:PORT'.format(downstream_proxy))

        proxy_instance = NTLMProxy(proxy_url, proxy_host, proxy_port, port, ntlmaps_bin, domain, user, password)
        _ = atexit.register(proxy_instance.kill_ntlm_aps)

        if not proxy_instance.wait_for_ntlm_aps():
            raise Exception('Failed to connect to the NTLM APS instance on port ' + str(port))

        return proxy_instance
