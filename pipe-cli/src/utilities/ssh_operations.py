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
import itertools
import logging
import os
import random
import select
import socket
import stat
import sys
import time

import paramiko
from scp import SCPClient, SCPException
from src.api.cluster import Cluster

from src.config import is_frozen
from src.utilities.pipe_shell import plain_shell, interactive_shell
from src.utilities.platform_utilities import is_windows, is_wsl, is_mac
from src.api.pipeline_run import PipelineRun
from src.api.preferenceapi import PreferenceAPI
from urllib.parse import urlparse

DEFAULT_SSH_PORT = 22
DEFAULT_SSH_USER = 'root'
DEFAULT_LOGGING_FORMAT = '%(asctime)s:%(levelname)s: %(message)s'
PIPE_PROC_NAMES = ['pipe', 'pipe.exe']
TUNNEL_REQUIRED_ARGS = ['tunnel', 'start']
TUNNEL_LOCAL_PORT_ARGS = ['-lp', '--local-port']
TUNNEL_REMOTE_PORT_ARGS = ['-rp', '--remote-port']
TUNNEL_SSH_ARGS = ['-s', '--ssh']
TUNNEL_SSH_PATH_ARGS = ['-sp', '--ssh-path']
TUNNEL_SSH_HOST_ARGS = ['-sh', '--ssh-host']
TUNNEL_DIRECT_ARGS = ['-d', '--direct']
TUNNEL_CONFLICT_ARGS = ['-ke', '--keep-existing',
                        '-ks', '--keep-same',
                        '-re', '--replace-existing',
                        '-rd', '--replace-different']
PYTHON_PROC_PREFIX = 'python'
PIPE_SCRIPT_NAME = 'pipe.py'

run_conn_info = collections.namedtuple('conn_info', 'ssh_proxy ssh_endpoint ssh_pass owner sensitive')


class PasswordlessSSHConfig:

    def __init__(self, run_id, conn_info, ssh_path=None):
        self.owner_user = conn_info.owner.split('@')[0]
        self.user = DEFAULT_SSH_USER if is_ssh_default_root_user_enabled() else self.owner_user
        self.key_name = 'pipeline-{}-{}-{}'.format(run_id, int(time.time()), random.randint(0, sys.maxsize))

        self.remote_keys_path = '/root/.pipe/.keys'
        self.remote_private_key_path = '{}/{}'.format(self.remote_keys_path, self.key_name)
        self.remote_public_key_path = '{}.pub'.format(self.remote_private_key_path)
        self.remote_ppk_key_path = '{}.ppk'.format(self.remote_private_key_path)
        self.remote_host_rsa_public_key_path = '/etc/ssh/ssh_host_rsa_key.pub'
        self.remote_host_ed25519_public_key_path = '/etc/ssh/ssh_host_ed25519_key.pub'
        self.remote_authorized_keys_paths = ['/root/.ssh/authorized_keys',
                                             '/home/{}/.ssh/authorized_keys'.format(self.owner_user)]

        self.local_keys_path = os.path.join(os.path.expanduser('~'), '.pipe', '.keys')
        self.local_private_key_path = os.path.join(self.local_keys_path, self.key_name)
        self.local_public_key_path = '{}.pub'.format(self.local_private_key_path)
        self.local_ppk_key_path = '{}.ppk'.format(self.local_private_key_path)
        self.local_host_ed25519_public_key_path = os.path.join(self.local_keys_path,
                                                               '{}_{}'.format(self.key_name,
                                                                              'ssh_host_ed25519_key.pub'))

        self.local_openssh_path = ssh_path or os.path.expanduser('~/.ssh')
        self.local_openssh_config_path = os.path.join(self.local_openssh_path, 'config')
        self.local_openssh_known_hosts_path = os.path.join(self.local_openssh_path, 'known_hosts')

        self.local_putty_registry_path = r'Software\SimonTatham\PuTTY'
        self.local_putty_sessions_registry_path = r'{}\{}'.format(self.local_putty_registry_path, 'Sessions')
        self.local_putty_ssh_host_keys_registry_path = r'{}\{}'.format(self.local_putty_registry_path, 'SshHostKeys')


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


def direct_connect(target, timeout=None, retries=None):
    timeout = timeout or None
    retries = retries or 0
    sock = None
    try:
        import socket
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(timeout)
        sock.connect(target)
        return sock
    except KeyboardInterrupt:
        raise
    except:
        if retries >= 1:
            if sock:
                sock.close()
            return direct_connect(target, timeout=timeout, retries=retries - 1)
        else:
            raise


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


def get_conn_info(run_id, region=None):
    run_model = PipelineRun.get(run_id)
    proxy_url = Cluster.get_edge_external_url(region)
    if not proxy_url:
        raise RuntimeError('Cannot retrieve EDGE service external url')
    proxy_url_parts = urlparse(proxy_url)
    ssh_proxy_host = proxy_url_parts.hostname
    if not ssh_proxy_host:
        raise RuntimeError('Cannot resolve EDGE service hostname from its external url for the specified Run ID #{}'.format(run_id))
    ssh_proxy_port = proxy_url_parts.port
    if not ssh_proxy_port:
        ssh_proxy_port = 80 if proxy_url_parts.scheme == 'http' else 443
    return run_conn_info(ssh_proxy=(ssh_proxy_host, ssh_proxy_port),
                         ssh_endpoint=(run_model.pod_ip, DEFAULT_SSH_PORT),
                         ssh_pass=run_model.ssh_pass,
                         owner=run_model.owner,
                         sensitive=run_model.sensitive)


