# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import collections
import functools
from contextlib import closing

import click
import itertools
import logging
import os
import random

import prettytable
import select
import socket
import stat
import sys
import time

import paramiko
from scp import SCPClient, SCPException
from src.api.cluster import Cluster
from src.api.user import User

from src.config import Config, is_frozen
from src.utilities.pipe_shell import plain_shell, interactive_shell, PYTHON3
from src.utilities.platform_utilities import is_windows, is_mac
from src.api.pipeline_run import PipelineRun
from src.api.preferenceapi import PreferenceAPI
from urllib.parse import urlparse

DEFAULT_SSH_PORT = 22
DEFAULT_SSH_USER = 'root'
DEFAULT_LOGGING_FORMAT = '%(asctime)s:%(levelname)s: %(message)s'
PYTHON_PROC_SUBSTR = 'python'
PIPE_PROC_SUBSTR = 'pipe'
PIPE_SCRIPT_NAME = 'pipe.py'
TUNNEL_REQUIRED_ARGS = ['tunnel', 'start']
TUNNEL_CONFLICT_ARGS = ['-ke', '--keep-existing',
                        '-ks', '--keep-same',
                        '-re', '--replace-existing',
                        '-rd', '--replace-different']
TUNNEL_FOREGROUND_ARGS = ['--foreground']
TUNNEL_IGNORE_EXISTING_ARGS = ['--ignore-existing']
UNKNOWN_USER = 'unknown'

run_conn_info = collections.namedtuple('conn_info', 'ssh_proxy ssh_endpoint ssh_pass owner '
                                                    'sensitive platform parameters')


def restarting(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        timeout = 10
        while True:
            try:
                return func(*args, **kwargs)
            except KeyboardInterrupt:
                raise
            except Exception:
                logging.warn('Restarting in %s seconds...', timeout)
            time.sleep(10)
    return wrapper


def socketclosing(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        inputs = []
        while True:
            try:
                return func(inputs, *args, **kwargs)
            except KeyboardInterrupt:
                logging.info('Interrupted...')
                raise
            except Exception:
                logging.exception('Errored...')
                raise
            finally:
                logging.info('Closing all sockets...')
                for input in inputs:
                    input.close()
                logging.info('Exiting...')
    return wrapper


class TunnelError(Exception):
    pass


class PasswordlessSSHConfig:

    def __init__(self, run_id, conn_info, ssh_users=None, ssh_path=None):
        self.run_ssh_mode = resolve_run_ssh_mode(conn_info)
        self.run_owner = conn_info.owner.split('@')[0]
        _non_unique_users = ssh_users or [resolve_run_ssh_user(self.run_ssh_mode, self.run_owner)]
        self.users = list(set(_non_unique_users))
        self.user = _non_unique_users[0]
        self.key_name = 'pipeline-{}-{}-{}'.format(run_id, int(time.time()), random.randint(0, sys.maxsize))

        self.remote_keys_path = '/root/.pipe/.keys'
        self.remote_private_key_path = '{}/{}'.format(self.remote_keys_path, self.key_name)
        self.remote_public_key_path = '{}.pub'.format(self.remote_private_key_path)
        self.remote_ppk_key_path = '{}.ppk'.format(self.remote_private_key_path)
        self.remote_host_rsa_public_key_path = '/etc/ssh/ssh_host_rsa_key.pub'
        self.remote_host_ed25519_public_key_path = '/etc/ssh/ssh_host_ed25519_key.pub'
        self.remote_authorized_users = self.users

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


class TunnelArgs:

    def __init__(self, host_id=None, local_ports=None, remote_ports=None,
                 ssh=None, ssh_path=None, ssh_host=None, ssh_users=None,
                 direct=None):
        self.host_id = str(host_id) if host_id else None
        self.local_ports = local_ports
        self.remote_ports = remote_ports
        if not self.local_ports:
            self.local_ports = self.remote_ports
        if not self.remote_ports:
            self.remote_ports = self.local_ports
        self.ssh = ssh
        self.ssh_path = str(ssh_path) if ssh_path else None
        self.ssh_host = str(ssh_host) if ssh_host else None
        self.ssh_users = ssh_users
        self.direct = direct

    @staticmethod
    def from_args(parsed_args):
        return TunnelArgs(
            host_id=parsed_args.get('host_id'),
            local_ports=parse_ports(parsed_args.get('local_port')),
            remote_ports=parse_ports(parsed_args.get('remote_port')),
            ssh=parsed_args.get('ssh'),
            ssh_path=parsed_args.get('ssh_path'),
            ssh_host=parsed_args.get('ssh_host'),
            ssh_users=parsed_args.get('ssh_user'),
            direct=parsed_args.get('direct'),
        )

    def compare(self, existing_tunnel_args):
        for public_field in list(self.__dict__):
            creating_tunnel_value = getattr(self, public_field)
            existing_tunnel_value = getattr(existing_tunnel_args, public_field)
            if creating_tunnel_value != existing_tunnel_value:
                logging.debug('The existing tunnel process has different %s configuration '
                              'in comparison with the creating tunnel process. Existing: %s. Creating: %s.',
                              public_field, existing_tunnel_value, creating_tunnel_value)
                return False
        return True


class SystemProcess:

    def __init__(self, pid=None, ppid=None, owner=None, args=None):
        self.pid = pid
        self.ppid = ppid
        self.owner = owner
        self.args = args or []


class TunnelProcess:

    def __init__(self, pid=None, ppid=None, owner=None, args=None, proc=None, parsed_args=None):
        self.pid = pid
        self.ppid = ppid
        self.owner = owner
        self.args = args or []
        self.proc = proc
        self.parsed_args = parsed_args or {}


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
    except Exception:
        if retries >= 1:
            if sock:
                sock.close()
            return direct_connect(target, timeout=timeout, retries=retries - 1)
        else:
            raise


def base64ify(bytes_or_str):
    import base64
    if PYTHON3 and isinstance(bytes_or_str, str):
        input_bytes = bytes_or_str.encode('utf8')
    else:
        input_bytes = bytes_or_str

    output_bytes = base64.urlsafe_b64encode(input_bytes)
    if PYTHON3:
        return output_bytes.decode('ascii')
    else:
        return output_bytes

def http_proxy_tunnel_connect(proxy, target, timeout=None, retries=None):
    timeout = timeout or None
    retries = retries or 0
    sock = None
    try:
        import socket
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(timeout)
        sock.connect(proxy)

        config = Config.instance()
        auth_token = base64ify('%s:%s' % (config.get_current_user().split('@')[0], config.access_key))
        headers = {'proxy-authorization': 'Basic ' + auth_token}

        cmd_connect = "CONNECT %s:%d HTTP/1.0\r\n" % target
        cmd_connect += '\r\n'.join('%s: %s' % (k, v) for (k, v) in headers.items()) + '\r\n\r\n'
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
    except Exception:
        if retries >= 1:
            if sock:
                sock.close()
            return http_proxy_tunnel_connect(proxy, target, timeout=timeout, retries=retries - 1)
        else:
            raise


def get_conn_info(run_id, region=None):
    run_model = PipelineRun.get(run_id)
    if not run_model.is_initialized:
        raise RuntimeError('The specified Run ID #{} is not initialized for the SSH session'.format(run_id))
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
                         sensitive=run_model.sensitive,
                         platform=run_model.platform,
                         parameters={parameter.name: parameter.value for parameter in run_model.parameters})


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
                         sensitive=None,
                         platform=None,
                         parameters={})


