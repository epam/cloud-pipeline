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
import stat
import sys
import time

import paramiko
from scp import SCPClient

from src.config import is_frozen
from src.utilities.pipe_shell import plain_shell, interactive_shell
from src.utilities.platform_utilities import is_windows, is_wsl
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

def http_proxy_tunnel_connect(proxy, target, timeout=None, retries=None):
    timeout = timeout or None
    retries = retries or 0
    sock = None
    try:
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
        sock.settimeout(timeout)
        return sock
    except KeyboardInterrupt:
        raise
    except:
        if retries >= 1:
            if sock:
                sock.close()
            return http_proxy_tunnel_connect(proxy, target, timeout=timeout, retries=retries - 1)
        else:
            raise

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
    sock = None
    transport = None
    try:
        sock = http_proxy_tunnel_connect(conn_info.ssh_proxy, conn_info.ssh_endpoint, timeout=5)
        transport = paramiko.Transport(sock)
        transport.start_client()
        return transport
    except:
        if retries >= 1:
            if sock:
                sock.close()
            if transport:
                transport.close()
            return setup_paramiko_transport(conn_info, retries - 1)
        else:
            raise


def setup_authenticated_paramiko_transport(run_id, retries):
    # Grab the run information from the API to setup the run's IP and EDGE proxy address
    conn_info = get_conn_info(run_id)

    # Initialize Paramiko SSH client
    setup_paramiko_logging()
    transport = setup_paramiko_transport(conn_info, retries)
    # User password authentication, which available only to the OWNER and ROLE_ADMIN users
    sshpass = conn_info.ssh_pass
    sshuser = DEFAULT_SSH_USER
    try:
        ssh_default_root_user_enabled_preference = PreferenceAPI.get_preference('system.ssh.default.root.user.enabled')
        ssh_default_root_user_enabled = ssh_default_root_user_enabled_preference.value.lower() == "true"
    except:
        ssh_default_root_user_enabled = True
    if not ssh_default_root_user_enabled:
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


def create_background_tunnel(log_file, timeout):
    import subprocess
    with open(log_file or os.devnull, 'w') as output:
        if is_windows():
            # See https://docs.microsoft.com/ru-ru/windows/win32/procthread/process-creation-flags
            DETACHED_PROCESS = 0x00000008
            CREATE_NEW_PROCESS_GROUP = 0x00000200
            creationflags = DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP
            stdin = None
        else:
            creationflags = 0
            import pty
            _, stdin = pty.openpty()
        executable = sys.argv + ['-f'] if is_frozen() else [sys.executable] + sys.argv + ['-f']
        tunnel_proc = subprocess.Popen(executable, stdin=stdin, stdout=output, stderr=subprocess.STDOUT,
                                       cwd=os.getcwd(), env=os.environ.copy(), creationflags=creationflags)
        if stdin:
            os.close(stdin)
        time.sleep(timeout / 1000)
        if tunnel_proc.poll() is not None:
            import click
            click.echo('Failed to serve tunnel in background. Tunnel command exited with return code: {}'
                       .format(tunnel_proc.returncode), err=True)
            sys.exit(1)


def create_foreground_tunnel_with_ssh(run_id, local_port, remote_port, log_file, log_level, retries):
    if is_windows():
        import click
        click.echo('Passwordless ssh configuration is not supported on Windows.', err=True)
        sys.exit(1)
    ssh_path = resolve_ssh_path()
    ssh_config_path = '{}/config'.format(ssh_path)
    ssh_known_hosts_path = '{}/known_hosts'.format(ssh_path)
    ssh_keys_path = os.path.expanduser('~/.pipe/.ssh')
    ssh_private_key_name = 'pipeline-{}-{}-{}'.format(run_id, int(time.time()), random.randint(0, sys.maxsize))
    ssh_private_key_path = os.path.join(ssh_keys_path, ssh_private_key_name)
    ssh_public_key_path = '{}.pub'.format(ssh_private_key_path)
    remote_host = os.getenv('CP_CLI_TUNNEL_SSH_HOST', 'pipeline-{}'.format(run_id))
    conn_info = get_conn_info(run_id)
    remote_ssh_authorized_keys_paths = ['/root/.ssh/authorized_keys',
                                        '/home/{}/.ssh/authorized_keys'.format(conn_info.owner)]
    remote_ssh_public_key_path = '/root/.ssh/id_rsa.pub'
    if not os.path.exists(ssh_keys_path):
        os.makedirs(ssh_keys_path)
    try:
        generate_ssh_keys(log_file, ssh_private_key_path)
        upload_ssh_public_key_to_run(run_id, ssh_public_key_path, retries, remote_ssh_authorized_keys_paths)
        add_to_ssh_config(ssh_config_path, remote_host, local_port, ssh_private_key_path)
        add_to_ssh_known_hosts(run_id, local_port, log_file, retries, ssh_known_hosts_path, remote_ssh_public_key_path)
        create_foreground_tunnel(run_id, local_port, remote_port, log_file, log_level, retries)
    finally:
        remove_ssh_public_key_from_run(run_id, ssh_public_key_path, retries, remote_ssh_authorized_keys_paths)
        remove_ssh_keys(ssh_public_key_path, ssh_private_key_path)
        remove_from_ssh_config(ssh_config_path, remote_host)
        remove_from_ssh_known_hosts(ssh_known_hosts_path, local_port, log_file)


