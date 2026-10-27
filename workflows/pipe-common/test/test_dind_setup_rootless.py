# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Covers the rootless mode of DinD (issue #4683): the helpers of shell/dind_setup_rootless, and the
# launch.sh functions which publish the DinD init line and reject Kubernetes in the rootless mode.
# Each test runs bash with PATH limited to a sandbox bin/ dir: stub commands that record their calls,
# plus links to the few real tools the code needs. Needs Linux (GNU tools).

import os
import socket
import stat
import subprocess
import tarfile

import pytest

try:
    from shlex import quote
except ImportError:
    from pipes import quote

SHELL_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', 'shell'))
LIBRARY = os.path.join(SHELL_DIR, 'dind_setup_rootless')
REAL_TOOLS = ['awk', 'bash', 'cat', 'chmod', 'dirname', 'grep', 'gzip', 'ln', 'mkdir', 'mktemp', 'rm',
              'sed', 'tar', 'touch']
# id -u for the users the tests use
ID_STUB = '\n'.join([
    'case "$2" in',
    '    alice) echo 1001 ;;',
    '    root) echo 0 ;;',
    '    *) exit 1 ;;',
    'esac'])


def _which(name):
    for directory in os.environ.get('PATH', '').split(os.pathsep):
        path = os.path.join(directory, name)
        if os.path.isfile(path) and os.access(path, os.X_OK):
            return path
    raise RuntimeError('%s is not found' % name)


BASH = _which('bash')