def setup_paramiko_transport(conn_info, retries):
    retries = retries or 0
    sock = None
    transport = None
    try:
        sock = http_proxy_tunnel_connect(conn_info.ssh_proxy, conn_info.ssh_endpoint, timeout=5)
        transport = paramiko.Transport(sock)
        transport.start_client()
        return transport
    except Exception:
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
    run_ssh_mode = 'root' if user == DEFAULT_SSH_USER \
                   else 'user' if user \
                   else resolve_run_ssh_mode(conn_info)
    run_owner = conn_info.owner.split('@')[0]
    user = user or User.whoami().get('userName')
    user = user.split('@')[0]
    if run_ssh_mode == 'user':
        sshuser = user
        sshpass = sshuser
    elif run_ssh_mode == 'owner':
        sshuser = run_owner
        sshpass = sshuser
    elif run_ssh_mode == 'owner-sshpass':
        sshuser = run_owner
        sshpass = resolve_run_ssh_pass(conn_info, region)
    else:
        sshuser = DEFAULT_SSH_USER
        sshpass = resolve_run_ssh_pass(conn_info, region)
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
        return get_boolean(ssh_default_root_user_enabled_preference.value)
    except Exception:
        return True


def get_boolean(value):
    return value and value.lower().strip() == 'true'


def resolve_run_ssh_mode(conn_info):
    return conn_info.parameters.get('CP_CAP_SSH_MODE') \
           or ('owner-sshpass' if conn_info.platform == 'windows'
               else 'root' if is_ssh_default_root_user_enabled()
               else 'owner')


def resolve_run_ssh_user(run_ssh_mode, run_owner):
    user = User.whoami().get('userName') if run_ssh_mode == 'user' \
           else run_owner if run_ssh_mode == 'owner' \
           else run_owner if run_ssh_mode == 'owner-sshpass' \
           else DEFAULT_SSH_USER
    return user.split('@')[0]


def resolve_run_ssh_pass(conn_info, region=None):
    parent_run_id = conn_info.parameters.get('parent-id')
    run_shared_users_enabled = get_boolean(conn_info.parameters.get('CP_CAP_SHARE_USERS'))
    if run_shared_users_enabled and parent_run_id:
        parent_conn_info = get_conn_info(parent_run_id, region)
        return parent_conn_info.ssh_pass
    else:
        return conn_info.ssh_pass


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


def create_tunnel(host_id, local_ports_str, remote_ports_str, connection_timeout,
                  ssh, ssh_path, ssh_host, ssh_users, ssh_keep, direct, log_file, log_level,
                  timeout, timeout_stop, foreground,
                  keep_existing, keep_same, replace_existing, replace_different, ignore_owner, ignore_existing,
                  retries, region, parse_tunnel_args):
    logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)
    local_ports, remote_ports = resolve_ports(local_ports_str, remote_ports_str,
                                              '-lp/--local-port', '-rp/--remote-port')
    if len(local_ports) > 1 and ssh:
        raise RuntimeError('A single port can be specified using -lp/--local-port and -rp/--remote-port options '
                           'if -s/--ssh option is used.')
    run_id = parse_run_identifier(host_id)
    if not run_id and ssh:
        raise RuntimeError('Option -s/--ssh can be used only for run tunnels.')
    if not ignore_existing:
        check_existing_tunnels(host_id, local_ports, remote_ports,
                               ssh, ssh_path, ssh_host, ssh_users, direct, log_file, timeout_stop,
                               keep_existing, keep_same, replace_existing, replace_different, ignore_owner,
                               region, retries, parse_tunnel_args)
        check_local_ports(local_ports)
    if run_id:
        create_tunnel_to_run(run_id, local_ports, remote_ports, connection_timeout,
                             ssh, ssh_path, ssh_host, ssh_users, ssh_keep, direct, log_file, log_level,
                             timeout, foreground, retries, region)
    else:
        create_tunnel_to_host(host_id, local_ports, remote_ports, connection_timeout,
                              direct, log_file, log_level,
                              timeout, foreground, retries, region)


def create_transmitting_tunnel(tunnel_host, tunnel_ports_str, output_host, output_ports_str,
                               refresh_interval, pool_size,
                               connection_timeout,
                               direct, log_file, log_level,
                               timeout, foreground,
                               retries, region):
    logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)
    tunnel_ports, output_ports = resolve_ports(tunnel_ports_str, output_ports_str,
                                               '-tp/--tunnel-port', '-op/--output-port')
    create_transmitting_tunnel_to_host(tunnel_host, tunnel_ports,
                                       output_host, output_ports,
                                       refresh_interval, pool_size,
                                       connection_timeout,
                                       direct, log_file, log_level,
                                       timeout, foreground, retries,
                                       region)


def create_transmitting_tunnel_to_host(tunnel_host, tunnel_ports,
                                       output_host, output_ports,
                                       refresh_interval, pool_size,
                                       connection_timeout,
                                       direct, log_file, log_level,
                                       timeout, foreground, retries,
                                       region=None):
    if foreground:
        conn_info = get_custom_conn_info(tunnel_host, region)
        create_foreground_transmitting_tunnel(tunnel_host, tunnel_ports, output_host, output_ports,
                                              refresh_interval, pool_size,
                                              connection_timeout, conn_info, direct, log_level, retries)
    else:
        create_background_tunnel(tunnel_ports, output_ports, tunnel_host, log_file, log_level, timeout)


def create_receiving_tunnel(input_ports_str, tunnel_ports_str,
                            log_file, log_level,
                            timeout, foreground):
    logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)
    input_ports, tunnel_ports = resolve_ports(input_ports_str, tunnel_ports_str,
                                              '-ip/--input-port', '-tp/--tunnel-port')
    create_receiving_tunnel_to_host(input_ports, tunnel_ports,
                                    log_file, log_level,
                                    timeout, foreground)


def create_receiving_tunnel_to_host(input_ports, tunnel_ports,
                                    log_file, log_level,
                                    timeout, foreground):
    if foreground:
        create_foreground_receiving_tunnel(input_ports, tunnel_ports)
    else:
        create_background_tunnel(input_ports, tunnel_ports, '127.0.0.1', log_file, log_level, timeout)


def resolve_ports(left_ports_str, right_ports_str, left_ports_arg, right_ports_arg):
    if not left_ports_str and not right_ports_str:
        raise RuntimeError('Either %s or %s option should be specified.' % (left_ports_arg, right_ports_arg))
    left_ports = parse_ports(left_ports_str)
    right_ports = parse_ports(right_ports_str)
    if not left_ports and not right_ports:
        raise RuntimeError('Either a single port (4567) or a range of ports (4567-4569) '
                           'can be specified using %s and %s options.' % (left_ports_arg, right_ports_arg))
    left_ports = left_ports or right_ports
    right_ports = right_ports or left_ports
    if len(right_ports) != len(left_ports):
        raise RuntimeError('The number of ({}) and ({}) ports should be the same.'
                           .format(len(right_ports), len(left_ports)))
    return left_ports, right_ports


def parse_ports(port_str):
    if not port_str:
        return []
    if isinstance(port_str, int):
        return [port_str]
    import re
    match = re.search('^(\\d+)$', port_str)
    if match:
        return [int(match.group(1))]
    match = re.search('^(\\d+)-(\\d+)$', port_str)
    if match:
        min_and_max_ports = sorted([int(match.group(1)), int(match.group(2))])
        return range(*[min_and_max_ports[0], min_and_max_ports[1] + 1])
    return []