def get_custom_conn_info(host_id, region=None):
    proxy_url = Cluster.get_edge_external_url(region)
    if not proxy_url:
        raise RuntimeError('Cannot retrieve EDGE service external url')
    proxy_url_parts = urlparse(proxy_url)
    proxy_host = proxy_url_parts.hostname
    if not proxy_host:
        raise RuntimeError('Cannot resolve EDGE service hostname from its external url')
    proxy_port = proxy_url_parts.port
    if not proxy_port:
        proxy_port = 80 if proxy_url_parts.scheme == 'http' else 443
    return run_conn_info(ssh_proxy=(proxy_host, proxy_port),
                         ssh_endpoint=(host_id, DEFAULT_SSH_PORT),
                         ssh_pass=None,
                         owner=None,
                         sensitive=None)


def setup_paramiko_transport(conn_info, retries):
    retries = retries or 0
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


def setup_authenticated_paramiko_transport(run_id, user, retries, region=None):
    # Grab the run information from the API to setup the run's IP and EDGE proxy address
    conn_info = get_conn_info(run_id, region)

    # Initialize Paramiko SSH client
    setup_paramiko_logging()
    transport = setup_paramiko_transport(conn_info, retries)
    # User password authentication, which available only to the OWNER and ROLE_ADMIN users
    if user or is_ssh_default_root_user_enabled():
        sshpass = conn_info.ssh_pass
        sshuser = user or DEFAULT_SSH_USER
    else:
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


def is_ssh_default_root_user_enabled():
    try:
        ssh_default_root_user_enabled_preference = PreferenceAPI.get_preference('system.ssh.default.root.user.enabled')
        return ssh_default_root_user_enabled_preference.value.lower() == 'true'
    except:
        return True


def run_ssh_command(channel, command):
    channel.exec_command(command)
    plain_shell(channel)
    return channel.recv_exit_status()


def run_ssh_session(channel):
    channel.invoke_shell()
    interactive_shell(channel)


def run_ssh(run_identifier, command, user=None, retries=10, region=None):
    run_id = parse_run_identifier(run_identifier)
    if not run_id:
        raise RuntimeError('The specified run {} is not a valid run identifier.'.format(run_identifier))

    transport = None
    channel = None
    try:
        transport = setup_authenticated_paramiko_transport(run_id, user, retries, region)
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


def parse_run_identifier(run_identifier):
    if isinstance(run_identifier, int):
        return run_identifier
    import re
    match = re.search('^(\d+)$', run_identifier)
    if match:
        return int(match.group(1))
    match = re.search('^pipeline-(\d+)$', run_identifier)
    if match:
        return int(match.group(1))
    return None


def run_scp(source, destination, recursive, quiet, retries, region=None):
    source_location, source_run_id = parse_scp_location(source)
    destination_location, destination_run_id = parse_scp_location(destination)

    if source_run_id and destination_run_id:
        raise RuntimeError('Both source and destination are remote locations.')
    if not source_run_id and not destination_run_id:
        raise RuntimeError('Both source and destination are local locations.')

    conn_info = get_conn_info(source_run_id if source_run_id else destination_run_id, region)
    if conn_info.sensitive:
        raise RuntimeError('Tunnel connections to sensitive runs are not allowed.')

    if source_run_id:
        try:
            run_scp_download(source_run_id, source_location, destination_location,
                             recursive=recursive, quiet=quiet, user=None, retries=retries, region=region)
        except SCPException as e:
            if not recursive and 'not a regular file' in str(e):
                raise RuntimeError('Flag --recursive (-r) is required to copy directories.')
            else:
                raise e
    else:
        if not recursive and os.path.isdir(source_location):
            raise RuntimeError('Flag --recursive (-r) is required to copy directories.')
        run_scp_upload(destination_run_id, source_location, destination_location,
                       recursive=recursive, quiet=quiet, user=None, retries=retries, region=region)


def parse_scp_location(location):
    location_parts = location.split(':', 1)
    if len(location_parts) == 2:
        run_id = parse_run_identifier(location_parts[0])
        if run_id:
            return location_parts[1], run_id
    return location, None


def create_tunnel(host_id, local_port, remote_port, connection_timeout,
                  ssh, ssh_path, ssh_host, ssh_keep, direct, log_file, log_level,
                  timeout, timeout_stop, foreground,
                  keep_existing, keep_same, replace_existing, replace_different,
                  retries, region=None):
    logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)
    check_existing_tunnels(host_id, local_port, remote_port, ssh, ssh_path, ssh_host, direct, timeout_stop,
                           keep_existing, keep_same, replace_existing, replace_different)
    run_id = parse_run_identifier(host_id)
    if run_id:
        create_tunnel_to_run(run_id, local_port, remote_port, connection_timeout,
                             ssh, ssh_path, ssh_host, ssh_keep, direct, log_file, log_level,
                             timeout, foreground, retries, region)
    else:
        if ssh:
            raise RuntimeError('Passwordless SSH tunnel connections are allowed to runs only.')
        create_tunnel_to_host(host_id, local_port, remote_port, connection_timeout,
                              direct, log_file, log_level,
                              timeout, foreground, retries, region)