class Sandbox(object):

    def __init__(self, root):
        self.root = str(root)
        self.bin = os.path.join(self.root, 'bin')
        self.calls_file = os.path.join(self.root, 'calls.log')
        os.makedirs(self.bin)
        open(self.calls_file, 'w').close()
        for tool in REAL_TOOLS:
            os.symlink(_which(tool), os.path.join(self.bin, tool))
        with open(os.path.join(SHELL_DIR, 'util_version_compare')) as script:
            self.write_executable(os.path.join(self.bin, 'util_version_compare'), script.read())
        self.stub('id', script=ID_STUB)

    def path(self, *parts):
        return os.path.join(self.root, *parts)

    def write_executable(self, path, contents):
        with open(path, 'w') as script:
            script.write(contents)
        os.chmod(path, 0o755)

    def stub(self, name, exit_code=0, output=None, script=None):
        """Replaces a command: records "<name> <args>" and prints the output, runs the script."""
        lines = ['#!%s' % BASH, 'echo "%s $*" >> %s' % (name, quote(self.calls_file))]
        if output is not None:
            output_file = self.path(name + '.out')
            with open(output_file, 'w') as output_stream:
                output_stream.write(output)
            lines.append('cat %s' % quote(output_file))
        if script:
            lines.append(script)
        lines.append('exit %d' % exit_code)
        self.write_executable(os.path.join(self.bin, name), '\n'.join(lines) + '\n')

    def unstub(self, name):
        os.remove(os.path.join(self.bin, name))

    def calls(self, name):
        with open(self.calls_file) as calls:
            return [line.rstrip('\n')[len(name) + 1:] for line in calls if line.startswith(name + ' ')]

    def run(self, function, args=(), env=None):
        command = 'source %s && %s' % (quote(LIBRARY), ' '.join([function] + [quote(arg) for arg in args]))
        return self.run_script(command, env)

    def run_script(self, script, env=None):
        full_env = {'PATH': self.bin, 'HOME': self.root}
        full_env.update(env or {})
        process = subprocess.Popen([BASH, '-c', script], env=full_env, cwd=self.root,
                                   stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = process.communicate()
        return process.returncode, out.decode('utf-8'), err.decode('utf-8')


@pytest.fixture
def sandbox(tmpdir):
    return Sandbox(tmpdir)


def _read(path):
    with open(path) as text:
        return text.read()


def _write(path, contents):
    with open(path, 'w') as text:
        text.write(contents)


@pytest.mark.parametrize('user,expected', [
    ('alice', 0),
    ('root', 1),
    ('nobody-here', 1),
    ('', 1),
])
def test_check_user_accepts_only_an_existing_non_root_user(sandbox, user, expected):
    returncode, _, _ = sandbox.run('dind_rootless_check_user', [user])

    assert returncode == expected


@pytest.mark.parametrize('version,expected', [
    ('20.10.0', 0),
    ('20.10.24', 0),
    ('27.5.1', 0),
    ('19.03.15', 1),
    ('18.09.6', 1),
    ('', 1),
])
def test_check_version_requires_docker_20_10_or_newer(sandbox, version, expected):
    returncode, _, _ = sandbox.run('dind_rootless_check_version', [version])

    assert returncode == expected


@pytest.mark.parametrize('start,expected', [
    ('100000000', 0),
    ('200000', 0),
    ('0', 1),
    ('-5', 1),
    ('012', 1),
    ('12 34', 1),
    ('abc', 1),
    ('', 1),
])
def test_check_subid_start_accepts_only_a_positive_integer(sandbox, start, expected):
    returncode, _, _ = sandbox.run('dind_rootless_check_subid_start', [start])

    assert returncode == expected


def test_docker_host_is_the_socket_in_the_users_runtime_dir(sandbox):
    assert sandbox.run('dind_rootless_runtime_dir', ['alice'])[:2] == (0, '/run/user/1001\n')
    assert sandbox.run('dind_rootless_docker_host', ['alice'])[:2] == (0, 'unix:///run/user/1001/docker.sock\n')


def test_docker_host_fails_for_an_unknown_user(sandbox):
    returncode, out, _ = sandbox.run('dind_rootless_docker_host', ['nobody-here'])

    assert returncode == 1
    assert out == ''


def test_set_subids_creates_the_file_with_the_range(sandbox):
    subuid = sandbox.path('subuid')

    for _ in range(2):
        returncode, _, _ = sandbox.run('dind_rootless_set_subids', ['alice', '100000000', '65536', subuid])
        assert returncode == 0

    assert _read(subuid) == 'alice:100000000:65536\n'


def test_set_subids_replaces_the_range_that_useradd_added(sandbox):
    subuid = sandbox.path('subuid')
    _write(subuid, 'bob:165536:65536\nalice:100000:65536\ncarol:231072:65536\n')

    returncode, _, _ = sandbox.run('dind_rootless_set_subids', ['alice', '100000000', '65536', subuid])

    assert returncode == 0
    assert _read(subuid) == 'bob:165536:65536\ncarol:231072:65536\nalice:100000000:65536\n'


def test_set_subids_does_not_match_other_users_by_prefix_or_pattern(sandbox):
    subuid = sandbox.path('subuid')
    _write(subuid, 'alice2:200000:65536\na.ice:300000:65536\n')

    sandbox.run('dind_rootless_set_subids', ['alice', '100000000', '65536', subuid])
    sandbox.run('dind_rootless_set_subids', ['al.ce', '400000', '65536', subuid])

    assert _read(subuid) == 'alice2:200000:65536\na.ice:300000:65536\n' \
                            'alice:100000000:65536\nal.ce:400000:65536\n'


def test_has_id_privilege_accepts_the_setuid_bit(sandbox):
    binary = sandbox.path('newuidmap')
    sandbox.write_executable(binary, '')
    os.chmod(binary, 0o4755)

    assert sandbox.run('dind_rootless_has_id_privilege', [binary])[0] == 0


def test_has_id_privilege_accepts_a_file_capability(sandbox):
    binary = sandbox.path('newuidmap')
    sandbox.write_executable(binary, '')
    sandbox.stub('getcap', output='%s cap_setuid=ep\n' % binary)

    assert sandbox.run('dind_rootless_has_id_privilege', [binary])[0] == 0


@pytest.mark.parametrize('getcap_output', [None, ''])
def test_has_id_privilege_rejects_a_plain_binary(sandbox, getcap_output):
    binary = sandbox.path('newuidmap')
    sandbox.write_executable(binary, '')
    if getcap_output is not None:
        sandbox.stub('getcap', output=getcap_output)

    assert sandbox.run('dind_rootless_has_id_privilege', [binary])[0] == 1


def test_ensure_id_privilege_keeps_a_privileged_binary(sandbox):
    binary = sandbox.path('newuidmap')
    sandbox.write_executable(binary, '')
    os.chmod(binary, 0o4755)
    sandbox.stub('setcap')

    assert sandbox.run('dind_rootless_ensure_id_privilege', [binary, 'cap_setuid+ep'])[0] == 0
    assert sandbox.calls('setcap') == []


def test_ensure_id_privilege_sets_the_file_capability(sandbox):
    binary = sandbox.path('newuidmap')
    marker = sandbox.path('capability-set')
    sandbox.write_executable(binary, '')
    sandbox.stub('setcap', script='touch %s' % quote(marker))
    sandbox.stub('getcap', script='[ -f %s ] && echo "$1 cap_setuid=ep"' % quote(marker))

    returncode, _, _ = sandbox.run('dind_rootless_ensure_id_privilege', [binary, 'cap_setuid+ep'])

    assert returncode == 0
    assert sandbox.calls('setcap') == ['cap_setuid+ep %s' % binary]
    assert not os.stat(binary).st_mode & stat.S_ISUID


@pytest.mark.parametrize('setcap_available', [False, True])
def test_ensure_id_privilege_falls_back_to_the_setuid_bit(sandbox, setcap_available):
    binary = sandbox.path('newuidmap')
    sandbox.write_executable(binary, '')
    if setcap_available:
        sandbox.stub('setcap', exit_code=1)

    returncode, _, _ = sandbox.run('dind_rootless_ensure_id_privilege', [binary, 'cap_setuid+ep'])

    assert returncode == 0
    assert os.stat(binary).st_mode & stat.S_ISUID


def _id_maps_installer(sandbox):
    """A package manager stub, which installs newuidmap and newgidmap into the sandbox."""
    return '\n'.join(
        ['if [ "$4" == "shadow-utils" ] || [ "$4" == "uidmap" ]; then'] +
        ['    printf "#!/bin/sh\\n" > %s && chmod 755 %s' % (quote(path), quote(path))
         for path in [os.path.join(sandbox.bin, 'newuidmap'), os.path.join(sandbox.bin, 'newgidmap')]] +
        ['fi'])


def test_install_idmap_skips_the_packages_already_installed(sandbox):
    for name in ['newuidmap', 'newgidmap']:
        sandbox.stub(name)
        os.chmod(os.path.join(sandbox.bin, name), 0o4755)
    sandbox.stub('getcap')
    sandbox.stub('yum')

    assert sandbox.run('dind_rootless_install_idmap', ['0'])[0] == 0
    assert sandbox.calls('yum') == []


@pytest.mark.parametrize('is_rpm_based,manager,packages', [
    ('0', 'yum', ['install -y -q shadow-utils', 'install -y -q libcap']),
    ('1', 'apt', ['install -y -qq uidmap', 'install -y -qq libcap2-bin']),
])
def test_install_idmap_installs_the_packages_and_the_privileges(sandbox, is_rpm_based, manager, packages):
    sandbox.stub(manager, script=_id_maps_installer(sandbox))

    returncode, _, _ = sandbox.run('dind_rootless_install_idmap', [is_rpm_based])

    assert returncode == 0
    assert sandbox.calls(manager) == packages
    for name in ['newuidmap', 'newgidmap']:
        assert os.stat(os.path.join(sandbox.bin, name)).st_mode & stat.S_ISUID


def test_install_idmap_fails_when_the_package_has_no_newuidmap(sandbox):
    sandbox.stub('yum')

    assert sandbox.run('dind_rootless_install_idmap', ['0'])[0] == 1


def _extras_archive(sandbox):
    """A tgz with the layout of docker-rootless-extras-<version>.tgz."""
    source = sandbox.path('extras', 'docker-rootless-extras')
    os.makedirs(source)
    sandbox.write_executable(os.path.join(source, 'dockerd-rootless.sh'), '#!/bin/sh\n')
    sandbox.write_executable(os.path.join(source, 'rootlesskit'), '#!/bin/sh\n')
    archive = sandbox.path('extras.tgz')
    with tarfile.open(archive, 'w:gz') as tar:
        tar.add(source, arcname='docker-rootless-extras')
    return archive


def test_install_extras_downloads_and_extracts_the_archive(sandbox):
    archive = _extras_archive(sandbox)
    install_dir = sandbox.path('usr-local-bin')
    os.makedirs(install_dir)
    sandbox.stub('wget', script='cat %s > "$3"' % quote(archive))
    url = 'https://mirror/docker-rootless-extras-20.10.24.tgz'

    returncode, _, _ = sandbox.run('dind_rootless_install_extras', [url, install_dir])

    assert returncode == 0
    assert sandbox.calls('wget')[0].endswith(' %s' % url)
    assert sorted(os.listdir(install_dir)) == ['dockerd-rootless.sh', 'rootlesskit']


def test_install_extras_fails_when_the_download_fails(sandbox):
    install_dir = sandbox.path('usr-local-bin')
    os.makedirs(install_dir)
    sandbox.stub('wget', exit_code=8)

    returncode, _, _ = sandbox.run('dind_rootless_install_extras', ['https://mirror/missing.tgz', install_dir])

    assert returncode != 0
    assert os.listdir(install_dir) == []


def test_ensure_tun_keeps_an_existing_device(sandbox):
    sandbox.stub('mknod')

    assert sandbox.run('dind_rootless_ensure_tun', ['/dev/null'])[0] == 0
    assert sandbox.calls('mknod') == []


def test_ensure_tun_creates_a_missing_device(sandbox):
    tun = sandbox.path('dev', 'net', 'tun')
    sandbox.stub('mknod', script='touch "$1"')

    assert sandbox.run('dind_rootless_ensure_tun', [tun])[0] == 0
    assert sandbox.calls('mknod') == ['%s c 10 200' % tun]


def test_prepare_dirs_creates_the_dirs_and_links_the_certificates(sandbox):
    runtime_dir = sandbox.path('run', 'user', '1001')
    data_root = sandbox.path('runs', 'docker')
    home = sandbox.path('var', 'lib', 'dind-rootless')
    certs_dir = sandbox.path('etc', 'docker', 'certs.d')
    sandbox.stub('chown')

    for _ in range(2):
        returncode, _, _ = sandbox.run('dind_rootless_prepare_dirs',
                                       ['alice', runtime_dir, data_root, home, certs_dir])
        assert returncode == 0

    assert stat.S_IMODE(os.stat(runtime_dir).st_mode) == 0o700
    assert os.path.isdir(data_root)
    assert os.path.isdir(certs_dir)
    assert os.readlink(os.path.join(home, '.config', 'docker', 'certs.d')) == certs_dir
    assert sandbox.calls('chown')[:2] == ['alice: %s %s' % (runtime_dir, data_root), '-R alice: %s' % home]


def test_clean_runtime_dir_removes_the_state_of_a_stopped_daemon(sandbox):
    runtime_dir = sandbox.path('run', 'user', '1001')
    os.makedirs(os.path.join(runtime_dir, 'dockerd-rootless'))
    _write(os.path.join(runtime_dir, 'docker.pid'), '42\n')
    _write(os.path.join(runtime_dir, 'dockerd-rootless', 'child_pid'), '43\n')
    _write(os.path.join(runtime_dir, 'other'), '')

    assert sandbox.run('dind_rootless_clean_runtime_dir', [runtime_dir])[0] == 0
    assert os.listdir(runtime_dir) == ['other']


NVIDIA_CONFIG = '[nvidia-container-cli]\n%s\nldconfig = "@/sbin/ldconfig"\n'
NVIDIA_NO_CGROUPS = 'no-cgroups = true # set by the rootless DinD'


@pytest.mark.parametrize('line', ['#no-cgroups = false', 'no-cgroups = false', '# no-cgroups=false'])
def test_configure_nvidia_turns_off_the_cgroups(sandbox, line):
    config = sandbox.path('config.toml')
    _write(config, NVIDIA_CONFIG % line)

    for _ in range(2):
        assert sandbox.run('dind_rootless_configure_nvidia', [config])[0] == 0
    assert _read(config) == NVIDIA_CONFIG % NVIDIA_NO_CGROUPS


def test_configure_nvidia_fails_without_the_setting(sandbox):
    config = sandbox.path('config.toml')
    _write(config, '[nvidia-container-cli]\nldconfig = "@/sbin/ldconfig"\n')

    assert sandbox.run('dind_rootless_configure_nvidia', [config])[0] == 1
    assert sandbox.run('dind_rootless_configure_nvidia', [sandbox.path('missing.toml')])[0] == 1


def test_restore_nvidia_restores_what_the_rootless_mode_changed(sandbox):
    config = sandbox.path('config.toml')
    _write(config, NVIDIA_CONFIG % '#no-cgroups = false')
    sandbox.run('dind_rootless_configure_nvidia', [config])

    assert sandbox.run('dind_rootless_restore_nvidia', [config])[0] == 0
    assert _read(config) == NVIDIA_CONFIG % '#no-cgroups = false'


def test_restore_nvidia_keeps_a_setting_made_by_someone_else(sandbox):
    config = sandbox.path('config.toml')
    _write(config, NVIDIA_CONFIG % 'no-cgroups = true')

    assert sandbox.run('dind_rootless_restore_nvidia', [config])[0] == 0
    assert _read(config) == NVIDIA_CONFIG % 'no-cgroups = true'
    assert sandbox.run('dind_rootless_restore_nvidia', [sandbox.path('missing.toml')])[0] == 0


def test_has_socket_accepts_only_a_socket(sandbox):
    socket_path = sandbox.path('docker.sock')
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    try:
        server.bind(socket_path)
        _write(sandbox.path('regular'), '')

        assert sandbox.run('dind_rootless_has_socket', ['unix://' + socket_path])[0] == 0
        assert sandbox.run('dind_rootless_has_socket', ['unix://' + sandbox.path('regular')])[0] == 1
        assert sandbox.run('dind_rootless_has_socket', ['unix://' + sandbox.path('missing.sock')])[0] == 1
    finally:
        server.close()


@pytest.mark.parametrize('previous_link', [None, '/run/user/1000/docker.sock'])
def test_link_default_socket_points_it_to_the_rootless_socket(sandbox, previous_link):
    default_socket = sandbox.path('var', 'run', 'docker.sock')
    if previous_link:
        os.makedirs(os.path.dirname(default_socket))
        os.symlink(previous_link, default_socket)

    returncode, _, _ = sandbox.run('dind_rootless_link_default_socket', ['/run/user/1001/docker.sock', default_socket])

    assert returncode == 0
    assert os.readlink(default_socket) == '/run/user/1001/docker.sock'


def test_link_default_socket_keeps_an_existing_socket(sandbox):
    default_socket = sandbox.path('docker.sock')
    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    try:
        server.bind(default_socket)

        returncode, _, _ = sandbox.run('dind_rootless_link_default_socket',
                                       ['/run/user/1001/docker.sock', default_socket])

        assert returncode == 1
        assert not os.path.islink(default_socket)
        assert stat.S_ISSOCK(os.stat(default_socket).st_mode)
    finally:
        server.close()


@pytest.mark.parametrize('su_exit_code', [0, 1])
def test_check_userns_runs_rootlesskit_as_the_user(sandbox, su_exit_code):
    sandbox.stub('su', exit_code=su_exit_code)

    returncode, _, _ = sandbox.run('dind_rootless_check_userns', ['alice', '/usr/local/bin', '/run/user/1001'])

    assert returncode == su_exit_code
    assert sandbox.calls('su') == ['-s /bin/bash -c XDG_RUNTIME_DIR=/run/user/1001 /usr/local/bin/rootlesskit true alice']


def test_start_command_without_proxies(sandbox):
    returncode, out, _ = sandbox.run('dind_rootless_start_command',
                                     ['/usr/local/bin', '/var/lib/dind-rootless', '/run/user/1001',
                                      '/etc/docker/daemon.json', '/var/log/dockerd-rootless.log'])

    assert returncode == 0
    assert out == 'cd / && env HOME=/var/lib/dind-rootless XDG_RUNTIME_DIR=/run/user/1001 ' \
                  'XDG_CONFIG_HOME=/var/lib/dind-rootless/.config ' \
                  'PATH=/usr/local/bin:/usr/local/sbin:/usr/sbin:/usr/bin:/sbin:/bin ' \
                  'setsid nohup dockerd-rootless.sh --config-file /etc/docker/daemon.json ' \
                  '>> /var/log/dockerd-rootless.log 2>&1 < /dev/null &\n'


def test_start_command_passes_the_proxies_to_the_daemon_as_they_are(sandbox):
    log_file = sandbox.path('dockerd.log')
    env_args = sandbox.path('env.args')
    no_proxy = 'localhost,.internal $(touch pwned)'
    # env prints its arguments one per line, instead of starting the daemon
    sandbox.stub('env', script='printf "%%s\\n" "$@" > %s' % quote(env_args))
    _, command, _ = sandbox.run('dind_rootless_start_command',
                                ['/usr/local/bin', '/var/lib/dind-rootless', '/run/user/1001',
                                 '/etc/docker/daemon.json', log_file],
                                env={'http_proxy': 'http://proxy:3128', 'no_proxy': no_proxy})

    returncode, _, _ = sandbox.run_script(command.strip() + ' wait')

    assert returncode == 0
    env_lines = _read(env_args).splitlines()
    assert 'http_proxy=http://proxy:3128' in env_lines
    assert 'no_proxy=%s' % no_proxy in env_lines
    assert 'https_proxy=' not in ''.join(env_lines)
    assert env_lines[-4:] == ['nohup', 'dockerd-rootless.sh', '--config-file', '/etc/docker/daemon.json']
    assert not os.path.exists('/pwned') and not os.path.exists(sandbox.path('pwned'))


def test_start_runs_the_start_command_as_the_user(sandbox):
    args = ['/usr/local/bin', '/var/lib/dind-rootless', '/run/user/1001', '/etc/docker/daemon.json',
            '/var/log/dockerd-rootless.log']
    sandbox.stub('su')
    _, command, _ = sandbox.run('dind_rootless_start_command', args)

    returncode, _, _ = sandbox.run('dind_rootless_start', ['alice'] + args)

    assert returncode == 0
    assert sandbox.calls('su') == ['-s /bin/bash -c %s alice' % command.strip()]


def test_export_docker_host_adds_the_line_once(sandbox):
    env_file = sandbox.path('cp_env.sh')
    _write(env_file, 'export OWNER="alice"\n')

    for _ in range(2):
        returncode, _, _ = sandbox.run('dind_rootless_export_docker_host',
                                       [env_file, 'unix:///run/user/1001/docker.sock'])
        assert returncode == 0

    assert _read(env_file) == 'export OWNER="alice"\nexport DOCKER_HOST="unix:///run/user/1001/docker.sock"\n'


@pytest.mark.parametrize('env_file', ['', 'missing.sh'])
def test_export_docker_host_fails_without_the_env_file(sandbox, env_file):
    path = sandbox.path(env_file) if env_file else ''

    assert sandbox.run('dind_rootless_export_docker_host', [path, 'unix:///run/user/1001/docker.sock'])[0] == 1
    assert not env_file or not os.path.exists(path)


LAUNCH_SCRIPT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..', '..',
                                             'scripts', 'pipeline-launch', 'launch.sh'))
