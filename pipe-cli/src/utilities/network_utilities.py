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

PROXY_NTLM_PID = None
PROXY_NTLM_PORT = None
PROXY_NTLM_PORT_DEFAULT = 10101

def kill_ntlm_aps():
    global PROXY_NTLM_PID
    if PROXY_NTLM_PID:
        try:
            os.kill(PROXY_NTLM_PID, signal.SIGTERM)
        except:
            pass

def get_ntlm_aps_local_url():
    global PROXY_NTLM_PORT
    if PROXY_NTLM_PORT:
        return 'http://localhost:' + str(PROXY_NTLM_PORT)
    else:
        return None

def wait_for_ntlm_aps(port, attempts=10):
    ntlm_aps_ready = False
    while not ntlm_aps_ready and attempts != 0:
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
            sock.settimeout(1)
            ntlm_aps_ready = sock.connect_ex(('localhost',port)) == 0
            attempts = attempts - 1
            time.sleep(1)
    return ntlm_aps_ready
    

def start_ntlm_aps(ntlmaps_bin, domain, user, password, downstream_proxy, port=None):
    global PROXY_NTLM_PID
    global PROXY_NTLM_PORT

    if PROXY_NTLM_PID:
        return get_ntlm_aps_local_url()

    if not port:
        PROXY_NTLM_PORT = find_free_port(PROXY_NTLM_PORT_DEFAULT)

    proxy_url = urlparse(downstream_proxy)
    proxy_host = proxy_url.hostname
    proxy_port = proxy_url.port

    if not proxy_host or not proxy_port:
        raise Exception('Cannot get proxy host or port from {}. Make sure it is sepcified in the format: http://HOST:PORT'.format(downstream_proxy))

    ntlm_aps_cmd = [ ntlmaps_bin,
                        "--domain", domain,
                        "--username", user,
                        "--password", password,
                        "--port", str(PROXY_NTLM_PORT),
                        "--downstream-proxy-host", proxy_host,
                        "--downstream-proxy-port", str(proxy_port) ]

    with open(os.devnull, 'w') as dev_null:
        ntlm_aps_proc = subprocess.Popen(ntlm_aps_cmd, stdout=dev_null, stderr=dev_null)
        PROXY_NTLM_PID = ntlm_aps_proc.pid
        
    _ = atexit.register(kill_ntlm_aps)

    if not wait_for_ntlm_aps(PROXY_NTLM_PORT):
        raise Exception('Failed to connect to the NTLM APS instance on port ' + str(PROXY_NTLM_PORT))

    return get_ntlm_aps_local_url()
    

def try_port(port):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    result = False
    try:
        sock.bind(('0.0.0.0', port))
        result = True
    except:
        result = False
    sock.close()
    return result

def find_free_port(start_from=10000, attempts=100):
    port = start_from
    while attempts != 0:
        if try_port(port):
            return port
        attempts = attempts - 1
        port = port + 1
    raise Exception('Unable to find free port after {} attempts'.format(attempts))