def resolve_ssh_path():
    possible_ssh_paths = []
    if os.getenv('CP_CLI_TUNNEL_SSH_PATH'):
        possible_ssh_paths.append(os.getenv('CP_CLI_TUNNEL_SSH_PATH'))
    possible_ssh_paths.append(os.path.expanduser('~/.ssh'))
    if is_wsl():
        command_output = perform_command(['powershell.exe', '$env:UserName'], collect_output=True) or ''
        windows_user = command_output.strip()
        if windows_user:
            possible_ssh_paths.append('/mnt/c/Users/{}/.ssh'.format(windows_user))
    for possible_ssh_path in possible_ssh_paths:
        if os.path.exists(possible_ssh_path):
            return possible_ssh_path
    raise RuntimeError('Default .ssh directory was not found by the following locations: {}. '
                       'Default .ssh directory path can be overridden using CP_CLI_TUNNEL_SSH_PATH variable.'
                       .format(', '.join(possible_ssh_paths)))


def create_foreground_tunnel(run_id, local_port, remote_port, log_file, log_level, retries,
                             tunnel_timeout=5, chunk_size=4096, server_delay=0.0001):
    logging.basicConfig(level=log_level or logging.ERROR)
    conn_info = get_conn_info(run_id)
    proxy_endpoint = (os.getenv('CP_CLI_TUNNEL_PROXY_HOST', conn_info.ssh_proxy[0]),
                      int(os.getenv('CP_CLI_TUNNEL_PROXY_PORT', conn_info.ssh_proxy[1])))
    target_endpoint = (os.getenv('CP_CLI_TUNNEL_TARGET_HOST', conn_info.ssh_endpoint[0]),
                       remote_port)
    tunnel_timeout = float(os.getenv('CP_CLI_TUNNEL_CONNECTION_TIMEOUT', tunnel_timeout))
    server_address = os.getenv('CP_CLI_TUNNEL_SERVER_ADDRESS', '0.0.0.0')
    logging.info('Initializing tunnel %s:pipeline-%s:%s...', local_port, run_id, remote_port)
    server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server_socket.bind((server_address, local_port))
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
                        tunnel_socket = http_proxy_tunnel_connect(proxy_endpoint, target_endpoint,
                                                                  timeout=tunnel_timeout,
                                                                  retries=retries)
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
                read_data = None
                sent_data = None
                try:
                    read_data = input.recv(chunk_size)
                except KeyboardInterrupt:
                    raise
                except:
                    logging.exception('Cannot read data from socket')
                if read_data:
                    logging.debug('Writing data...')
                    try:
                        channel[input].send(read_data)
                        sent_data = read_data
                    except KeyboardInterrupt:
                        raise
                    except:
                        logging.exception('Cannot write data to socket')
                if not read_data or not sent_data:
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
    finally:
        logging.info('Closing all sockets...')
        for input in inputs:
            input.close()
        logging.info('Exiting...')


def configure_graceful_exiting():
    def throw_keyboard_interrupt(signum, frame):
        logging.info('Killed...')
        raise KeyboardInterrupt()

    import signal
    signal.signal(signal.SIGTERM, throw_keyboard_interrupt)


def generate_ssh_keys(log_file, ssh_private_key_path):
    generate_ssh_keys_command = ['ssh-keygen', '-t', 'rsa', '-f', ssh_private_key_path, '-N', '', '-q']
    perform_command(generate_ssh_keys_command, log_file)


def remove_ssh_keys(ssh_public_key_path, ssh_private_key_path):
    if os.path.exists(ssh_public_key_path):
        os.remove(ssh_public_key_path)
    if os.path.exists(ssh_private_key_path):
        os.remove(ssh_private_key_path)


def upload_ssh_public_key_to_run(run_id, ssh_public_key_path, retries, remote_ssh_authorized_keys_paths):
    with open(ssh_public_key_path, 'r') as f:
        ssh_public_key = f.read().strip()
    run_ssh(run_id,
            'echo "{}" | tee -a {} > /dev/null'
            .format(ssh_public_key, ' '.join(remote_ssh_authorized_keys_paths)), retries)