def check_existing_tunnels(host_id, local_port, remote_port,
                           ssh, ssh_path, ssh_host, direct, timeout_stop,
                           keep_existing, keep_same, replace_existing, replace_different):
    for tunnel_proc in find_tunnel_procs(run_id=None, local_port=local_port):
        logging.info('Process with pid %s was found (%s)', tunnel_proc.pid, ' '.join(tunnel_proc.cmdline()))
        if keep_existing:
            logging.info('Skipping tunnel establishing since the tunnel already exists...')
            sys.exit(0)
        if replace_existing:
            logging.info('Stopping existing tunnel...')
            kill_process(tunnel_proc, timeout_stop)
            return
        proc_args = tunnel_proc.cmdline()
        proc_remote_port = None
        proc_ssh = False
        proc_ssh_path = None
        proc_ssh_host = None
        proc_direct = False
        for i in range(len(proc_args)):
            proc_remote_port = proc_remote_port or get_parameter_value(proc_args, i, TUNNEL_REMOTE_PORT_ARGS)
            proc_ssh = proc_ssh or get_flag_value(proc_args, i, TUNNEL_SSH_ARGS)
            proc_ssh_path = proc_ssh_path or get_parameter_value(proc_args, i, TUNNEL_SSH_PATH_ARGS)
            proc_ssh_host = proc_ssh_host or get_parameter_value(proc_args, i, TUNNEL_SSH_HOST_ARGS)
            proc_direct = proc_direct or get_flag_value(proc_args, i, TUNNEL_DIRECT_ARGS)
        is_same_tunnel = (host_id in proc_args) \
            & (str(remote_port) == str(proc_remote_port)) \
            & (ssh == proc_ssh) \
            & (str(ssh_path) == str(proc_ssh_path)) \
            & (str(ssh_host) == str(proc_ssh_host)) \
            & (direct == proc_direct)
        if is_same_tunnel and keep_same:
            logging.info('Skipping tunnel establishing since the same tunnel already exists...')
            sys.exit(0)
        if not is_same_tunnel and replace_different:
            logging.info('Stopping existing tunnel since it has a different configuration...')
            kill_process(tunnel_proc, timeout_stop)
            return
        raise RuntimeError('{} tunnel already exists on the same local port'
                           .format('Same' if is_same_tunnel else 'Different'))


def get_parameter_value(proc_args, arg_index, arg_names):
    return proc_args[arg_index + 1] if proc_args[arg_index] in arg_names else None


def get_flag_value(proc_args, arg_index, arg_names):
    return proc_args[arg_index] in arg_names


def create_tunnel_to_run(run_id, local_port, remote_port, connection_timeout,
                         ssh, ssh_path, ssh_host, ssh_keep, direct, log_file, log_level,
                         timeout, foreground, retries, region=None):
    conn_info = get_conn_info(run_id, region)
    if conn_info.sensitive:
        raise RuntimeError('Tunnel connections to sensitive runs are not allowed.')
    remote_host = ssh_host or 'pipeline-{}'.format(run_id)
    if foreground:
        if ssh:
            create_foreground_tunnel_with_ssh(run_id, local_port, remote_port, connection_timeout, conn_info,
                                              ssh_path, ssh_keep, remote_host, direct, log_file, log_level, retries)
        else:
            create_foreground_tunnel(run_id, local_port, remote_port, connection_timeout, conn_info,
                                     remote_host, direct, log_level, retries)
    else:
        create_background_tunnel(local_port, remote_port, remote_host, log_file, log_level, timeout)


def create_tunnel_to_host(host_id, local_port, remote_port, connection_timeout,
                          direct, log_file, log_level,
                          timeout, foreground, retries, region=None):
    if foreground:
        conn_info = get_custom_conn_info(host_id, region)
        create_foreground_tunnel(host_id, local_port, remote_port, connection_timeout, conn_info,
                                 host_id, direct, log_level, retries)
    else:
        create_background_tunnel(local_port, remote_port, host_id, log_file, log_level, timeout)


def create_background_tunnel(local_port, remote_port, remote_host, log_file, log_level, timeout):
    import subprocess
    logging.info('Launching background tunnel %s:%s:%s...', local_port, remote_host, remote_port)
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
        sys_args = [sys_arg for sys_arg in sys.argv if sys_arg not in TUNNEL_CONFLICT_ARGS]
        executable = sys_args + ['-f'] if is_frozen() else [sys.executable] + sys_args + ['-f']
        tunnel_proc = subprocess.Popen(executable, stdin=stdin, stdout=output, stderr=subprocess.STDOUT,
                                       cwd=os.getcwd(), env=os.environ.copy(), creationflags=creationflags)
        if stdin:
            os.close(stdin)
        wait_for_background_tunnel(tunnel_proc, local_port, timeout)