def check_existing_tunnels(host_id, local_ports, remote_ports,
                           ssh, ssh_path, ssh_host, ssh_users, direct, log_file, timeout_stop,
                           keep_existing, keep_same, replace_existing, replace_different, ignore_owner,
                           region, retries, parse_tunnel_args):
    for existing_tunnel in find_tunnels(parse_tunnel_args):
        existing_tunnel_args = TunnelArgs.from_args(existing_tunnel.parsed_args)
        creating_tunnel_args = TunnelArgs(host_id=host_id, local_ports=local_ports, remote_ports=remote_ports,
                                          ssh=ssh, ssh_path=ssh_path, ssh_host=ssh_host, ssh_users=ssh_users,
                                          direct=direct)
        if not any(local_port in creating_tunnel_args.local_ports for local_port in existing_tunnel_args.local_ports):
            logging.debug('Skipping tunnel process #%s '
                          'because it has %s local ports but %s local ports are required...',
                          existing_tunnel.pid, stringify_ports(existing_tunnel_args.local_ports),
                          stringify_ports(local_ports))
            continue
        logging.info('Comparing the existing tunnel with the creating tunnel...')
        is_same_tunnel = creating_tunnel_args.compare(existing_tunnel_args)
        existing_tunnel_run_id = parse_run_identifier(existing_tunnel_args.host_id)
        existing_tunnel_remote_host = existing_tunnel_args.ssh_host \
                                      or 'pipeline-{}'.format(existing_tunnel_args.host_id)
        if replace_existing:
            logging.info('Trying to replace the existing tunnel...')
            if not ignore_owner and has_different_owner(existing_tunnel.owner):
                if is_same_tunnel:
                    raise TunnelError('Same tunnel already exists '
                                      'and it cannot be replaced because it was launched by {tunnel_owner} user '
                                      'which is not the same as the current user {current_owner}. \n\n'
                                      'Usually there is no need to replace the same tunnel '
                                      'but if it is required then you can either '
                                      'specify other local ports to use for the tunnel '
                                      'or stop the existing tunnel if you have sufficient permissions. '
                                      'In order to stop the existing tunnel '
                                      'execute the following command once. \n\n'
                                      '{pipe_command} tunnel stop -lp {local_ports} --ignore-owner \n'
                                      .format(tunnel_owner=existing_tunnel.owner,
                                              current_owner=get_current_user(),
                                              pipe_command=get_current_pipe_command(),
                                              local_ports=stringify_ports(local_ports)))
                else:
                    raise TunnelError('Different tunnel already exists on {local_ports} local ports '
                                      'and it cannot be replaced because it was launched by {tunnel_owner} user '
                                      'which is not the same as the current user {current_owner}. \n\n'
                                      'You can either specify other local ports to use for the tunnel '
                                      'or stop the existing tunnel if you have sufficient permissions. '
                                      'In order to stop the existing tunnel '
                                      'execute the following command once. \n\n'
                                      '{pipe_command} tunnel stop -lp {local_ports} --ignore-owner \n'
                                      .format(tunnel_owner=existing_tunnel.owner,
                                              current_owner=get_current_user(),
                                              pipe_command=get_current_pipe_command(),
                                              local_ports=stringify_ports(existing_tunnel_args.local_ports)))
            kill_tunnel(existing_tunnel.proc, existing_tunnel_args.local_ports, timeout_stop)
            continue
        if keep_existing:
            logging.info('Skipping tunnel establishing because the tunnel already exists...')
            if existing_tunnel_args.ssh and has_different_owner(existing_tunnel.owner):
                configure_ssh(existing_tunnel_run_id,
                              existing_tunnel_args.local_ports[0], existing_tunnel_args.remote_ports[0],
                              existing_tunnel_args.ssh_path, ssh_users, existing_tunnel_remote_host,
                              log_file, retries)
            sys.exit(0)
        if replace_different and not is_same_tunnel:
            logging.info('Trying to replace the existing tunnel because it is different...')
            if not ignore_owner and has_different_owner(existing_tunnel.owner):
                raise TunnelError('Different tunnel already exists on {local_ports} local ports '
                                  'and it cannot be replaced because it was launched by {tunnel_owner} user '
                                  'which is not the same as the current user {current_owner}. \n\n'
                                  'You can either specify other local ports to use for the tunnel '
                                  'or stop the existing tunnel if you have sufficient permissions. '
                                  'In order to stop the existing tunnel '
                                  'execute the following command once. \n\n'
                                  '{pipe_command} tunnel stop -lp {local_ports} --ignore-owner \n'
                                  .format(tunnel_owner=existing_tunnel.owner,
                                          current_owner=get_current_user(),
                                          pipe_command=get_current_pipe_command(),
                                          local_ports=stringify_ports(existing_tunnel_args.local_ports)))
            kill_tunnel(existing_tunnel.proc, existing_tunnel_args.local_ports, timeout_stop)
            continue
        if keep_same and is_same_tunnel:
            logging.info('Skipping tunnel establishing because the same tunnel already exists...')
            if existing_tunnel_args.ssh and has_different_owner(existing_tunnel.owner):
                configure_ssh(existing_tunnel_run_id,
                              existing_tunnel_args.local_ports[0], existing_tunnel_args.remote_ports[0],
                              existing_tunnel_args.ssh_path, ssh_users, existing_tunnel_remote_host,
                              log_file, retries)
            sys.exit(0)
        if has_different_owner(existing_tunnel.owner):
            if is_same_tunnel:
                raise TunnelError('Same tunnel already exists on {local_ports} local ports. '
                                  'It was launched by {tunnel_owner} user '
                                  'which is not the same as the current user {current_owner}. \n\n'
                                  'Usually there is no need to replace the same tunnel '
                                  'but if it is required then you can either '
                                  'specify other local ports to use for the tunnel '
                                  'or stop the existing tunnel if you have sufficient permissions. '
                                  'In order to stop the existing tunnel '
                                  'execute the following command once. \n\n'
                                  '{pipe_command} tunnel stop -lp {local_ports} --ignore-owner \n'
                                  .format(tunnel_owner=existing_tunnel.owner,
                                          current_owner=get_current_user(),
                                          pipe_command=get_current_pipe_command(),
                                          local_ports=stringify_ports(existing_tunnel_args.local_ports)))
            else:
                raise TunnelError('Different tunnel already exists on {local_ports} local ports. '
                                  'It was launched by {tunnel_owner} user '
                                  'which is not the same as the current user {current_owner}. \n\n'
                                  'You can either specify other local ports to use for the tunnel '
                                  'or stop the existing tunnel if you have sufficient permissions. '
                                  'In order to stop the existing tunnel '
                                  'execute the following command once. \n\n'
                                  '{pipe_command} tunnel stop -lp {local_ports} --ignore-owner \n'
                                  .format(tunnel_owner=existing_tunnel.owner,
                                          current_owner=get_current_user(),
                                          pipe_command=get_current_pipe_command(),
                                          local_ports=stringify_ports(existing_tunnel_args.local_ports)))
        else:
            if is_same_tunnel:
                raise TunnelError('Same tunnel already exists. \n\n'
                                  'Usually there is no need to replace the same tunnel '
                                  'but if it is required then you can either '
                                  'specify other local ports to use for the tunnel '
                                  'or stop the existing tunnel. '
                                  'In order to stop the existing tunnel '
                                  'execute the following command once. \n\n'
                                  '{pipe_command} tunnel stop -lp {local_ports} \n'
                                  .format(pipe_command=get_current_pipe_command(),
                                          local_ports=stringify_ports(existing_tunnel_args.local_ports)))
            else:
                raise TunnelError('Different tunnel already exists on {local_ports} local ports. \n\n'
                                  'You can either specify other local ports to use for the tunnel '
                                  'or stop the existing tunnel. '
                                  'In order to stop the existing tunnel '
                                  'execute the following command once. \n\n'
                                  '{pipe_command} tunnel stop -lp {local_ports} \n'
                                  .format(pipe_command=get_current_pipe_command(),
                                          local_ports=stringify_ports(existing_tunnel_args.local_ports)))


