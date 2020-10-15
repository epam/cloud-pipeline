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
import logging
import os
import select
import socket
import sys
import time

import paramiko

from src.config import is_frozen
from src.utilities.pipe_shell import plain_shell, interactive_shell
from src.api.pipeline_run import PipelineRun
from src.api.preferenceapi import PreferenceAPI
from urllib.parse import urlparse

DEFAULT_SSH_PORT = 22
DEFAULT_SSH_USER = 'root'

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
            response = [str(error)]
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

    run_conn_info = collections.namedtuple('conn_info', 'ssh_proxy ssh_endpoint ssh_pass owner')
    return run_conn_info(ssh_proxy=(ssh_proxy_host, ssh_proxy_port),
                         ssh_endpoint=(run_model.pod_ip, DEFAULT_SSH_PORT),
                         ssh_pass=run_model.ssh_pass,
                         owner=run_model.owner)


def run_ssh_command(channel, command):
    channel.exec_command(command)
    plain_shell(channel)
    return channel.recv_exit_status()


def run_ssh_session(channel):
    channel.invoke_shell()
    interactive_shell(channel)


def create_tunnel(run_id, local_port, remote_port, log_file, log_level, timeout, foreground,
                  server_delay=0.0001, tunnel_timeout=5, chunk_size=4096):
    if foreground:
        server_tunnel(run_id, local_port, remote_port, log_file, log_level, server_delay, tunnel_timeout, chunk_size)
    else:
        create_background_tunnel(log_file, timeout)


def server_tunnel(run_id, local_port, remote_port, log_file, log_level, server_delay, tunnel_timeout, chunk_size):
    logging.basicConfig(level=log_level or logging.ERROR)
    conn_info = get_conn_info(run_id)
    proxy_endpoint = (os.getenv('CP_CLI_TUNNEL_PROXY_HOST', conn_info.ssh_proxy[0]),
                      int(os.getenv('CP_CLI_TUNNEL_PROXY_PORT', conn_info.ssh_proxy[1])))
    target_endpoint = (conn_info.ssh_endpoint[0], remote_port)
    logging.info('Initializing tunnel %s:pipeline-%s:%s...', local_port, run_id, remote_port)
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind(('0.0.0.0', local_port))
    server_socket.listen(5)
    inputs = []
    channel = {}
    logging.info('Serving tunnel...')
    try:
        inputs.append(server_socket)
        while True:
            time.sleep(server_delay)
            logging.info('Waiting for connections...')
            inputs_ready, _, _ = select.select(inputs, [], [])
            for input in inputs_ready:
                if input == server_socket:
                    try:
                        logging.info('Initializing client connection...')
                        client_socket, address = server_socket.accept()
                    except KeyboardInterrupt:
                        raise
                    except:
                        logging.exception('Cannot establish client connection')
                        break
                    try:
                        logging.info('Initializing tunnel connection...')
                        tunnel_socket = http_proxy_tunnel_connect(proxy_endpoint, target_endpoint, tunnel_timeout)
                    except KeyboardInterrupt:
                        raise
                    except:
                        logging.exception('Cannot establish tunnel connection')
                        client_socket.close()
                        break
                    inputs.append(client_socket)
                    inputs.append(tunnel_socket)
                    channel[client_socket] = tunnel_socket
                    channel[tunnel_socket] = client_socket
                    break

                logging.debug('Reading data...')
                data = input.recv(chunk_size)
                if data:
                    logging.debug('Writing data...')
                    channel[input].send(data)
                else:
                    logging.info('Closing client and tunnel connections...')
                    out = channel[input]
                    inputs.remove(input)
                    inputs.remove(out)
                    channel[out].close()
                    channel[input].close()
                    del channel[out]
                    del channel[input]
                    break
    except KeyboardInterrupt:
        logging.info('Interrupted...')
    except:
        logging.exception('Errored...')
        sys.exit(1)
    finally:
        logging.info('Closing all sockets...')
        for input in inputs:
            input.close()
        logging.info('Exiting...')


def create_background_tunnel(log_file, timeout):
    import subprocess
    import os
    import platform
    with open(log_file or os.devnull, 'w') as output:
        if platform.system() == 'Windows':
            # See https://docs.microsoft.com/ru-ru/windows/win32/procthread/process-creation-flags
            DETACHED_PROCESS = 0x00000008
            CREATE_NEW_PROCESS_GROUP = 0x00000200
            creationflags = DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
        else:
            creationflags = 0
        executable = sys.argv + ['-f'] if is_frozen() else [sys.executable] + sys.argv + ['-f']
        tunnel_proc = subprocess.Popen(executable, stdout=output, stderr=subprocess.STDOUT, cwd=os.getcwd(),
                                       env=os.environ.copy(), creationflags=creationflags)
        time.sleep(timeout / 1000)
        if tunnel_proc.poll() is not None:
            import click
            click.echo('Failed to serve tunnel in background. Tunnel command exited with return code: %d'
                       % tunnel_proc.returncode, err=True)
            sys.exit(1)


def run_ssh(run_id, command):
    # Grab the run information from the API to setup the run's IP and EDGE proxy address
    conn_info = get_conn_info(run_id)

    # Initialize Paramiko SSH client
    channel = None
    transport = None
    try:
        socket = http_proxy_tunnel_connect(conn_info.ssh_proxy, conn_info.ssh_endpoint, 5)
        setup_paramiko_logging()
        transport = paramiko.Transport(socket)
        transport.start_client()
        # User password authentication, which available only to the OWNER and ROLE_ADMIN users
        sshpass = conn_info.ssh_pass
        sshuser = DEFAULT_SSH_USER
        ssh_default_root_user_enabled = PreferenceAPI.get_preference('system.ssh.default.root.user.enabled')
        if ssh_default_root_user_enabled is not None and ssh_default_root_user_enabled.value.lower() != "true":
            # split owner by @ in case it represented by email address
            owner_user_name = conn_info.owner.split("@")[0]
            sshpass = owner_user_name
            sshuser = owner_user_name
        try:
            transport.auth_password(sshuser, sshpass)
        except paramiko.ssh_exception.AuthenticationException:
            # Reraise authentication error to provide more details
            raise PermissionError('Authentication failed for {}@{}'.format(
                sshuser, conn_info.ssh_endpoint[0]))
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