def wait_for_background_tunnel(tunnel_proc, local_port, timeout, polling_delay=1):
    attempts = int(timeout / polling_delay)
    is_tunnel_ready = is_tunnel_ready_on_lin
    is_tunnel_ready = is_tunnel_ready_on_mac if is_mac() else is_tunnel_ready
    is_tunnel_ready = is_tunnel_ready_on_win if is_windows() else is_tunnel_ready
    while attempts > 0:
        time.sleep(polling_delay)
        if tunnel_proc.poll() is not None:
            raise RuntimeError('Failed to serve tunnel in background. '
                               'Tunnel exited with return code {}.'
                               .format(tunnel_proc.returncode))
        if is_tunnel_ready(tunnel_proc.pid, local_port):
            logging.info('Background tunnel is initialized. Exiting...')
            return
        logging.debug('Background tunnel is not initialized yet. '
                      'Only %s attempts remain left...', attempts)
        attempts -= 1
    raise RuntimeError('Failed to serve tunnel in background. '
                       'Tunnel is not initialized after {} seconds.'
                       .format(timeout))


def is_tunnel_ready_on_mac(tunnel_pid, local_port):
    # psutil.net_connections() is not allowed to a regular user on mac
    listening_pid = clean_tunnel_pid(perform_command(['lsof', '-t', '-i', 'TCP:' + str(local_port), '-s', 'TCP:LISTEN'],
                                                     fail_on_error=False))
    # In MacOS the "listening_pid" will be a child of "tunnel_pid" (which is a pyinstaller bootloader)
    # So we need to check that listening_pid's parent is a tunnel_pid
    listening_parent_pid = clean_tunnel_pid(perform_command(['ps', '-o', 'ppid=', '-p', listening_pid],
                                                            fail_on_error=False))
    logging.debug('Waiting for tunnel PID {}, got PID {} listening on port {} with parent PID {}'
                  .format(tunnel_pid, listening_pid, local_port, listening_parent_pid))
    return listening_pid and \
           listening_parent_pid and \
           (int(listening_pid) == tunnel_pid or int(listening_parent_pid) == tunnel_pid)


def clean_tunnel_pid(pid):
    return pid.strip() if pid and pid.strip().isdigit() else ''


def is_tunnel_ready_on_lin(tunnel_pid, local_port):
    listening_pid = get_listening_pid_on_lin_and_win(local_port)
    listening_parent_pid = get_parent_pid_on_lin_and_win(listening_pid)
    logging.debug('Waiting for tunnel PID {}, got PID {} listening on port {} with parent PID {}'
                  .format(tunnel_pid, listening_pid, local_port, listening_parent_pid))
    return listening_pid and \
           listening_parent_pid and \
           (listening_pid == tunnel_pid or listening_parent_pid == tunnel_pid)


def get_listening_pid_on_lin_and_win(local_port):
    import psutil
    for net_connection in psutil.net_connections():
        if net_connection.laddr and net_connection.laddr.port == local_port:
            return net_connection.pid
    return 0


def get_parent_pid_on_lin_and_win(pid):
    import psutil
    return psutil.Process(pid).ppid() if pid else 0


def is_tunnel_ready_on_win(tunnel_pid, local_port):
    return get_listening_pid_on_lin_and_win(local_port) == tunnel_pid


def create_foreground_tunnel_with_ssh(run_id, local_port, remote_port, connection_timeout, conn_info,
                                      ssh_path, ssh_keep, remote_host, direct, log_file, log_level, retries):
    if is_windows():
        create_foreground_tunnel_with_ssh_on_windows(run_id, local_port, remote_port, connection_timeout, conn_info,
                                                     ssh_keep, remote_host, direct, log_level, retries)
    else:
        create_foreground_tunnel_with_ssh_on_linux(run_id, local_port, remote_port, connection_timeout, conn_info,
                                                   ssh_path, ssh_keep, remote_host, direct, log_file, log_level, retries)


def create_foreground_tunnel_with_ssh_on_windows(run_id, local_port, remote_port, connection_timeout, conn_info,
                                                 ssh_keep, remote_host, direct, log_level, retries):
    logging.info('Configuring putty and openssh passwordless ssh...')
    passwordless_config = PasswordlessSSHConfig(run_id, conn_info)
    if not os.path.exists(passwordless_config.local_openssh_path):
        os.makedirs(passwordless_config.local_openssh_path, mode=stat.S_IRWXU)
    if not os.path.exists(passwordless_config.local_keys_path):
        os.makedirs(passwordless_config.local_keys_path, mode=stat.S_IRWXU)
    try:
        logging.info('Initializing passwordless ssh %s:%s:%s...', local_port, remote_host, remote_port)
        generate_remote_openssh_and_putty_keys(run_id, retries, passwordless_config)
        copy_remote_putty_and_private_keys(run_id, retries, passwordless_config)
        add_record_to_putty_config(local_port, remote_host, passwordless_config)
        copy_remote_openssh_public_host_key_to_putty_known_hosts(run_id, local_port, retries, passwordless_config)
        add_record_to_openssh_config(local_port, remote_host, passwordless_config)
        copy_remote_openssh_public_key_to_openssh_known_hosts(run_id, local_port, retries, passwordless_config)
        create_foreground_tunnel(run_id, local_port, remote_port, connection_timeout, conn_info,
                                 remote_host, direct, log_level, retries)
    except:
        logging.exception('Error occurred while trying set up tunnel')
        raise
    finally:
        if not ssh_keep:
            logging.info('Deinitializing passwordless ssh %s:%s:%s...', local_port, remote_host, remote_port)
            remove_remote_openssh_and_putty_keys(run_id, retries, passwordless_config)
            remove_remote_openssh_public_key_from_openssh_known_hosts(local_port, passwordless_config)
            remove_record_from_openssh_config(remote_host, passwordless_config)
            remove_remote_openssh_host_public_key_from_putty_known_hosts(local_port, passwordless_config)
            remove_record_from_putty_config(remote_host, passwordless_config)
            remove_ssh_keys(passwordless_config.local_private_key_path,
                            passwordless_config.local_ppk_key_path,
                            passwordless_config.local_host_ed25519_public_key_path)