def check_local_ports(local_ports):
    procs_by_local_ports = get_procs_by_local_ports(local_ports)
    occupied_local_ports = [local_port for local_port in find_local_ports_which_cannot_be_occupied(local_ports)
                            if local_port not in procs_by_local_ports]
    err_msgs = []
    for local_port, proc in procs_by_local_ports.items():
        if proc.pid:
            err_msgs.append('Local port {} is occupied by process #{} with parent #{} owned by {} ({}).'
                            .format(local_port, proc.pid, proc.ppid or '-', proc.owner or '-',
                                    ' '.join(proc.args) or '-'))
        else:
            err_msgs.append('Local port {} is occupied by another process or is not allowed.'.format(local_port))
    for local_port in occupied_local_ports:
        err_msgs.append('Local port {} is occupied by another process or is not allowed.'.format(local_port))
    if err_msgs:
        raise TunnelError('Some of the local ports cannot be used: \n'
                          ' - {}\n\n'
                          'You can either specify other local ports to use for the tunnel '
                          'or stop the processes which occupy the required local ports.\n'
                          .format('\n - '.join(err_msgs)))


def find_local_ports_which_cannot_be_occupied(local_ports):
    logging.info('Trying to occupy local ports...')
    for local_port in local_ports:
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as server_socket:
            try:
                server_socket.bind((os.getenv('CP_CLI_TUNNEL_SERVER_ADDRESS', '0.0.0.0'), local_port))
            except Exception:
                logging.debug('Local port %s is occupied or not allowed.', local_port, exc_info=sys.exc_info())
                yield local_port


def get_procs_by_local_ports(local_ports):
    procs_by_local_ports = {}
    for local_port, proc in find_serving_procs(local_ports):
        procs_by_local_ports[local_port] = proc
    return procs_by_local_ports


def get_current_pipe_command():
    return sys.argv[0] if is_frozen() else (sys.executable + ' ' + sys.argv[0])


def has_different_owner(username):
    return username != get_current_user()


def get_current_user():
    import psutil
    return psutil.Process().username()


def configure_ssh(run_id, local_port, remote_port,
                  ssh_path, ssh_users, remote_host,
                  log_file, retries):
    def _establish_tunnel():
        pass
    conn_info = get_conn_info(run_id)
    ssh_keep = True
    if is_windows():
        configure_ssh_and_execute_on_windows(run_id, local_port, remote_port, conn_info,
                                             ssh_users, ssh_keep, remote_host,
                                             retries, _establish_tunnel)
    else:
        configure_ssh_and_execute_on_linux(run_id, local_port, remote_port, conn_info,
                                           ssh_path, ssh_users, ssh_keep, remote_host, log_file,
                                           retries, _establish_tunnel)


def get_parameter_value(proc_args, arg_index, arg_names):
    return proc_args[arg_index + 1] if proc_args[arg_index] in arg_names else None


def get_flag_value(proc_args, arg_index, arg_names):
    return proc_args[arg_index] in arg_names


def create_tunnel_to_run(run_id, local_ports, remote_ports, connection_timeout,
                         ssh, ssh_path, ssh_host, ssh_users, ssh_keep, direct, log_file, log_level,
                         timeout, foreground, retries, region=None):
    conn_info = get_conn_info(run_id, region)
    if conn_info.sensitive:
        raise RuntimeError('Tunnel connections are only allowed to non sensitive runs.')
    remote_host = ssh_host or 'pipeline-{}'.format(run_id)
    if foreground:
        if ssh:
            create_foreground_tunnel_with_ssh(run_id, local_ports, remote_ports, connection_timeout, conn_info,
                                              ssh_path, ssh_users, ssh_keep,
                                              remote_host, direct, log_file, log_level, retries)
        else:
            create_foreground_tunnel(run_id, local_ports, remote_ports, connection_timeout, conn_info,
                                     remote_host, direct, log_level, retries)
    else:
        create_background_tunnel(local_ports, remote_ports, remote_host, log_file, log_level, timeout)


def create_tunnel_to_host(host_id, local_ports, remote_ports, connection_timeout,
                          direct, log_file, log_level,
                          timeout, foreground, retries, region=None):
    if foreground:
        conn_info = get_custom_conn_info(host_id, region)
        create_foreground_tunnel(host_id, local_ports, remote_ports, connection_timeout, conn_info,
                                 host_id, direct, log_level, retries)
    else:
        create_background_tunnel(local_ports, remote_ports, host_id, log_file, log_level, timeout)


def create_background_tunnel(local_ports, remote_ports, remote_host, log_file, log_level, timeout):
    import subprocess
    if len(local_ports) > 1:
        logging.info('Launching background tunnel %s-%s:%s:%s-%s...',
                     local_ports[0], local_ports[-1], remote_host, remote_ports[0], remote_ports[-1])
    else:
        logging.info('Launching background tunnel %s:%s:%s...', local_ports[0], remote_host, remote_ports[0])
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
        executable = sys_args if is_frozen() else [sys.executable] + sys_args
        executable += TUNNEL_FOREGROUND_ARGS
        executable += TUNNEL_IGNORE_EXISTING_ARGS
        tunnel_proc = subprocess.Popen(executable, stdin=stdin, stdout=output, stderr=subprocess.STDOUT,
                                       cwd=os.getcwd(), env=os.environ.copy(), creationflags=creationflags)
        if stdin:
            os.close(stdin)
        wait_for_background_tunnel(tunnel_proc, local_ports, timeout)


def wait_for_background_tunnel(tunnel_proc, local_ports, timeout, polling_delay=1):
    attempts = int(timeout / polling_delay)
    logging.info('Waiting for tunnel process #%s to listen all the required ports...', tunnel_proc.pid)
    while attempts > 0:
        time.sleep(polling_delay)
        if tunnel_proc.poll() is not None:
            raise RuntimeError('Failed to serve tunnel in background. '
                               'Tunnel exited with return code {}.'
                               .format(tunnel_proc.returncode))
        if is_tunnel_ready(tunnel_proc.pid, local_ports):
            logging.info('Background tunnel is initialized. Exiting...')
            return
        logging.debug('Background tunnel is not initialized yet. '
                      'Only %s attempts remain left...', attempts)
        attempts -= 1
    raise RuntimeError('Failed to serve tunnel in background. '
                       'Tunnel is not initialized after {} seconds.'
                       .format(timeout))


def is_tunnel_ready(tunnel_pid, local_ports):
    serving_procs = list(find_serving_procs(local_ports))
    return is_tunnel_listen_all_ports(tunnel_pid, local_ports, serving_procs)


def find_serving_procs(local_ports):
    return find_serving_procs_on_mac(local_ports) if is_mac() else find_serving_procs_on_lin_and_win(local_ports)


def find_serving_procs_on_mac(local_ports):
    logging.info('Searching for processes listening local ports...')
    procs_by_pid = {}
    for local_port in local_ports:
        listening_pid = get_trimmed_digit_str(perform_command(['lsof', '-t',
                                                               '-i', 'TCP:' + str(local_port),
                                                               '-s', 'TCP:LISTEN'],
                                                              fail_on_error=False))
        if listening_pid:
            yield local_port, get_or_set(procs_by_pid, listening_pid, get_proc_details)


