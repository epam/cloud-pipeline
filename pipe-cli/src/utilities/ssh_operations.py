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
import random
import select
import socket
import sys
import time

import paramiko
from scp import SCPClient

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


def setup_paramiko_transport(conn_info, retries):
    socket = None
    transport = None
    try:
        socket = http_proxy_tunnel_connect(conn_info.ssh_proxy, conn_info.ssh_endpoint, 5)
        transport = paramiko.Transport(socket)
        transport.start_client()
        return transport
    except Exception as e:
        if retries >= 1:
            retries = retries - 1
            if socket:
                socket.close()
            if transport:
                transport.close()
            return setup_paramiko_transport(conn_info, retries)
        else:
            raise e


def setup_authenticated_paramiko_transport(run_id, retries):
    # Grab the run information from the API to setup the run's IP and EDGE proxy address
    conn_info = get_conn_info(run_id)

    # Initialize Paramiko SSH client
    setup_paramiko_logging()
    transport = setup_paramiko_transport(conn_info, retries)
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
        return transport
    except paramiko.ssh_exception.AuthenticationException:
        # Reraise authentication error to provide more details
        raise RuntimeError('Authentication failed for {}@{}'.format(
            sshuser, conn_info.ssh_endpoint[0]))


def run_ssh_command(channel, command):
    channel.exec_command(command)
    plain_shell(channel)
    return channel.recv_exit_status()


def run_ssh_session(channel):
    channel.invoke_shell()
    interactive_shell(channel)


def run_ssh(run_id, command, retries=10):
    transport = None
    channel = None
    try:
        transport = setup_authenticated_paramiko_transport(run_id, retries)
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


def create_tunnel(run_id, local_port, remote_port, ssh, log_file, log_level, timeout, foreground, retries):
    if foreground:
        if ssh:
            create_foreground_tunnel_with_ssh(run_id, local_port, remote_port, log_file, log_level, retries)
        else:
            create_foreground_tunnel(run_id, local_port, remote_port, log_file, log_level, retries)
    else:
        create_background_tunnel(log_file, timeout)


def configure_passwordless_ssh(run_id, local_port, remote_host, log_file, retries,
                               ssh_config_path, ssh_known_hosts_path,
                               ssh_public_key_path, ssh_private_key_path):
    generate_ssh_keys(log_file, ssh_private_key_path)
    upload_ssh_public_key_to_run(run_id, ssh_public_key_path, retries)
    add_to_ssh_config(ssh_config_path, remote_host, local_port, ssh_private_key_path)
    add_to_ssh_known_hosts(ssh_known_hosts_path, run_id, local_port, log_file, retries)


def deconfigure_passwordless_ssh(run_id, local_port, remote_host, log_file, retries,
                                 ssh_config_path, ssh_known_hosts_path,
                                 ssh_public_key_path, ssh_private_key_path):
    remove_ssh_public_key_from_run(run_id, ssh_public_key_path, retries)
    remove_ssh_keys(ssh_public_key_path, ssh_private_key_path)
    remove_from_ssh_config(ssh_config_path, remote_host)
    remove_from_ssh_known_hosts(ssh_known_hosts_path, local_port, log_file)


def generate_ssh_keys(log_file, ssh_private_key_path):
    generate_ssh_keys_command = ['ssh-keygen', '-t', 'rsa', '-f', ssh_private_key_path, '-N', '', '-q']
    perform_command(generate_ssh_keys_command, log_file)


def remove_ssh_keys(ssh_public_key_path, ssh_private_key_path):
    if os.path.exists(ssh_public_key_path):
        os.remove(ssh_public_key_path)
    if os.path.exists(ssh_private_key_path):
        os.remove(ssh_private_key_path)


def upload_ssh_public_key_to_run(run_id, ssh_public_key_path, retries):
    authorized_keys_path = '/root/.ssh/authorized_keys'
    with open(ssh_public_key_path, 'r') as f:
        ssh_public_key = f.read().strip()
    run_ssh(run_id, 'echo "%s" >> %s' % (ssh_public_key, authorized_keys_path), retries)


def remove_ssh_public_key_from_run(run_id, ssh_public_key_path, retries):
    if os.path.exists(ssh_public_key_path):
        with open(ssh_public_key_path, 'r') as f:
            ssh_public_key = f.read().strip()
        authorized_keys_temp_path = '/root/.ssh/authorized_keys_%s' % random.randint(0, sys.maxsize)
        authorized_keys = '/root/.ssh/authorized_keys'
        run_ssh(run_id, 'grep -v "%s" %s > %s; cp %s %s; rm %s'
                % (ssh_public_key, authorized_keys, authorized_keys_temp_path,
                   authorized_keys_temp_path, authorized_keys,
                   authorized_keys_temp_path),
                retries)


def add_to_ssh_config(ssh_config_path, remote_host, local_port, ssh_private_key_path):
    with open(ssh_config_path, 'r') as f:
        ssh_config = f.read()
    if remote_host in ssh_config:
        remove_from_ssh_config(ssh_config_path, remote_host)
    with open(ssh_config_path, 'a') as f:
        f.write('Host %s\n'
                '    Hostname 127.0.0.1\n'
                '    User root\n'
                '    Port %s\n'
                '    IdentityFile %s\n'
                % (remote_host, local_port, ssh_private_key_path))


