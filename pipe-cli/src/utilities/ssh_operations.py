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
import click
import collections
import logging
import os
import paramiko
from src.utilities.pipe_shell import plain_shell, interactive_shell
from src.api.pipeline_run import PipelineRun
from urllib.parse import urlparse

DEFAULT_SSH_PORT = 22
DEFAULT_SSH_USER = 'root'

# For certain cases - we'll need to use "pipe ssh" as an OpenSSH replacement
# Some of the OpenSSH client's options are not relevant to the Cloud Pipeline
# So we just ignore ("swallowing") them
def ssh_swallowed_options_flags():
    return ['-1', '-2', '-4', '-6', '-A', '-a', '-C', '-f', 
            '-g', '-K', '-k', '-M', '-N', '-n', '-q', '-s',
            '-T', '-t', '-V', '-X', '-x', '-Y', '-y']
def ssh_swallowed_options_values():
    return ['-b', '-c', '-D', '-F', '-I', '-L', '-m',
            '-O', '-R', '-S ', '-W', '-w', '-o', '-e']

def setup_paramiko_logging():
    # Log to "null" file by default
    paramiko_log_file = os.getenv("PARAMIKO_LOG_FILE", os.devnull)
    paramiko_log_level_name = os.getenv("PARAMIKO_LOG_LEVEL", "ERROR")
    if hasattr(logging, paramiko_log_level_name):
        paramiko_log_level = getattr(logging, paramiko_log_level_name)
    else:
        paramiko_log_level = logging.ERROR

    if paramiko_log_file:
        paramiko.util.log_to_file(paramiko_log_file, paramiko_log_level)

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

def is_run_id_int(run_id):
    try:
        run_id = int(run_id)
    except:
        return False

    return True

def get_conn_info(run_id, port_number=None, tunnel_proxy=None):
    ssh_user = DEFAULT_SSH_USER
    ssh_proxy_host = None
    ssh_proxy_port = None
    if not port_number:
        port_number = DEFAULT_SSH_PORT

    # Check run_id is an int or a job name
    if not is_run_id_int(run_id):
        # If run_id is not an integer - it can be:
        # - A job name (e.g. pipeline-12345) 
        # - May contain a username (e.g. user@12345)
        # - Or both user@pipeline-12345
        
        # First check if the username is specified in the run_id via '@' (e.g. user@12345)
        if '@' in run_id:
            run_id_parts = run_id.split('@')
            ssh_user = run_id_parts[0]
            run_id = run_id_parts[1]

        # Double check that a run_id is int or not (as it may still look like pipeline-12345)
        if not is_run_id_int(run_id):
            # If not ing - extract the last part and check again. If nothing succeeds - raise and fail
            run_id = run_id.split('-')[-1]
            if not is_run_id_int(run_id):
                raise RuntimeError("Cannot determing the real Run ID from {}".format(run_id))

    run_model = PipelineRun.get(run_id)
    if not run_model.is_initialized:
        raise RuntimeError("The specified Run ID #{} is not initialized for the SSH session".format(run_id))
    if tunnel_proxy:
        ssh_proxy_parts = tunnel_proxy.split(':')
        ssh_proxy_host = ssh_proxy_parts[0]
        ssh_proxy_port = int(ssh_proxy_parts[1] if len(ssh_proxy_parts) > 1 else 443)
    else:
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

    run_conn_info = collections.namedtuple('conn_info', 'ssh_proxy ssh_endpoint ssh_pass ssh_user')
    return run_conn_info(ssh_proxy=(ssh_proxy_host, ssh_proxy_port),
                         ssh_endpoint=(run_model.pod_ip, port_number),
                         ssh_pass=run_model.ssh_pass,
                         ssh_user=ssh_user)

def run_ssh_command(channel, command):
    channel.exec_command(command)
    plain_shell(channel)
    return channel.recv_exit_status()

def run_ssh_session(channel):
    channel.invoke_shell()
    interactive_shell(channel)

def run_ssh(run_id, command, tunnel_proxy=None, user=None, identity_file=None, port_number=None, verbose=False):
    if verbose:
        click.echo('Run ID:   {}'.format(run_id))
        click.echo('Command:  {}'.format(command))
        click.echo('Identity: {}'.format(None))
        os.environ['PARAMIKO_LOG_LEVEL'] = 'DEBUG'
        if not os.getenv('PARAMIKO_LOG_FILE'):
            os.environ['PARAMIKO_LOG_LEVEL'] = 'ssh_session.log'

    # Grab the run information from the API to setup the run's IP and EDGE proxy address
    conn_info = get_conn_info(run_id, port_number=port_number, tunnel_proxy=tunnel_proxy)

    # If a use is specific in the host string (e.g. user1@run_id) and it is also set explicitly
    # then we prefer the latter
    ssh_user = user if user else conn_info.ssh_user

    if verbose:
        click.echo('SSH Proxy:    {}'.format(conn_info.ssh_proxy))
        click.echo('SSH Endpoint: {}'.format(conn_info.ssh_endpoint))
        click.echo('SSH User:     {}'.format(ssh_user))

    # Initialize Paramiko SSH client
    channel = None
    transport = None
    try:
        socket = http_proxy_tunnel_connect(conn_info.ssh_proxy, conn_info.ssh_endpoint, 5)
        setup_paramiko_logging()
        transport = paramiko.Transport(socket)
        transport.start_client()

        try:
            if identity_file:
                # If the private key file is explicitly specified - try to use it
                identity_key = paramiko.RSAKey.from_private_key_file(identity_file)
                transport.auth_publickey(ssh_user, identity_key)
            else:
                # Otherwise - use password authentication, which is available only to the OWNER and ROLE_ADMIN users
                try:
                    transport.auth_password(ssh_user, conn_info.ssh_pass)
                except paramiko.ssh_exception.AuthenticationException:
                    # if the "secret" has failed - there are also cases when the pass will be equal to the username
                    transport.auth_password(ssh_user, ssh_user)
        except paramiko.ssh_exception.AuthenticationException:
            # Reraise authentication error to provide more details
            raise PermissionError('Authentication failed for {}@{}'.format(
                ssh_user, conn_info.ssh_endpoint[0]))
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