def find_serving_procs_on_lin_and_win(local_ports):
    import psutil
    logging.info('Searching for processes listening local ports...')
    procs_by_pid = {}
    for net_connection in psutil.net_connections():
        if net_connection.laddr and net_connection.laddr.port in local_ports:
            if net_connection.pid:
                yield net_connection.laddr.port, get_or_set(procs_by_pid, net_connection.pid, get_proc_details)
            else:
                yield net_connection.laddr.port, SystemProcess()


def get_or_set(values, key, get_value):
    value = values.get(key)
    if not value:
        values[key] = value = get_value(key)
    return value


def get_proc_details(listening_pid):
    import psutil
    try:
        proc = psutil.Process(listening_pid)
        proc_ppid = proc.ppid()
        proc_owner = UNKNOWN_USER
        try:
            proc_owner = proc.username()
        except Exception:
            logging.debug('Process #%s owner retrieval has failed.', listening_pid,
                          exc_info=sys.exc_info())
            return SystemProcess(pid=listening_pid, ppid=proc_ppid, owner=proc_owner)
        try:
            proc_args = proc.cmdline()
        except psutil.AccessDenied:
            logging.debug('Process #%s details access is denied.', proc.pid)
            return SystemProcess(pid=listening_pid, ppid=proc_ppid, owner=proc_owner)
        logging.info('Process #%s details were retrieved (%s).', proc.pid, ' '.join(proc_args))
        return SystemProcess(pid=listening_pid, ppid=proc_ppid, owner=proc_owner, args=proc_args)
    except Exception:
        logging.debug('Process #%s details retrieval has failed.', listening_pid,
                      exc_info=sys.exc_info())
        return SystemProcess(pid=listening_pid)


def get_trimmed_digit_str(digit_str):
    return int(digit_str.strip()) if digit_str and digit_str.strip().isdigit() else None


def is_tunnel_listen_all_ports(tunnel_pid, local_ports, serving_procs):
    if len(serving_procs) != len(local_ports):
        logging.debug('Waiting for all required ports (%s/%s) to be listened...',
                      len(serving_procs), len(local_ports))
        return False
    serving_proc_pids = [proc.pid for local_port, proc in serving_procs]
    if len(set(serving_proc_pids)) != 1:
        logging.debug('Waiting for a single process to listen all required ports...')
        return False
    _, serving_proc = serving_procs[0]
    logging.debug('Found a process #%s and its parent #%s that listen all required ports.',
                  serving_proc.pid or '-', serving_proc.ppid or '-')
    return tunnel_pid in [serving_proc.pid, serving_proc.ppid]


def create_foreground_tunnel_with_ssh(run_id, local_ports, remote_ports, connection_timeout, conn_info,
                                      ssh_path, ssh_users, ssh_keep, remote_host, direct, log_file, log_level, retries):
    def _establish_tunnel():
        create_foreground_tunnel(run_id, local_ports, remote_ports, connection_timeout, conn_info,
                                 remote_host, direct, log_level, retries)
    if is_windows():
        configure_ssh_and_execute_on_windows(run_id, local_ports[0], remote_ports[0], conn_info,
                                             ssh_users, ssh_keep, remote_host,
                                             retries, _establish_tunnel)
    else:
        configure_ssh_and_execute_on_linux(run_id, local_ports[0], remote_ports[0], conn_info,
                                           ssh_path, ssh_users, ssh_keep, remote_host, log_file,
                                           retries, _establish_tunnel)


def configure_ssh_and_execute_on_windows(run_id, local_port, remote_port, conn_info,
                                         ssh_users, ssh_keep, remote_host,
                                         retries, func):
    logging.info('Configuring putty and openssh passwordless ssh...')
    passwordless_config = PasswordlessSSHConfig(run_id, conn_info, ssh_users)
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
        func()
    except Exception:
        logging.exception('Error occurred while trying to configure passwordless ssh')
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


def configure_ssh_and_execute_on_linux(run_id, local_port, remote_port, conn_info,
                                       ssh_path, ssh_users, ssh_keep, remote_host, log_file,
                                       retries, func):
    logging.info('Configuring openssh passwordless ssh...')
    passwordless_config = PasswordlessSSHConfig(run_id, conn_info, ssh_users, ssh_path)
    if not os.path.exists(passwordless_config.local_openssh_path):
        os.makedirs(passwordless_config.local_openssh_path, mode=stat.S_IRWXU)
    if not os.path.exists(passwordless_config.local_keys_path):
        os.makedirs(passwordless_config.local_keys_path, mode=stat.S_IRWXU)
    try:
        logging.info('Initializing passwordless ssh %s:%s:%s...', local_port, remote_host, remote_port)
        generate_remote_openssh_and_putty_keys(run_id, retries, passwordless_config)
        copy_remote_putty_and_private_keys(run_id, retries, passwordless_config)
        add_record_to_openssh_config(local_port, remote_host, passwordless_config)
        copy_remote_openssh_public_key_to_openssh_known_hosts(run_id, local_port, retries, passwordless_config)
        func()
    except Exception:
        logging.exception('Error occurred while trying to configure passwordless ssh')
        raise
    finally:
        if not ssh_keep:
            logging.info('Deinitializing passwordless ssh %s:%s:%s...', local_port, remote_host, remote_port)
            remove_remote_openssh_and_putty_keys(run_id, retries, passwordless_config)
            remove_remote_openssh_public_key_from_openssh_known_hosts(local_port, passwordless_config)
            remove_record_from_openssh_config(remote_host, passwordless_config)
            remove_ssh_keys(passwordless_config.local_private_key_path,
                            passwordless_config.local_ppk_key_path,
                            passwordless_config.local_host_ed25519_public_key_path)


@socketclosing
def create_foreground_tunnel(inputs, run_id, local_ports, remote_ports, connection_timeout, conn_info,
                             remote_host, direct, log_level, retries,
                             chunk_size=4096, server_delay=0.0001):
    logging.info('Initializing tunnel %s:%s:%s...',
                 stringify_ports(local_ports), remote_host, stringify_ports(remote_ports))
    listeners = []
    for listener_socket in serve_local_ports(local_ports):
        add_to_socket_lists(listener_socket, inputs, listeners)
    listener_local_ports = dict(zip(inputs, local_ports))
    listener_remote_ports = dict(zip(inputs, remote_ports))
    channel = {}
    logging.info('Serving tunnel...')
    while True:
        time.sleep(server_delay)
        logging.info('Waiting for connections...')
        inputs_ready, _, _ = select.select(inputs, [], [])
        for input in inputs_ready:
            if input in listeners:
                local_port = listener_local_ports[input]
                remote_port = listener_remote_ports[input]
                try:
                    logging.info('Initializing client connection %s:%s:%s...',
                                 local_port, remote_host, remote_port)
                    client_socket, address = input.accept()
                except KeyboardInterrupt:
                    raise
                except Exception:
                    logging.exception('Cannot establish client connection %s:%s:%s.',
                                      local_port, remote_host, remote_port)
                    break
                try:
                    tunnel_socket = connect(conn_info, remote_host, remote_port, local_port,
                                            direct, connection_timeout, retries)
                except KeyboardInterrupt:
                    raise
                except Exception:
                    close_socket(client_socket)
                    break
                add_to_socket_lists(client_socket, inputs)
                add_to_socket_lists(tunnel_socket, inputs)
                add_to_channel(client_socket, tunnel_socket, channel)
                break

            read_data, sent_data = exchange_data(input, channel, chunk_size)
            if not read_data or not sent_data:
                logging.info('Closing client and tunnel connections...')
                output = channel[input] if input in channel else None
                close_sockets(input, output)
                remove_from_socket_list(input, inputs)
                remove_from_socket_list(output, inputs)
                remove_from_socket_dict(input, channel)
                remove_from_socket_dict(output, channel)
                break