ROOTFUL_INIT_LINE = 'dind_setup && docker_setup_credentials'
ROOTLESS_INIT_LINE = 'export CP_CAP_DIND_CONTAINER_ROOTLESS=true && dind_setup && docker_setup_credentials || exit 1'


def _launch_functions(sandbox):
    """Copies the launch.sh functions under test into a file to source."""
    with open(LAUNCH_SCRIPT) as launch:
        lines = launch.read().splitlines()
    functions = []
    for name in ['check_cp_cap', 'cp_cap_check_dind_rootless', 'cp_cap_publish']:
        start = lines.index('function %s {' % name)
        functions.extend(lines[start:lines.index('}', start) + 1])
    path = sandbox.path('launch_functions.sh')
    _write(path, '\n'.join(functions) + '\n')
    return path


def _publish(sandbox, env):
    """Runs cp_cap_publish and returns the master and the worker init scripts."""
    scripts_dir = sandbox.path('caps')
    publish_env = {'cluster_role': 'master', 'CP_CAP_SCRIPTS_DIR': scripts_dir}
    publish_env.update(env)
    returncode, _, err = sandbox.run_script('source %s && cp_cap_publish' % quote(_launch_functions(sandbox)),
                                            publish_env)
    assert returncode == 0, err
    return _read(os.path.join(scripts_dir, 'master')), _read(os.path.join(scripts_dir, 'worker'))