def remove_from_ssh_config(ssh_config_path, remote_host):
    with open(ssh_config_path, 'r') as f:
        ssh_config_lines = f.readlines()
    updated_ssh_config_lines = []
    skip_host = False
    for line in ssh_config_lines:
        if line.startswith('Host '):
            if line.startswith('Host %s' % remote_host):
                skip_host = True
            else:
                skip_host = False
        if not skip_host:
            updated_ssh_config_lines.append(line)
    with open(ssh_config_path, 'w') as f:
        f.writelines(updated_ssh_config_lines)


def add_to_ssh_known_hosts(ssh_known_hosts_path, run_id, local_port, log_file, retries):
    run_ssh_public_key_path = '/root/.ssh/id_rsa.pub'
    ssh_known_hosts_temp_path = ssh_known_hosts_path + '_%s' % random.randint(0, sys.maxsize)
    run_scp_download(run_id, run_ssh_public_key_path, ssh_known_hosts_temp_path, retries)
    with open(ssh_known_hosts_temp_path, 'r') as f:
        public_key = f.read().strip()
    os.remove(ssh_known_hosts_temp_path)
    with open(ssh_known_hosts_path, 'a') as f:
        f.write('[127.0.0.1]:%s %s' % (local_port, public_key))
    perform_command(['ssh-keygen', '-H', '-f', ssh_known_hosts_path], log_file)


def remove_from_ssh_known_hosts(ssh_known_hosts_path, local_port, log_file):
    perform_command(['ssh-keygen', '-R', '[127.0.0.1]:%s' % local_port, '-f', ssh_known_hosts_path], log_file)


def perform_command(executable, log_file):
    import subprocess
    import os
    with open(log_file or os.devnull, 'w') as output:
        command_proc = subprocess.Popen(executable, stdout=output, stderr=subprocess.STDOUT, cwd=os.getcwd(),
                                        env=os.environ.copy())
        command_proc.wait()
        if command_proc.returncode != 0:
            raise RuntimeError('Command "%s" exited with return code: %d' % (executable, command_proc.returncode))


def run_scp_upload(run_id, source, destination, retries):
    transport = None
    scp = None
    try:
        transport = setup_authenticated_paramiko_transport(run_id, retries)
        scp = SCPClient(transport)
        scp.put(source, destination)
    finally:
        if scp:
            scp.close()
        if transport:
            transport.close()


def run_scp_download(run_id, source, destination, retries):
    transport = None
    scp = None
    try:
        transport = setup_authenticated_paramiko_transport(run_id, retries)
        scp = SCPClient(transport)
        scp.get(source, destination)
    finally:
        if scp:
            scp.close()
        if transport:
            transport.close()


def create_foreground_tunnel(run_id, local_port, remote_port, log_file, log_level, retries,
                             server_delay=0.0001, tunnel_timeout=5, chunk_size=4096):
    logging.basicConfig(level=log_level or logging.ERROR)
    conn_info = get_conn_info(run_id)
    proxy_endpoint = (os.getenv('CP_CLI_TUNNEL_PROXY_HOST', conn_info.ssh_proxy[0]),
                      int(os.getenv('CP_CLI_TUNNEL_PROXY_PORT', conn_info.ssh_proxy[1])))
    target_endpoint = (os.getenv('CP_CLI_TUNNEL_TARGET_HOST', conn_info.ssh_endpoint[0]),
                       remote_port)
    logging.info('Initializing tunnel %s:pipeline-%s:%s...', local_port, run_id, remote_port)
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind(('0.0.0.0', local_port))
    server_socket.listen(5)
    inputs = []
    channel = {}
    configure_graceful_exiting()
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


def create_foreground_tunnel_with_ssh(run_id, local_port, remote_port, log_file, log_level, retries):
    import platform
    if platform.system() == 'Windows':
        import click
        click.echo('Passwordless ssh configuration is not support on Windows.', err=True)
        sys.exit(1)
    remote_host = 'pipeline-%s' % run_id
    ssh_config_path = os.path.expanduser('~/.ssh/config')
    ssh_known_hosts_path = os.path.expanduser('~/.ssh/known_hosts')
    ssh_keys_path = os.path.expanduser('~/.pipe/.ssh')
    ssh_private_key_name = 'pipeline-%s-%s-%s' % (run_id, int(time.time()), random.randint(0, sys.maxsize))
    ssh_private_key_path = os.path.join(ssh_keys_path, ssh_private_key_name)
    ssh_public_key_path = ssh_private_key_path + '.pub'
    if not os.path.exists(ssh_keys_path):
        os.makedirs(ssh_keys_path)
    try:
        configure_passwordless_ssh(run_id, local_port, remote_host, log_file, retries,
                                   ssh_config_path, ssh_known_hosts_path,
                                   ssh_public_key_path, ssh_private_key_path)
        create_foreground_tunnel(run_id, local_port, remote_port, log_file, log_level, retries)
    finally:
        deconfigure_passwordless_ssh(run_id, local_port, remote_host, log_file, retries,
                                     ssh_config_path, ssh_known_hosts_path,
                                     ssh_public_key_path, ssh_private_key_path)


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


def configure_graceful_exiting():
    def throw_keyboard_interrupt(signum, frame):
        logging.info('Killed...')
        raise KeyboardInterrupt()

    import signal
    signal.signal(signal.SIGTERM, throw_keyboard_interrupt)