def serve_local_ports(local_ports):
    for local_port in local_ports:
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            server_socket.bind((os.getenv('CP_CLI_TUNNEL_SERVER_ADDRESS', '0.0.0.0'), local_port))
            server_socket.listen(5)
            yield server_socket
        except Exception:
            server_socket.close()
            logging.debug('Local port %s is occupied or not allowed.', local_port, exc_info=sys.exc_info())
            raise TunnelError('Local port {} is occupied or not allowed.'.format(local_port))


def generate_remote_openssh_and_putty_keys(run_id, retries, passwordless_config):
    logging.info('Generating tunnel remote ssh keys and copying ssh public key to authorized keys...')
    exit_code = run_ssh(run_id,
                        """
                        mkdir -p $(dirname {remote_private_key_path})
                        ssh-keygen -t rsa -f {remote_private_key_path} -N "" -q
                        for authorized_user in {authorized_users}; do
                            user_home_path="$(getent passwd "$authorized_user" | cut -d: -f6)"
                            user_openssh_path="$user_home_path/.ssh"
                            user_authorized_keys_path="$user_openssh_path/authorized_keys"
                            mkdir -p "$user_openssh_path"
                            touch "$user_authorized_keys_path"
                            chown -R "$authorized_user:$authorized_user" "$user_openssh_path"
                            chmod 700 "$user_openssh_path"
                            chmod 600 "$user_authorized_keys_path"
                            cat "{remote_public_key_path}" | tee -a "$user_authorized_keys_path" > /dev/null
                        done
                        if ! command -v puttygen; then
                            wget -q "${{GLOBAL_DISTRIBUTION_URL:-"https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/"}}tools/putty/puttygen.tgz" -O "/tmp/puttygen.tgz"
                            tar -zxf "/tmp/puttygen.tgz" -C "${{CP_USR_BIN:-/usr/cpbin}}"
                            rm -f "/tmp/puttygen.tgz"
                        fi
                        if ! command -v puttygen; then apt-get -y install putty-tools; fi
                        if ! command -v puttygen; then yum -y install putty; fi
                        puttygen {remote_private_key_path} -o {remote_ppk_key_path} -O private
                        """.format(remote_public_key_path=passwordless_config.remote_public_key_path,
                                   remote_private_key_path=passwordless_config.remote_private_key_path,
                                   remote_ppk_key_path=passwordless_config.remote_ppk_key_path,
                                   authorized_users=' '.join(passwordless_config.remote_authorized_users)),
                        user=DEFAULT_SSH_USER, retries=retries)
    if exit_code:
        raise RuntimeError('Generating tunnel remote ssh keys and copying ssh public key to authorized keys '
                           'have failed with {} exit code'
                           .format(exit_code))


def remove_remote_openssh_and_putty_keys(run_id, retries, passwordless_config):
    logging.info('Deleting remote ssh keys...')
    remove_ssh_keys_from_run_command = ''
    for remote_authorized_user in passwordless_config.remote_authorized_users:
        remove_ssh_keys_from_run_command += (
            """
            [ -f {public_key_path} ] &&
            user_home_path="$(getent passwd "{authorized_user}" | cut -d: -f6)" &&
            user_openssh_path="$user_home_path/.ssh" &&
            user_authorized_keys_path="$user_openssh_path/authorized_keys" &&
            user_authorized_keys_temp_path="${{user_authorized_keys_path}}_$RANDOM" &&
            cat {public_key_path} | xargs -I {{}} grep -v "{{}}" "$user_authorized_keys_path" > "$user_authorized_keys_temp_path" || true &&
            chown -R "{authorized_user}:{authorized_user}" "$user_openssh_path" &&
            chmod 600 "$user_authorized_keys_path" &&
            cp "$user_authorized_keys_temp_path" "$user_authorized_keys_path" &&
            rm "$user_authorized_keys_temp_path";
            """.format(public_key_path=passwordless_config.remote_public_key_path,
                       private_key_path=passwordless_config.remote_private_key_path,
                       ppk_key_path=passwordless_config.remote_ppk_key_path,
                       authorized_user=remote_authorized_user))
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


def remove_ssh_keys(*key_paths):
    logging.info('Removing tunnel ssh keys...')
    for key_path in key_paths:
        if os.path.exists(key_path):
            os.remove(key_path)


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


def kill_tunnels(host_id, local_ports_str, timeout_stop, force, ignore_owner, log_level, parse_tunnel_args):
    logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)
    local_ports = parse_ports(local_ports_str)
    if local_ports_str and not local_ports:
        raise RuntimeError('Either a single port (4567) or a range of ports (4567-4569) '
                           'can be specified using -lp/--local-port option.')
    for tunnel in find_tunnels(parse_tunnel_args):
        tunnel_args = TunnelArgs.from_args(tunnel.parsed_args)
        if host_id and tunnel_args.host_id != host_id:
            logging.debug('Skipping tunnel process #%s '
                          'because it has %s host but %s host is required.',
                          tunnel.pid, tunnel_args.host_id, host_id)
            continue
        if local_ports and not any(local_port in local_ports for local_port in tunnel_args.local_ports):
            logging.debug('Skipping tunnel process #%s '
                          'because it has %s local ports but %s local ports are required...',
                          tunnel.pid, stringify_ports(tunnel_args.local_ports), stringify_ports(local_ports))
            continue
        if not ignore_owner and has_different_owner(tunnel.owner):
            logging.debug('Skipping tunnel process #%s '
                          'because it has %s owner but %s owner is required...',
                          tunnel.pid, tunnel.owner, get_current_user())
            continue
        kill_tunnel(tunnel.proc, tunnel_args.local_ports, timeout_stop, force)


def list_tunnels(log_level, parse_tunnel_args):
    logging.basicConfig(level=log_level or logging.ERROR, format=DEFAULT_LOGGING_FORMAT)
    tunnels_table = prettytable.PrettyTable()
    tunnels_table.field_names = ['PID', 'PPID', 'Owner', 'Host', 'Local Ports', 'Remote Ports']
    tunnels_table.sortby = 'PID'
    tunnels_table.align = 'l'
    tunnels = list(find_tunnels(parse_tunnel_args))
    tunnels_by_ppid = {tunnel.ppid: tunnel for tunnel in tunnels}
    for tunnel in tunnels:
        tunnel_child = tunnels_by_ppid.get(tunnel.pid)
        if tunnel_child:
            logging.debug('Skipping tunnel process #%s because it is a parent of tunnel process #%s...',
                          tunnel.pid, tunnel_child.pid)
            continue
        tunnel_args = TunnelArgs.from_args(tunnel.parsed_args)
        tunnels_table.add_row([tunnel.pid,
                               tunnel.ppid,
                               tunnel.owner,
                               tunnel_args.host_id,
                               stringify_ports(tunnel_args.local_ports),
                               stringify_ports(tunnel_args.remote_ports)])
    click.echo(tunnels_table)


def stringify_ports(ports):
    if not ports:
        return ''
    if len(ports) == 1:
        return str(ports[0])
    return '{}-{}'.format(ports[0], ports[-1])