def remove_ssh_public_key_from_run(run_id, ssh_public_key_path, retries, remote_ssh_authorized_keys_paths):
    if os.path.exists(ssh_public_key_path) and remote_ssh_authorized_keys_paths:
        with open(ssh_public_key_path, 'r') as f:
            ssh_public_key = f.read().strip()
        remove_ssh_public_keys_from_run_command = ''
        for remote_ssh_authorized_keys_path in remote_ssh_authorized_keys_paths:
            remote_ssh_authorized_keys_temp_path = '{}_{}'.format(remote_ssh_authorized_keys_path,
                                                                  random.randint(0, sys.maxsize))
            remove_ssh_public_keys_from_run_command += \
                'grep -v "{key}" {authorized_keys_path} > {authorized_keys_temp_path};' \
                'cp {authorized_keys_temp_path} {authorized_keys_path};' \
                'chmod 600 {authorized_keys_path};' \
                'rm {authorized_keys_temp_path};' \
                .format(key=ssh_public_key,
                        authorized_keys_path=remote_ssh_authorized_keys_path,
                        authorized_keys_temp_path=remote_ssh_authorized_keys_temp_path)
        run_ssh(run_id, remove_ssh_public_keys_from_run_command.rstrip(';'), retries)


def add_to_ssh_config(ssh_config_path, remote_host, local_port, ssh_private_key_path):
    remove_from_ssh_config(ssh_config_path, remote_host)
    ssh_config_path_existed = os.path.exists(ssh_config_path)
    with open(ssh_config_path, 'a') as f:
        f.write('Host {}\n'
                '    Hostname 127.0.0.1\n'
                '    Port {}\n'
                '    IdentityFile {}\n'
                .format(remote_host, local_port, ssh_private_key_path))
    if not ssh_config_path_existed:
        os.chmod(ssh_config_path, stat.S_IRUSR | stat.S_IWUSR)


def remove_from_ssh_config(ssh_config_path, remote_host):
    if os.path.exists(ssh_config_path):
        with open(ssh_config_path, 'r') as f:
            ssh_config_lines = f.readlines()
        updated_ssh_config_lines = []
        skip_host = False
        for line in ssh_config_lines:
            if line.startswith('Host '):
                if line.startswith('Host {}'.format(remote_host)):
                    skip_host = True
                else:
                    skip_host = False
            if not skip_host:
                updated_ssh_config_lines.append(line)
        with open(ssh_config_path, 'w') as f:
            f.writelines(updated_ssh_config_lines)
        os.chmod(ssh_config_path, stat.S_IRUSR | stat.S_IWUSR)


def add_to_ssh_known_hosts(run_id, local_port, log_file, retries, ssh_known_hosts_path, remote_ssh_public_key_path):
    ssh_known_hosts_temp_path = ssh_known_hosts_path + '_{}'.format(random.randint(0, sys.maxsize))
    run_scp_download(run_id, remote_ssh_public_key_path, ssh_known_hosts_temp_path, retries)
    with open(ssh_known_hosts_temp_path, 'r') as f:
        public_key = f.read().strip()
    os.remove(ssh_known_hosts_temp_path)
    ssh_known_hosts_path_existed = os.path.exists(ssh_known_hosts_path)
    with open(ssh_known_hosts_path, 'a') as f:
        f.write('[127.0.0.1]:{} {}'.format(local_port, public_key))
    if not ssh_known_hosts_path_existed:
        os.chmod(ssh_known_hosts_path, stat.S_IRUSR | stat.S_IWUSR)
    perform_command(['ssh-keygen', '-H', '-f', ssh_known_hosts_path], log_file)
    perform_command(['rm', ssh_known_hosts_path + '.old'], log_file)


def remove_from_ssh_known_hosts(ssh_known_hosts_path, local_port, log_file):
    if os.path.exists(ssh_known_hosts_path):
        perform_command(['ssh-keygen', '-R', '[127.0.0.1]:{}'.format(local_port), '-f', ssh_known_hosts_path], log_file)


def perform_command(executable, log_file=None, collect_output=True):
    import subprocess
    with open(log_file or os.devnull, 'a') as output:
        if collect_output:
            command_proc = subprocess.Popen(executable, stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=os.getcwd(),
                                            env=os.environ.copy())
        else:
            command_proc = subprocess.Popen(executable, stdout=output, stderr=subprocess.STDOUT, cwd=os.getcwd(),
                                            env=os.environ.copy())
        out, err = command_proc.communicate()
        exit_code = command_proc.wait()
        if exit_code != 0:
            raise RuntimeError('Command "{}" exited with return code: {}, stdout: {}, stderr: {}'
                               .format(executable, exit_code, out, err))
        return out


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