def _run_init_script(sandbox, init_script, dind_setup_exit_code):
    """Runs an init script with the DinD line and a capability line after it, as cp_cap_init does."""
    sandbox.stub('dind_setup', exit_code=dind_setup_exit_code,
                 script='echo "dind_setup: rootless=$CP_CAP_DIND_CONTAINER_ROOTLESS '
                        'start=$CP_CAP_DIND_CONTAINER_ROOTLESS_SUBID_START"')
    sandbox.stub('docker_setup_credentials')
    sandbox.stub('sge_setup_master')
    return sandbox.run_script(init_script + 'sge_setup_master\n')


@pytest.mark.parametrize('rootless', [None, 'false', 'no'])
def test_publish_keeps_the_rootful_init_line(sandbox, rootless):
    env = {'CP_CAP_DIND_CONTAINER': 'true'}
    if rootless is not None:
        env['CP_CAP_DIND_CONTAINER_ROOTLESS'] = rootless

    master, worker = _publish(sandbox, env)

    assert master == worker == 'set -e\n%s\n' % ROOTFUL_INIT_LINE


@pytest.mark.parametrize('rootless', ['true', 'True', 'yes'])
def test_publish_sets_the_rootless_mode_for_the_master_and_the_workers(sandbox, rootless):
    master, worker = _publish(sandbox, {'CP_CAP_DIND_CONTAINER': 'true', 'CP_CAP_DIND_CONTAINER_ROOTLESS': rootless})

    assert master == worker == 'set -e\n%s\n' % ROOTLESS_INIT_LINE