def create_foreground_tunnel_with_ssh_on_linux(run_id, local_port, remote_port, connection_timeout, conn_info,
                                               ssh_path, ssh_keep, remote_host, direct, log_file, log_level, retries):
    logging.info('Configuring openssh passwordless ssh...')
    passwordless_config = PasswordlessSSHConfig(run_id, conn_info, ssh_path)
    if not os.path.exists(passwordless_config.local_openssh_path):
        os.makedirs(passwordless_config.local_openssh_path, mode=stat.S_IRWXU)
    if not os.path.exists(passwordless_config.local_keys_path):
        os.makedirs(passwordless_config.local_keys_path, mode=stat.S_IRWXU)
    try:
        logging.info('Initializing passwordless ssh %s:%s:%s...', local_port, remote_host, remote_port)
        generate_openssh_keys(log_file, passwordless_config)
        copy_openssh_public_key_to_remote_authorized_hosts(run_id, retries, passwordless_config)
        add_record_to_openssh_config(local_port, remote_host, passwordless_config)
        copy_remote_openssh_public_key_to_openssh_known_hosts(run_id, local_port, retries, passwordless_config)
        create_foreground_tunnel(run_id, local_port, remote_port, connection_timeout, conn_info,
                                 remote_host, direct, log_level, retries)
    except:
        logging.exception('Error occurred while trying set up tunnel')
        raise
    finally:
        if not ssh_keep:
            logging.info('Deinitializing passwordless ssh %s:%s:%s...', local_port, remote_host, remote_port)
            remove_openssh_public_key_from_remote_authorized_hosts(run_id, retries, passwordless_config)
            remove_remote_openssh_public_key_from_openssh_known_hosts(local_port, passwordless_config)
            remove_record_from_openssh_config(remote_host, passwordless_config)
            remove_ssh_keys(passwordless_config.local_public_key_path,
                            passwordless_config.local_private_key_path)


