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

import base64
import collections
import os
import paramiko
from src.utilities.pipe_shell import plain_shell, interactive_shell
from src.api.pipeline_run import PipelineRun
from urllib.parse import urlparse

DEFAULT_SSH_PORT = 22
DEFAULT_SSH_USER = 'root'

def http_proxy_tunnel_connect(proxy, target, timeout=None):
    import socket
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    sock.connect(proxy)
    cmd_connect = "CONNECT %s:%d HTTP/1.0\r\n\r\n" % target
    sock.sendall(cmd_connect.encode('UTF-8'))
    response = []
    sock.settimeout(2)  # quick hack - replace this with something better performing.
    try:
        # in worst case this loop will take 2 seconds if not response was received (sock.timeout)
        while True:
            chunk = sock.recv(1024)
            if not chunk:  # if something goes wrong
                break
            response.append(chunk.decode('utf-8'))
            if "\r\n\r\n" in chunk.decode('utf-8'):  # we do not want to read too far
                break
    except socket.error as error:
        if "timed out" not in error:
            response = [error]
    response = ''.join(response)
    if "200 connection established" not in response.lower():
        raise RuntimeError("Unable to establish HTTP-Tunnel: %s" % repr(response))
    return sock

def get_conn_info(run_id):
    run_model = PipelineRun.get(run_id)
    if not run_model.is_initialized:
        raise RuntimeError("The specified Run ID #{} is not initialized for the SSH session".format(run_id))
    ssh_url = PipelineRun.get_ssh_url(run_id)
    if not ssh_url:
        raise RuntimeError("Cannot get the SSH proxy endpoint for the specified Run ID #{}".format(run_id))
    ssh_url_parts = urlparse(ssh_url)
    ssh_proxy_host = ssh_url_parts.hostname
    if not ssh_proxy_host:
        raise RuntimeError("Cannot get the SSH proxy hostname from the endpoint {} for the specified Run ID #{}".format(ssh_url, run_id))
    ssh_proxy_port = ssh_url_parts.port
    if not ssh_proxy_port:
        ssh_proxy_port = 80 if ssh_url_parts.scheme == "http" else 443

    run_conn_info = collections.namedtuple('conn_info', 'ssh_proxy ssh_endpoint ssh_pass')
    return run_conn_info(ssh_proxy=(ssh_proxy_host, ssh_proxy_port),
                         ssh_endpoint=(run_model.pod_ip, DEFAULT_SSH_PORT),
                         ssh_pass=run_model.ssh_pass)


def run_ssh_command(channel, command):
    channel.exec_command(command)
    plain_shell(channel)
    return channel.recv_exit_status()

def run_ssh_session(channel):
    channel.invoke_shell()
    interactive_shell(channel)

def run_ssh(run_id, command):
    # Grab the run information from the API to setup the run's IP and EDGE proxy address
    conn_info = get_conn_info(run_id)

    # Initialize Paramiko SSH client
    channel = None
    transport = None
    try:
        socket = http_proxy_tunnel_connect(conn_info.ssh_proxy, conn_info.ssh_endpoint, 5)
        transport = paramiko.Transport(socket)
        transport.start_client()
        # User password authentication, which available only to the OWNER and ROLE_ADMIN users
        try:
            transport.auth_password(DEFAULT_SSH_USER, conn_info.ssh_pass)
        except paramiko.ssh_exception.AuthenticationException:
            # Reraise authentication error to provide more details
            raise PermissionError('Authentication failed for {}@{}'.format(
                DEFAULT_SSH_USER, conn_info.ssh_endpoint[0]))
        channel = transport.open_session()
        # "get_pty" is used for non-interactive commands too
        # This allows to get stdout and stderr in a correct order
        # Otherwise we'll need to combine them somehow
        channel.get_pty()
        if command:
            # Execute command and wait for it's execution
            return run_ssh_command(channel, command)
        else:
            # Open a remote shell
            run_ssh_session(channel)
            return 0
        
    finally:
        if channel:
            channel.close()
        if transport:
            transport.close()