def test_publish_passes_the_subid_start_to_the_workers(sandbox):
    _, worker = _publish(sandbox, {'CP_CAP_DIND_CONTAINER': 'true', 'CP_CAP_DIND_CONTAINER_ROOTLESS': 'true',
                                   'CP_CAP_DIND_CONTAINER_ROOTLESS_SUBID_START': '300000'})

    assert worker == 'set -e\nexport CP_CAP_DIND_CONTAINER_ROOTLESS=true ' \
                     'CP_CAP_DIND_CONTAINER_ROOTLESS_SUBID_START=300000 ' \
                     '&& dind_setup && docker_setup_credentials || exit 1\n'


def test_publish_replaces_the_previous_dind_init_line(sandbox):
    os.makedirs(sandbox.path('caps'))
    _write(sandbox.path('caps', 'master'), 'set -e\n%s\n' % ROOTFUL_INIT_LINE)

    master, _ = _publish(sandbox, {'CP_CAP_DIND_CONTAINER': 'true', 'CP_CAP_DIND_CONTAINER_ROOTLESS': 'true'})

    assert master.count('dind_setup') == 1
    assert master.endswith('\n%s\n' % ROOTLESS_INIT_LINE)


def test_rootless_init_line_fails_the_init_script_when_dind_setup_fails(sandbox):
    master, _ = _publish(sandbox, {'CP_CAP_DIND_CONTAINER': 'true', 'CP_CAP_DIND_CONTAINER_ROOTLESS': 'true'})

    returncode, _, _ = _run_init_script(sandbox, master, dind_setup_exit_code=1)

    assert returncode == 1
    assert sandbox.calls('docker_setup_credentials') == []
    assert sandbox.calls('sge_setup_master') == []