def create_foreground_tunnel(run_id, local_port, remote_port, connection_timeout, conn_info,
                             remote_host, direct, log_level, retries,
                             chunk_size=4096, server_delay=0.0001):
    proxy_endpoint = (os.getenv('CP_CLI_TUNNEL_PROXY_HOST', conn_info.ssh_proxy[0]),
                      int(os.getenv('CP_CLI_TUNNEL_PROXY_PORT', conn_info.ssh_proxy[1])))
    target_endpoint = (os.getenv('CP_CLI_TUNNEL_TARGET_HOST', conn_info.ssh_endpoint[0]),
                       remote_port)
    server_address = os.getenv('CP_CLI_TUNNEL_SERVER_ADDRESS', '0.0.0.0')
    logging.info('Initializing tunnel %s:%s:%s...', local_port, remote_host, remote_port)
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
                        if direct:
                            tunnel_socket = direct_connect(target_endpoint,
                                                           timeout=connection_timeout,
                                                           retries=retries)
                        else:
                            tunnel_socket = http_proxy_tunnel_connect(proxy_endpoint, target_endpoint,
                                                                      timeout=connection_timeout,
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


def generate_remote_openssh_and_putty_keys(run_id, retries, passwordless_config):
    logging.info('Generating tunnel remote ssh keys and copying ssh public key to authorized keys...')
    exit_code = run_ssh(run_id,
                        """
                        mkdir -p $(dirname {remote_private_key_path})
                        ssh-keygen -t rsa -f {remote_private_key_path} -N "" -q
                        cat {remote_public_key_path} | tee -a {remote_authorized_keys_paths} > /dev/null
                        if ! command -v puttygen; then
                            wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/putty/puttygen.tgz" -O "/tmp/puttygen.tgz"
                            tar -zxf "/tmp/puttygen.tgz" -C "${{CP_USR_BIN:-/usr/cpbin}}"
                            rm -f "/tmp/puttygen.tgz"
                        fi
                        if ! command -v puttygen; then apt-get -y install putty-tools; fi
                        if ! command -v puttygen; then yum -y install putty; fi
                        puttygen {remote_private_key_path} -o {remote_ppk_key_path} -O private
                        """
                        .format(remote_public_key_path=passwordless_config.remote_public_key_path,
                                remote_private_key_path=passwordless_config.remote_private_key_path,
                                remote_ppk_key_path=passwordless_config.remote_ppk_key_path,
                                remote_authorized_keys_paths=' '.join(passwordless_config.remote_authorized_keys_paths)),
                        user=DEFAULT_SSH_USER, retries=retries)
    if exit_code:
        raise RuntimeError('Generating tunnel remote ssh keys and copying ssh public key to authorized keys '
                           'have failed with {} exit code'
                           .format(exit_code))


def remove_remote_openssh_and_putty_keys(run_id, retries, passwordless_config):
    logging.info('Deleting remote ssh keys...')
    remove_ssh_keys_from_run_command = ''
    for remote_authorized_keys_path in passwordless_config.remote_authorized_keys_paths:
        remote_authorized_keys_temp_path = '{}_{}'.format(remote_authorized_keys_path,
                                                          random.randint(0, sys.maxsize))
        remove_ssh_keys_from_run_command += \
            ('[ -f {public_key_path} ] &&'
             'cat {public_key_path} | xargs -I {{}} grep -v "{{}}" {authorized_keys_path} > {authorized_keys_temp_path} &&'
             'cp {authorized_keys_temp_path} {authorized_keys_path} &&'
             'chmod 600 {authorized_keys_path} &&'
             'rm {authorized_keys_temp_path};') \
                .format(public_key_path=passwordless_config.remote_public_key_path,
                        private_key_path=passwordless_config.remote_private_key_path,
                        ppk_key_path=passwordless_config.remote_ppk_key_path,
                        authorized_keys_path=remote_authorized_keys_path,
                        authorized_keys_temp_path=remote_authorized_keys_temp_path)
    for key_path in [passwordless_config.remote_public_key_path,
                     passwordless_config.remote_private_key_path,
                     passwordless_config.remote_ppk_key_path]:
        remove_ssh_keys_from_run_command += '[ -f {key_path} ] && rm {key_path};'.format(key_path=key_path)
    exit_code = run_ssh(run_id, remove_ssh_keys_from_run_command.rstrip(';'),
                        user=DEFAULT_SSH_USER, retries=retries)
    if exit_code:
        raise RuntimeError('Deleting remote ssh keys has failed with {} exit code'
                           .format(exit_code))


def copy_remote_putty_and_private_keys(run_id, retries, passwordless_config):
    logging.info('Copying remote ppk key...')
    run_scp_download(run_id, passwordless_config.remote_ppk_key_path, passwordless_config.local_ppk_key_path,
                     user=DEFAULT_SSH_USER, retries=retries)
    run_scp_download(run_id, passwordless_config.remote_private_key_path, passwordless_config.local_private_key_path,
                     user=DEFAULT_SSH_USER, retries=retries)


def add_record_to_putty_config(local_port, remote_host, passwordless_config):
    import winreg
    logging.info('Appending host record to putty sessions...')
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER,
                          r'{}\{}'.format(passwordless_config.local_putty_sessions_registry_path, remote_host)) as key:
        winreg.SetValueEx(key, 'HostName', 0, winreg.REG_SZ, '{}@127.0.0.1'.format(passwordless_config.user))
        winreg.SetValueEx(key, 'PortNumber', 0, winreg.REG_DWORD, local_port)
        winreg.SetValueEx(key, 'Protocol', 0, winreg.REG_SZ, 'ssh')
        winreg.SetValueEx(key, 'PublicKeyFile', 0, winreg.REG_SZ, passwordless_config.local_ppk_key_path)


def remove_record_from_putty_config(remote_host, passworless_config):
    import winreg

    logging.info('Removing host record from putty sessions...')
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, passworless_config.local_putty_sessions_registry_path) as key:
        if winreg_subkey_exists(key, remote_host):
            winreg.DeleteKey(winreg.HKEY_CURRENT_USER,
                             r'{}\{}'.format(passworless_config.local_putty_sessions_registry_path, remote_host))


def copy_remote_openssh_public_host_key_to_putty_known_hosts(run_id, local_port, retries, passwordless_config):
    import winreg
    from src.utilities.putty import get_putty_fingerprint
    logging.info('Copying remote host public key...')
    run_scp_download(run_id, passwordless_config.remote_host_ed25519_public_key_path,
                     passwordless_config.local_host_ed25519_public_key_path,
                     user=DEFAULT_SSH_USER, retries=retries)

    logging.info('Calculating putty host hash...')
    with open(passwordless_config.local_host_ed25519_public_key_path, 'r') as f:
        ssh_host_public_key = f.read().strip()

    os.remove(passwordless_config.local_host_ed25519_public_key_path)

    remote_host_fingerprint = get_putty_fingerprint(ssh_host_public_key)
    if not remote_host_fingerprint:
        raise RuntimeError('Putty host hash calculation has failed for host public key')

    logging.info('Appending host record to putty known hosts...')
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, passwordless_config.local_putty_ssh_host_keys_registry_path) as key:
        winreg.SetValueEx(key, 'ssh-ed25519@{}:127.0.0.1'.format(local_port), 0, winreg.REG_SZ, remote_host_fingerprint)


def remove_remote_openssh_host_public_key_from_putty_known_hosts(local_port, passwordless_config):
    import winreg

    logging.info('Removing host record from putty known hosts...')
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, passwordless_config.local_putty_ssh_host_keys_registry_path) as key:
        putty_ssh_host_key_name = 'ssh-ed25519@{}:127.0.0.1'.format(local_port)
        if winreg_value_exists(key, putty_ssh_host_key_name):
            winreg.DeleteValue(key, putty_ssh_host_key_name)