def find_tunnels(parse_tunnel_args):
    import psutil
    logging.info('Searching for tunnel processes...')
    current_pids = get_current_pids()
    for proc in psutil.process_iter():
        proc_pid = proc.pid
        try:
            proc_name = proc.name()
            if PIPE_PROC_SUBSTR not in proc_name \
                    and PYTHON_PROC_SUBSTR not in proc_name:
                continue
            if proc_pid in current_pids:
                logging.debug('Skipping process #%s because it is current process or its parent...', proc_pid)
                continue
            try:
                proc_args = proc.cmdline()
            except psutil.AccessDenied:
                logging.debug('Skipping process #%s because its details access is denied...', proc_pid)
                continue
            if PYTHON_PROC_SUBSTR in proc_name \
                    and not any(proc_arg.endswith(PIPE_SCRIPT_NAME) for proc_arg in proc_args):
                continue
            if not all(required_arg in proc_args for required_arg in TUNNEL_REQUIRED_ARGS):
                logging.debug('Skipping process #%s because it is not pipe tunnel process...', proc_pid)
                continue
            proc_parsed_args = parse_tunnel_proc_args(proc_args, parse_tunnel_args)
            if not proc_parsed_args:
                logging.debug('Skipping tunnel process #%s because its arguments cannot be parsed...', proc_pid)
                continue
            logging.info('Tunnel process #%s was found (%s).', proc_pid, ' '.join(proc_args))
            proc_ppid = proc.ppid()
            proc_owner = UNKNOWN_USER
            try:
                proc_owner = proc.username()
            except Exception:
                logging.debug('Tunnel process #%s owner retrieval has failed. Using default user instead...', proc_pid,
                              exc_info=sys.exc_info())
            yield TunnelProcess(pid=proc_pid, ppid=proc_ppid, owner=proc_owner, args=proc_args,
                                proc=proc, parsed_args=proc_parsed_args)
        except Exception:
            logging.debug('Skipping process #%s because its details retrieval has failed.', proc_pid,
                          exc_info=sys.exc_info())


def get_current_pids():
    import psutil
    current_pids = [os.getpid()]
    try:
        for _ in range(3):
            current_pids.append(psutil.Process(current_pids[-1]).ppid())
    except psutil.NoSuchProcess:
        pass
    return current_pids


def parse_tunnel_proc_args(proc_args, parse_start_tunnel_arguments):
    try:
        for i in range(len(proc_args)):
            if proc_args[i] == 'start':
                return parse_start_tunnel_arguments(proc_args[i + 1:])
    except Exception:
        logging.debug('Existing tunnel process arguments parsing has failed.', exc_info=sys.exc_info())
    return {}


def kill_tunnel(proc, local_ports, timeout, force=False):
    logging.info('Killing tunnel process #%s...', proc.pid)
    kill_process(proc, timeout, force)
    wait_for_local_ports(local_ports, timeout)


def kill_process(proc, timeout, force):
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


def wait_for_local_ports(local_ports, timeout, polling_delay=1):
    attempts = int(timeout / polling_delay)
    logging.info('Waiting for %s local ports to become unoccupied...', stringify_ports(local_ports))
    while attempts > 0:
        time.sleep(polling_delay)
        if not list(find_local_ports_which_cannot_be_occupied(local_ports)):
            logging.info('Local ports %s are not occupied anymore.', stringify_ports(local_ports))
            return
        logging.debug('Local ports %s are still occupied. '
                      'Only %s attempts remain left...', stringify_ports(local_ports), attempts)
        attempts -= 1
    raise TunnelError('Local ports are still occupied after {} seconds.'.format(timeout))


def send_signal_to_process(proc, signal, timeout):
    if proc.is_running():
        proc.send_signal(signal)
        proc.wait(timeout if timeout else None)


def run_scp_upload(run_id, source, destination, recursive=False, quiet=True, user=None, retries=None, region=None):
    with closing(setup_authenticated_paramiko_transport(run_id, user, retries, region)) as transport:
        with closing(SCPClient(transport, progress=None if quiet else build_scp_progress())) as scp:
            scp.put(source, destination, recursive=recursive)


def run_scp_download(run_id, source, destination, recursive=False, quiet=True, user=None, retries=None, region=None):
    with closing(setup_authenticated_paramiko_transport(run_id, user, retries, region)) as transport:
        with closing(SCPClient(transport, progress=None if quiet else build_scp_progress())) as scp:
            scp.get(source, destination, recursive=recursive)


def build_scp_progress():
    from src.utilities.progress_bar import ProgressPercentage

    progresses = {}

    def scp_progress(filename, size, total):
        progress = progresses.get(filename, None)
        if not progress:
            progress = progresses[filename] = ProgressPercentage(filename, size)
        progress(total - progress._seen_so_far_in_bytes)

    return scp_progress


@restarting
@socketclosing
def create_foreground_receiving_tunnel(inputs, input_ports, tunnel_ports,
                                       chunk_size=4096, server_delay=0.0001):
    logging.info('Initializing receiving tunnel %s:%s:%s...',
                 stringify_ports(input_ports), '127.0.0.1', stringify_ports(tunnel_ports))
    input_listeners = []
    tunnel_listeners = []
    tunnel_client_ports = {tunnel_port: [] for tunnel_port in tunnel_ports}
    ports_mapping = dict(list(zip(input_ports, tunnel_ports)) + list(zip(tunnel_ports, input_ports)))
    for listener_socket in serve_local_ports(input_ports):
        add_to_socket_lists(listener_socket, inputs, input_listeners)
    for listener_socket in serve_local_ports(tunnel_ports):
        add_to_socket_lists(listener_socket, inputs, tunnel_listeners)
    input_listener_ports = dict(zip(input_listeners, input_ports))
    tunnel_listener_ports = dict(zip(tunnel_listeners, tunnel_ports))
    channel = {}
    logging.info('Serving tunnel...')
    while True:
        time.sleep(server_delay)
        logging.info('Waiting for connections...')
        inputs_ready, _, _ = select.select(inputs, [], [])

        for input in inputs_ready:
            if input in tunnel_listeners:
                tunnel_port = tunnel_listener_ports[input]
                try:
                    logging.info('Initializing tunnel client connection on %s...', tunnel_port)
                    client_socket, address = input.accept()
                except KeyboardInterrupt:
                    raise
                except Exception:
                    logging.exception('Cannot establish tunnel client connection on %s.', tunnel_port)
                    break
                add_to_socket_lists(client_socket, inputs)
                add_socket_to_multidict(client_socket, tunnel_port, tunnel_client_ports)
                break

            if input in input_listeners:
                input_port = input_listener_ports[input]
                tunnel_port = ports_mapping[input_port]
                try:
                    logging.info('Initializing input client connection on %s...', input_port)
                    client_socket, address = input.accept()
                except KeyboardInterrupt:
                    raise
                except Exception:
                    logging.exception('Cannot establish input client connection on %s.', input_port)
                    break
                if not tunnel_client_ports[tunnel_port]:
                    logging.error('There are no more tunnel client connections available on %s '
                                  'for input client connection on %s.', tunnel_port, input_port)
                    close_socket(client_socket)
                    break
                remote_client_socket = tunnel_client_ports[tunnel_port].pop()
                add_to_socket_lists(client_socket, inputs)
                add_to_channel(client_socket, remote_client_socket, channel)
                break

            read_data, sent_data = exchange_data(input, channel, chunk_size)
            if not read_data or not sent_data:
                logging.info('Closing client and tunnel connections...')
                output = channel[input] if input in channel else None
                close_sockets(input, output)
                remove_from_socket_lists(input, inputs)
                remove_from_socket_lists(output, inputs)
                remove_from_socket_multidict(input, tunnel_client_ports)
                remove_from_socket_multidict(output, tunnel_client_ports)
                remove_from_socket_dict(input, channel)
                remove_from_socket_dict(output, channel)
                break