def test_rootless_init_line_passes_the_settings_to_dind_setup(sandbox):
    master, _ = _publish(sandbox, {'CP_CAP_DIND_CONTAINER': 'true', 'CP_CAP_DIND_CONTAINER_ROOTLESS': 'true',
                                   'CP_CAP_DIND_CONTAINER_ROOTLESS_SUBID_START': '1; touch pwned'})

    returncode, out, _ = _run_init_script(sandbox, master, dind_setup_exit_code=0)

    assert returncode == 0
    assert 'dind_setup: rootless=true start=1; touch pwned\n' in out
    assert not os.path.exists(sandbox.path('pwned'))
    assert sandbox.calls('docker_setup_credentials') == ['']
    assert sandbox.calls('sge_setup_master') == ['']


@pytest.mark.parametrize('env,expected', [
    ({'CP_CAP_KUBE': 'true', 'CP_CAP_DIND_CONTAINER_ROOTLESS': 'true'}, 1),
    ({'CP_CAP_KUBE': 'yes', 'CP_CAP_DIND_CONTAINER_ROOTLESS': 'True'}, 1),
    ({'CP_CAP_KUBE': 'true'}, 0),
    ({'CP_CAP_KUBE': 'true', 'CP_CAP_DIND_CONTAINER_ROOTLESS': 'false'}, 0),
    ({'CP_CAP_DIND_CONTAINER_ROOTLESS': 'true'}, 0),
])
def test_check_dind_rootless_rejects_kubernetes(sandbox, env, expected):
    returncode, out, _ = sandbox.run_script(
        'source %s && cp_cap_check_dind_rootless' % quote(_launch_functions(sandbox)), env)

    assert returncode == expected
    assert ('[ERROR]' in out) == (expected == 1)


def test_launch_fails_the_run_when_the_rootless_check_fails():
    with open(LAUNCH_SCRIPT) as launch:
        script = launch.read()

    assert 'if ! cp_cap_check_dind_rootless; then\n      exit_init 1\nfi\n' in script