def winreg_subkey_exists(key, expected_subkey_name):
    import winreg

    try:
        for i in itertools.count():
            actual_subkey_name = winreg.EnumKey(key, i)
            if actual_subkey_name == expected_subkey_name:
                return True
    except OSError:
        pass
    return False


def winreg_value_exists(key, expected_value_name):
    import winreg

    try:
        for i in itertools.count():
            actual_value_name, _, _ = winreg.EnumValue(key, i)
            if actual_value_name == expected_value_name:
                return True
    except OSError:
        pass
    return False


def generate_openssh_keys(log_file, passwordless_config):
    logging.info('Generating tunnel ssh keys...')
    perform_command(['ssh-keygen', '-t', 'rsa', '-f', passwordless_config.local_private_key_path, '-N', '', '-q'], log_file)


def remove_ssh_keys(*key_paths):
    logging.info('Removing tunnel ssh keys...')
    for key_path in key_paths:
        if os.path.exists(key_path):
            os.remove(key_path)


def copy_openssh_public_key_to_remote_authorized_hosts(run_id, retries, passwordless_config):
    logging.info('Copying ssh public key to remote authorized keys...')
    with open(passwordless_config.local_public_key_path, 'r') as f:
        ssh_public_key = f.read().strip()
    exit_code = run_ssh(run_id,
                        'echo "{}" | tee -a {} > /dev/null'
                        .format(ssh_public_key, ' '.join(passwordless_config.remote_authorized_keys_paths)),
                        user=DEFAULT_SSH_USER, retries=retries)
    if exit_code:
        raise RuntimeError('Copying ssh public key to remote authorized keys has failed with {} exit code'.format(exit_code))


def remove_openssh_public_key_from_remote_authorized_hosts(run_id, retries, passwordless_config):
    logging.info('Removing ssh public keys from remote authorized hosts...')
    if os.path.exists(passwordless_config.local_public_key_path):
        with open(passwordless_config.local_public_key_path, 'r') as f:
            ssh_public_key = f.read().strip()
        remove_ssh_public_keys_from_run_command = ''
        for remote_ssh_authorized_keys_path in passwordless_config.remote_authorized_keys_paths:
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
        exit_code = run_ssh(run_id, remove_ssh_public_keys_from_run_command.rstrip(';'),
                            user=DEFAULT_SSH_USER, retries=retries)
        if exit_code:
            raise RuntimeError('Removing ssh public keys from remote authorized hosts has failed with {} exit code'.format(exit_code))


def add_record_to_openssh_config(local_port, remote_host, passwordless_config):
    remove_record_from_openssh_config(remote_host, passwordless_config)
    logging.info('Appending host record to ssh config...')
    ssh_config_path_existed = os.path.exists(passwordless_config.local_openssh_config_path)
    with open(passwordless_config.local_openssh_config_path, 'a+') as f:
        f.seek(0)
        content = f.read() or ''
        if content.strip():
            if not content.endswith('\n\n'):
                if not content.endswith('\n'):
                    f.write('\n')
                f.write('\n')
        f.write('Host {}\n'
                '    Hostname 127.0.0.1\n'
                '    Port {}\n'
                '    IdentityFile {}\n'
                '    User {}\n'
                .format(remote_host, local_port,
                        passwordless_config.local_private_key_path, passwordless_config.user))
    if not ssh_config_path_existed and not is_windows():
        os.chmod(passwordless_config.local_openssh_config_path, stat.S_IRUSR | stat.S_IWUSR)


def remove_record_from_openssh_config(remote_host, passwordless_config):
    logging.info('Removing host record from ssh config...')
    if os.path.exists(passwordless_config.local_openssh_config_path):
        with open(passwordless_config.local_openssh_config_path, 'r') as f:
            ssh_config_lines = f.readlines()
        updated_ssh_config_lines = []
        skip_host = False
        skip_newlines = True
        for line in ssh_config_lines:
            if line.startswith('Host '):
                skip_newlines = False
                if line.startswith('Host {}'.format(remote_host)):
                    skip_host = True
                else:
                    skip_host = False
            if not skip_host:
                if not line or not line.strip():
                    if skip_newlines:
                        continue
                    else:
                        skip_newlines = True
                updated_ssh_config_lines.append(line)
        with open(passwordless_config.local_openssh_config_path, 'w') as f:
            f.writelines(updated_ssh_config_lines)
        if not is_windows():
            os.chmod(passwordless_config.local_openssh_config_path, stat.S_IRUSR | stat.S_IWUSR)


def copy_remote_openssh_public_key_to_openssh_known_hosts(run_id, local_port, retries, passwordless_config):
    remove_remote_openssh_public_key_from_openssh_known_hosts(local_port, passwordless_config)
    logging.info('Copying remote public key to known hosts...')
    ssh_known_hosts_temp_path = passwordless_config.local_openssh_known_hosts_path + '_{}'.format(random.randint(0, sys.maxsize))
    run_scp_download(run_id, passwordless_config.remote_host_rsa_public_key_path, ssh_known_hosts_temp_path,
                     user=DEFAULT_SSH_USER, retries=retries)
    with open(ssh_known_hosts_temp_path, 'r') as f:
        public_key = f.read().strip()
    os.remove(ssh_known_hosts_temp_path)
    ssh_known_hosts_path_existed = os.path.exists(passwordless_config.local_openssh_known_hosts_path)
    with open(passwordless_config.local_openssh_known_hosts_path, 'a+') as f:
        f.seek(0)
        content = f.read() or ''
        if content.strip() and not content.endswith('\n'):
            f.write('\n')
        f.write('[127.0.0.1]:{} {}\n'.format(local_port, public_key))
    if not ssh_known_hosts_path_existed and not is_windows():
        os.chmod(passwordless_config.local_openssh_known_hosts_path, stat.S_IRUSR | stat.S_IWUSR)