@restarting
@socketclosing
def create_foreground_transmitting_tunnel(inputs, tunnel_host, tunnel_ports, output_host, output_ports,
                                          refresh_interval, pool_size,
                                          connection_timeout, conn_info, direct, log_level, retries,
                                          chunk_size=4096, server_delay=0.0001):
    logging.info('Initializing transmitting tunnel %s:%s:%s:%s...',
                 stringify_ports(tunnel_ports), tunnel_host,
                 output_host, stringify_ports(output_ports))
    tunnel_clients = []
    tunnel_client_ports = {}
    ports_mapping = dict(list(zip(tunnel_ports, output_ports)) + list(zip(output_ports, tunnel_ports)))
    for tunnel_port in tunnel_ports:
        output_port = ports_mapping[tunnel_port]
        for i in range(0, pool_size):
            client_socket = connect(conn_info, tunnel_host, tunnel_port, output_port,
                                    direct, connection_timeout, retries)
            add_to_socket_lists(client_socket, inputs, tunnel_clients)
            add_to_socket_dict(client_socket, tunnel_port, tunnel_client_ports)
    latest_refresh_time = time.time()
    channel = {}
    logging.info('Serving tunnel...')
    while True:
        time.sleep(server_delay)
        logging.info('Waiting for connections...')
        inputs_ready, _, _ = select.select(inputs, [], [], refresh_interval)

        if not inputs_ready:
            current_refresh_time = time.time()
            if current_refresh_time - latest_refresh_time > refresh_interval:
                latest_refresh_time = current_refresh_time
                logging.info('Refreshing socket connections...')
                for remote_client in list(tunnel_clients):
                    tunnel_port = tunnel_client_ports[remote_client]
                    output_port = ports_mapping[tunnel_port]
                    close_socket(remote_client)
                    remove_from_socket_lists(remote_client, inputs, tunnel_clients)
                    remove_from_socket_dict(remote_client, tunnel_client_ports)
                    client_socket = connect(conn_info, tunnel_host, tunnel_port, output_port,
                                            direct, connection_timeout, retries)
                    add_to_socket_lists(client_socket, inputs, tunnel_clients)
                    add_to_socket_dict(client_socket, tunnel_port, tunnel_client_ports)

        for input in inputs_ready:
            if input in tunnel_clients and input not in channel:
                tunnel_port = tunnel_client_ports[input]
                output_port = ports_mapping[tunnel_port]
                logging.info('Connecting to output host at %s:%s...', output_host, output_port)
                try:
                    client_socket = direct_connect((output_host, output_port),
                                                   timeout=connection_timeout,
                                                   retries=retries)
                except KeyboardInterrupt:
                    raise
                except Exception:
                    logging.exception('Cannot establish output connection %s:%s:%s.',
                                      tunnel_port, output_host, output_port)
                    close_socket(input)
                    remove_from_socket_lists(input, inputs, tunnel_clients)
                    logging.info('Reconnecting to remote host at %s:%s...', tunnel_host, stringify_ports(tunnel_ports))
                    client_socket = connect(conn_info, tunnel_host, tunnel_port, output_port,
                                            direct, connection_timeout, retries)
                    add_to_socket_lists(client_socket, inputs, tunnel_clients)
                    add_to_socket_dict(client_socket, tunnel_port, tunnel_client_ports)
                    continue
                add_to_socket_lists(client_socket, inputs)
                add_to_channel(input, client_socket, channel)

            read_data, sent_data = exchange_data(input, channel, chunk_size)
            if not read_data or not sent_data:
                logging.info('Closing client and tunnel connections...')
                output = channel[input] if input in channel else None
                tunnel_port = tunnel_client_ports[input] if input in tunnel_client_ports else \
                    tunnel_client_ports[output] if output in tunnel_client_ports else \
                    None
                output_port = ports_mapping[tunnel_port] if tunnel_port else None
                close_sockets(input, output)
                remove_from_socket_lists(input, inputs, tunnel_clients)
                remove_from_socket_lists(output, inputs, tunnel_clients)
                remove_from_socket_dicts(input, channel, tunnel_client_ports)
                remove_from_socket_dicts(output, channel, tunnel_client_ports)
                if output_port and tunnel_port:
                    logging.info('Reconnecting to remote host at ' + tunnel_host + ':' + str(tunnel_ports) + '...')
                    client_socket = connect(conn_info, tunnel_host, tunnel_port, output_port,
                                            direct, connection_timeout, retries)
                    add_to_socket_lists(client_socket, inputs, tunnel_clients)
                    add_to_socket_dict(client_socket, tunnel_port, tunnel_client_ports)
                break


def connect(conn_info, remote_host, remote_port, local_port, direct, connection_timeout, retries):
    target_endpoint = (conn_info.ssh_endpoint[0], remote_port)
    proxy_endpoint = (os.getenv('CP_CLI_TUNNEL_PROXY_HOST', conn_info.ssh_proxy[0]),
                      int(os.getenv('CP_CLI_TUNNEL_PROXY_PORT', conn_info.ssh_proxy[1])))
    try:
        logging.info('Initializing tunnel connection %s:%s:%s...',
                     local_port, remote_host, remote_port)
        if direct:
            return direct_connect(target_endpoint,
                                  timeout=connection_timeout,
                                  retries=retries)
        else:
            return http_proxy_tunnel_connect(proxy_endpoint, target_endpoint,
                                             timeout=connection_timeout,
                                             retries=retries)
    except KeyboardInterrupt:
        raise
    except Exception:
        logging.exception('Cannot establish tunnel connection %s:%s:%s.',
                          local_port, remote_host, remote_port)
        raise


def exchange_data(input, channel, chunk_size):
    logging.debug('Reading data...')
    read_data = None
    sent_data = None
    try:
        read_data = input.recv(chunk_size)
    except KeyboardInterrupt:
        raise
    except Exception:
        logging.exception('Cannot read data from socket')
    if read_data:
        logging.debug('Writing data...')
        try:
            channel[input].send(read_data)
            sent_data = read_data
        except KeyboardInterrupt:
            raise
        except Exception:
            logging.exception('Cannot write data to socket')
    return read_data, sent_data


def add_to_socket_lists(client_socket, *client_socket_lists):
    for client_socket_list in client_socket_lists:
        client_socket_list.append(client_socket)


def remove_from_socket_lists(client_socket, *client_socket_lists):
    for client_socket_list in client_socket_lists:
        remove_from_socket_list(client_socket, client_socket_list)


def remove_from_socket_list(client_socket, client_socket_list):
    if client_socket and client_socket in client_socket_list:
        client_socket_list.remove(client_socket)


def add_to_socket_dict(client_socket, value, client_socket_dict):
    client_socket_dict[client_socket] = value


def remove_from_socket_dicts(client_socket, *client_socket_dicts):
    for client_socket_dict in client_socket_dicts:
        remove_from_socket_dict(client_socket, client_socket_dict)


def remove_from_socket_dict(client_socket, client_socket_dict):
    if client_socket and client_socket in client_socket_dict:
        del client_socket_dict[client_socket]


def add_socket_to_multidict(client_socket, value, client_socket_multidict):
    client_socket_multidict[value].append(client_socket)


def remove_from_socket_multidict(client_socket, client_socket_multidict):
    for _, client_sockets in client_socket_multidict.items():
        remove_from_socket_list(client_socket, client_sockets)


def add_to_channel(left_client_socket, right_client_socket, channel):
    channel[left_client_socket] = right_client_socket
    channel[right_client_socket] = left_client_socket


def close_sockets(*client_sockets):
    for client_socket in client_sockets:
        close_socket(client_socket)


def close_socket(client_socket):
    if client_socket:
        client_socket.close()