def remove_remote_openssh_public_key_from_openssh_known_hosts(local_port, passwordless_config):
    logging.info('Removing remote public key from known hosts...')
    if os.path.exists(passwordless_config.local_openssh_known_hosts_path):
        with open(passwordless_config.local_openssh_known_hosts_path, 'r') as f:
            ssh_known_hosts_lines = f.readlines()
        updated_ssh_known_hosts_lines = [line for line in ssh_known_hosts_lines
                                         if line and line.strip()
                                         and not line.startswith('[127.0.0.1]:{}'.format(local_port))]
        with open(passwordless_config.local_openssh_known_hosts_path, 'w') as f:
            f.writelines(updated_ssh_known_hosts_lines)
        if not is_windows():
            os.chmod(passwordless_config.local_openssh_known_hosts_path, stat.S_IRUSR | stat.S_IWUSR)


def perform_command(executable, log_file=None, collect_output=True, fail_on_error=True):
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
        if exit_code != 0 and fail_on_error:
            raise RuntimeError('Command "{}" exited with return code: {}, stdout: {}, stderr: {}'
                               .format(executable, exit_code, out, err))
        return out


def kill_tunnels(run_id=None, local_port=None, timeout=None, force=False, log_level=None):
    logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)
    for tunnel_proc in find_tunnel_procs(run_id, local_port):
        logging.info('Process with pid %s was found (%s)', tunnel_proc.pid, ' '.join(tunnel_proc.cmdline()))
        logging.info('Killing the process...')
        kill_process(tunnel_proc, timeout / 1000, force)


def find_tunnel_procs(run_id=None, local_port=None):
    import psutil
    logging.info('Searching for pipe tunnel processes...')
    current_pids = get_current_pids()
    for proc in psutil.process_iter():
        if proc.pid in current_pids:
            continue
        proc_name = proc.name()
        if proc_name not in PIPE_PROC_NAMES and not proc_name.startswith(PYTHON_PROC_PREFIX):
            continue
        proc_args = proc.cmdline()
        if proc_name.startswith(PYTHON_PROC_PREFIX) \
                and all(not proc_arg.endswith(PIPE_SCRIPT_NAME)
                        for proc_arg in proc_args):
            continue
        if not all(required_arg in proc_args
                   for required_arg in (TUNNEL_REQUIRED_ARGS + ([str(run_id)] if run_id else []))):
            continue
        if local_port:
            for i in range(len(proc_args)):
                if proc_args[i] in TUNNEL_LOCAL_PORT_ARGS and proc_args[i + 1] == str(local_port):
                    yield proc
        else:
            yield proc


def get_current_pids():
    import psutil
    current_pids = [os.getpid()]
    try:
        for _ in range(3):
            current_pids.append(psutil.Process(current_pids[-1]).ppid())
    except psutil.NoSuchProcess:
        pass
    return current_pids


def kill_process(proc, timeout, force=False):
    import psutil
    import signal
    if is_windows():
        send_signal_to_process(proc, signal.SIGTERM, timeout)
    elif force:
        send_signal_to_process(proc, signal.SIGKILL, timeout)
    else:
        try:
            send_signal_to_process(proc, signal.SIGTERM, timeout)
        except psutil.TimeoutExpired:
            send_signal_to_process(proc, signal.SIGKILL, timeout)


def send_signal_to_process(proc, signal, timeout):
    if proc.is_running():
        proc.send_signal(signal)
        proc.wait(timeout if timeout else None)


def run_scp_upload(run_id, source, destination, recursive=False, quiet=True, user=None, retries=None, region=None):
    transport = None
    scp = None
    try:
        transport = setup_authenticated_paramiko_transport(run_id, user, retries, region)
        scp = SCPClient(transport, progress=None if quiet else build_scp_progress())
        scp.put(source, destination, recursive=recursive)
    finally:
        if scp:
            scp.close()
        if transport:
            transport.close()


def run_scp_download(run_id, source, destination, recursive=False, quiet=True, user=None, retries=None, region=None):
    transport = None
    scp = None
    try:
        transport = setup_authenticated_paramiko_transport(run_id, user, retries, region)
        scp = SCPClient(transport, progress=None if quiet else build_scp_progress())
        scp.get(source, destination, recursive=recursive)
    finally:
        if scp:
            scp.close()
        if transport:
            transport.close()


def build_scp_progress():
    from src.utilities.progress_bar import ProgressPercentage

    progresses = {}

    def scp_progress(filename, size, total):
        progress = progresses.get(filename, None)
        if not progress:
            progress = progresses[filename] = ProgressPercentage(filename, size)
        progress(total - progress._seen_so_far_in_bytes)

    return scp_progress
