# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Covers the NoMachine setup of workflows/pipe-common/shell/nomachine_setup on an RPM-based OS: the desktop and the
# NoMachine version it installs, the NoMachine configuration it writes, and the check for systemd, on Rocky Linux 10
# (GNOME on X11) and on Rocky Linux 9 (Xfce). Runs the script with bash, with the absolute paths it reads and writes
# replaced by temporary ones, PID 1 of the run included. yum, rpm, wget, tar, su, chown, lsof, nohup, tail, systemctl,
# the NoMachine server, dbus-uuidgen, gnome_desktop_setup, the pipe_log_* commands and the Python of the run are
# replaced by stubs, which record their calls: one line per call, the command and its arguments separated by tabs. The
# tar stub creates the files of the archives, lsof reports the ports as listened to.

import os
import re
import stat
import subprocess
import time

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
NOMACHINE_SETUP = os.path.join(SHELL_DIR, 'nomachine_setup')

RECORDING_STUB = '''#!/bin/bash
{ printf '%s' "$(basename "$0")"; [ $# -gt 0 ] && printf '\\t%s' "$@"; printf '\\n'; } >> "$CALLS"
'''

# rpm -q -f /usr/bin/rpm succeeds (an RPM-based OS), rpm -qa lists the NoMachine package of NM_INSTALLED, if it is set
RPM_STUB = RECORDING_STUB + '''if [ "$1" == "-qa" ] && [ "$NM_INSTALLED" ]; then
    echo "nomachine-$NM_INSTALLED.x86_64"
fi
'''

# wget ... -O <file>: the file is created
WGET_STUB = RECORDING_STUB + '''while [ $# -gt 0 ]; do
    [ "$1" == "-O" ] && touch "$2"
    shift
done
'''

# tar ... -C <folder> ...: the files of the archive are created in the folder
TAR_STUB = RECORDING_STUB + '''_folder=""
while [ $# -gt 0 ]; do
    [ "$1" == "-C" ] && _folder="$2"
    shift
done
if [[ "$_folder" == *nomachine-common* ]]; then
    echo true > "$_folder/create_chrome_launcher.sh"
    touch "$_folder/scramble" "$_folder/template.nxs"
    mkdir -p "$_folder/xfce"
elif [ "$_folder" ]; then
    touch "$_folder/xfce4-session-4.18.rpm"
fi
'''

# gnome_desktop_setup applies: succeeds, if the OS is said to be Rocky Linux 10. The other commands fail, if said to
GNOME_DESKTOP_SETUP_STUB = RECORDING_STUB + '''if [ "$1" == "applies" ]; then
    [ "$GNOME_APPLIES" == "true" ]
    exit $?
fi
[ "$GNOME_INSTALL_FAILS" == "true" ] && [ "$1" == "install" ] && exit 1
exit 0
'''

LSOF_STUB = RECORDING_STUB + '''echo "nxd 100 root 3u IPv4 0t0 TCP *:4000 (LISTEN)"
'''

# The configuration files of the NoMachine packages, with the keys that the setup changes
NODE_CFG = '''#UserScriptAfterSessionStart ""
DefaultDesktopCommand "/etc/X11/Xsession default"
'''

SERVER_CFG = '''#CreateDisplay 0
#DisplayOwner ""
#EnableFirewallConfiguration 1
'''

ROCKY_10 = {'CP_OS': 'rocky', 'CP_VER': '10.2', 'CP_VER_MAJOR': '10', 'GNOME_APPLIES': 'true'}
ROCKY_9 = {'CP_OS': 'rocky', 'CP_VER': '9.8', 'CP_VER_MAJOR': '9', 'GNOME_APPLIES': 'false'}


def _stub(directory, name, body):
    stub = directory.join(name)
    stub.write(body)
    os.chmod(str(stub), stat.S_IRWXU)


@pytest.fixture
def root(tmpdir):
    """The temporary folder, which stands for / of the run."""
    root = tmpdir.mkdir('root')
    root.ensure('usr', 'NX', 'etc', 'node.cfg').write(NODE_CFG)
    root.ensure('usr', 'NX', 'etc', 'server.cfg').write(SERVER_CFG)
    for folder in (('usr', 'local', 'bin'), ('var', 'log'), ('tmp',), ('opt',), ('etc', 'xdg', 'autostart')):
        root.ensure(*folder, dir=True)
    root.ensure('etc', 'machine-id').write('original-machine-id\n')
    return root


@pytest.fixture
def nomachine_setup(tmpdir, root):
    stub_bin = tmpdir.mkdir('stub-bin')
    for name in ('pipe_log_info', 'pipe_log_warn', 'pipe_log_fail', 'pipe_log_success', 'yum', 'su', 'chown',
                 'nohup', 'tail', 'nxserver', 'dbus-uuidgen', 'python', 'systemctl'):
        _stub(stub_bin, name, RECORDING_STUB)
    _stub(stub_bin, 'rpm', RPM_STUB)
    _stub(stub_bin, 'wget', WGET_STUB)
    _stub(stub_bin, 'tar', TAR_STUB)
    _stub(stub_bin, 'lsof', LSOF_STUB)
    _stub(stub_bin, 'gnome_desktop_setup', GNOME_DESKTOP_SETUP_STUB)
    work_dir = tmpdir.mkdir('work')
    calls = tmpdir.join('calls')
    replacements = {
        '/usr/bin/rpm -q -f /usr/bin/rpm': 'rpm -q -f /usr/bin/rpm',
        '/usr/NX/etc/': str(root.join('usr', 'NX', 'etc')) + '/',
        '/etc/NX/nxserver': str(stub_bin.join('nxserver')),
        '/bin/dbus-uuidgen': str(stub_bin.join('dbus-uuidgen')),
        '/etc/nomachine': str(root.join('etc', 'nomachine')),
        '/usr/local/bin/': str(root.join('usr', 'local', 'bin')) + '/',
        '/var/log/': str(root.join('var', 'log')) + '/',
        '/home/${OWNER}': str(root.join('home')) + '/${OWNER}',
        '/opt/': str(root.join('opt')) + '/',
        '/tmp/': str(root.join('tmp')) + '/',
        '/etc/xdg/': str(root.join('etc', 'xdg')) + '/',
        '/etc/machine-id': str(root.join('etc', 'machine-id')),
        '/etc/os-release': str(root.join('etc', 'os-release')),
        '/proc/1/comm': str(root.join('proc-1-comm')),
        '"/caps.sh"': '"%s"' % root.join('caps.sh'),
        'sleep 5': 'sleep 0',
    }
    # A single pass: the temporary paths contain some of the replaced ones (e.g. /tmp/)
    pattern = re.compile('|'.join(re.escape(path) for path in sorted(replacements, key=len, reverse=True)))
    with open(NOMACHINE_SETUP) as f:
        script = pattern.sub(lambda match: replacements[match.group(0)], f.read())

    def run(os_version, pid_1='systemd', **extra_env):
        if calls.exists():
            calls.remove()
        root.join('proc-1-comm').write(pid_1 + '\n')
        root.join('etc', 'os-release').write('ID=%s\nVERSION_ID="%s"\n' % (os_version['CP_OS'], os_version['CP_VER']))
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls),
            'OWNER': 'user1',
            'GLOBAL_DISTRIBUTION_URL': 'https://distribution.example/',
            'CP_PYTHON_PATH': str(stub_bin.join('python')),
            'COMMON_REPO_DIR': str(tmpdir.join('pipe-common')),
            'CP_NM_WAIT_TIMEOUT': '2'
        }
        env.update(os_version)
        env.update(extra_env)
        exit_code = subprocess.call(['bash', '-c', script], env=env, cwd=str(work_dir))
        # The NoMachine server is started in background, "tail -f" of its log is the last step. The setup waits for its
        # ports (lsof) after starting it: without these calls, the server was not started
        for _ in range(50):
            lines = calls.read().splitlines() if calls.exists() else []
            if any(line.startswith('tail\t') for line in lines):
                break
            if exit_code != 0 or not any(line.startswith('lsof\t') for line in lines):
                break
            time.sleep(0.1)
        lines = calls.read().splitlines() if calls.exists() else []
        return exit_code, [line.split('\t') for line in lines]

    return run


def _calls_of(calls, command):
    return [call[1:] for call in calls if call[0] == command]


def _index(calls, command, first_arg):
    return [i for i, call in enumerate(calls) if call[0] == command and call[1:2] == [first_arg]][0]


def _cfg(root, name):
    return root.join('usr', 'NX', 'etc', name).read().splitlines()


def test_rocky_10_installs_the_gnome_desktop_instead_of_xfce(nomachine_setup):
    exit_code, calls = nomachine_setup(ROCKY_10)

    assert exit_code == 0
    assert _calls_of(calls, 'gnome_desktop_setup') == [['applies'],
                                                       ['install', 'NoMachineInitialization'],
                                                       ['configure', 'NoMachineInitialization']]
    assert not [args for args in _calls_of(calls, 'wget') if 'xfce4-' in args[-3]]
    assert ['install', '-y', 'nomachine.rpm'] in _calls_of(calls, 'yum')
    assert not _calls_of(calls, 'su')
    assert not _calls_of(calls, 'dbus-uuidgen')
    assert not _calls_of(calls, 'pipe_log_fail')


def test_rocky_10_sets_the_gnome_defaults_before_the_desktop_starts(nomachine_setup):
    exit_code, calls = nomachine_setup(ROCKY_10)

    assert exit_code == 0
    assert _index(calls, 'gnome_desktop_setup', 'configure') < _index(calls, 'nxserver', '--startup')


def test_rocky_10_stops_gdm_which_the_nomachine_display_does_not_use(nomachine_setup):
    exit_code, calls = nomachine_setup(ROCKY_10)

    assert exit_code == 0
    # Disabled, not masked: NICE DCV starts GDM, if the run has DCV as well
    assert _calls_of(calls, 'systemctl') == [['disable', '--now', 'gdm.service']]
    assert _index(calls, 'systemctl', 'disable') < _index(calls, 'nxserver', '--startup')


@pytest.mark.parametrize('os_version, extra_env', [
    (ROCKY_10, {}),
    (ROCKY_9, {'CP_CAP_DESKTOP_NM_VERSION': '8.4.2_1'}),
])
def test_installed_target_version_is_kept(nomachine_setup, os_version, extra_env):
    # The package reports 9.8.2-1 for nomachine_9.8.2_1_x86_64.rpm, e.g. in an image committed from a run
    installed = '9.8.2-1' if os_version is ROCKY_10 else '8.4.2-1'
    exit_code, calls = nomachine_setup(os_version, NM_INSTALLED=installed, **extra_env)

    assert exit_code == 0
    assert [args for args in _calls_of(calls, 'pipe_log_info') if 'NoMachine installed already' in args[0]]
    assert not [args for args in _calls_of(calls, 'wget') if 'nomachine_' in args[1]]
    assert not [args for args in _calls_of(calls, 'yum') if 'nomachine.rpm' in args]


def test_rocky_10_replaces_an_earlier_installed_version(nomachine_setup):
    exit_code, calls = nomachine_setup(ROCKY_10, NM_INSTALLED='8.4.2-1')

    assert exit_code == 0
    assert ['-q', 'https://distribution.example/tools/nomachine/nomachine_9.8.2_1_x86_64.rpm',
            '-O', 'nomachine.rpm'] in _calls_of(calls, 'wget')
    assert [args for args in _calls_of(calls, 'pipe_log_info') if 'version changed successfully' in args[0]]
    assert _calls_of(calls, 'gnome_desktop_setup') == [['applies'], ['configure', 'NoMachineInitialization']]


def test_rocky_10_starts_gnome_in_the_nomachine_display(nomachine_setup, root):
    exit_code, _ = nomachine_setup(ROCKY_10)

    assert exit_code == 0
    node_cfg = _cfg(root, 'node.cfg')
    assert 'DefaultDesktopCommand "/bin/dbus-run-session gnome-session --session=gnome"' in node_cfg
    # The keyboard layouts are a part of the GNOME defaults, setxkbmap is not used
    assert '#UserScriptAfterSessionStart ""' in node_cfg
    assert not root.join('etc', 'nomachine', 'set_xkb.sh').exists()


@pytest.mark.parametrize('extra_env, command', [
    ({'CP_NM_DEFAULT_DESKTOP_COMMAND_EL10': '/usr/bin/custom-session'}, '/usr/bin/custom-session'),
    # The command of the other RPM-based distributions starts Xfce, which these releases do not have
    ({'CP_NM_DEFAULT_DESKTOP_COMMAND_RPM': '/usr/bin/startxfce4'},
     '/bin/dbus-run-session gnome-session --session=gnome'),
])
def test_rocky_10_desktop_command_can_be_overridden(nomachine_setup, root, extra_env, command):
    exit_code, _ = nomachine_setup(ROCKY_10, **extra_env)

    assert exit_code == 0
    assert 'DefaultDesktopCommand "%s"' % command in _cfg(root, 'node.cfg')


def test_rocky_10_creates_the_display_without_the_firewall_configuration(nomachine_setup, root):
    exit_code, _ = nomachine_setup(ROCKY_10)

    assert exit_code == 0
    server_cfg = _cfg(root, 'server.cfg')
    assert 'CreateDisplay 1' in server_cfg
    assert 'DisplayOwner user1' in server_cfg
    assert 'EnableFirewallConfiguration 0' in server_cfg


@pytest.mark.parametrize('extra_env, version', [
    ({}, '9.8.2_1'),
    # The version of the other distributions does not apply: NoMachine 8.4.2 does not offer the GNOME display
    ({'CP_CAP_DESKTOP_NM_VERSION': '8.4.2_1'}, '9.8.2_1'),
    ({'CP_CAP_DESKTOP_NM_VERSION_EL10': '9.9.0_1'}, '9.9.0_1'),
])
def test_rocky_10_installs_nomachine_9(nomachine_setup, extra_env, version):
    exit_code, calls = nomachine_setup(ROCKY_10, **extra_env)

    assert exit_code == 0
    assert ['-q', 'https://distribution.example/tools/nomachine/nomachine_%s_x86_64.rpm' % version,
            '-O', 'nomachine.rpm'] in _calls_of(calls, 'wget')


def test_rocky_10_without_systemd_fails_early(nomachine_setup):
    exit_code, calls = nomachine_setup(ROCKY_10, pid_1='bash')

    assert exit_code == 1
    assert [args for args in _calls_of(calls, 'pipe_log_fail') if 'CP_CAP_SYSTEMD_CONTAINER' in args[0]]
    assert not _calls_of(calls, 'yum')
    assert not _calls_of(calls, 'nxserver')


@pytest.mark.parametrize('pid_1', ['systemd', 'bash'])
def test_rocky_10_with_nice_dcv_leaves_the_desktop_to_dcv(nomachine_setup, pid_1):
    # Both run the GNOME desktop there, and launch.sh sets up NICE DCV after NoMachine
    exit_code, calls = nomachine_setup(ROCKY_10, pid_1=pid_1, CP_CAP_DCV='true')

    assert exit_code == 0
    assert _calls_of(calls, 'gnome_desktop_setup') == [['applies']]
    assert [args for args in _calls_of(calls, 'pipe_log_warn')
            if 'NoMachine is not configured' in args[0] and 'NICE DCV' in args[0]]
    assert _calls_of(calls, 'pipe_log_success') == [['NoMachine is skipped, as the run has NICE DCV',
                                                     'NoMachineInitialization']]
    assert not _calls_of(calls, 'pipe_log_fail')
    for command in ('yum', 'wget', 'systemctl', 'nxserver', 'nohup'):
        assert not _calls_of(calls, command)


def test_rocky_10_with_nice_dcv_disabled_installs_nomachine(nomachine_setup):
    exit_code, calls = nomachine_setup(ROCKY_10, CP_CAP_DCV='false')

    assert exit_code == 0
    assert _calls_of(calls, 'gnome_desktop_setup')[1:] == [['install', 'NoMachineInitialization'],
                                                           ['configure', 'NoMachineInitialization']]


def test_rocky_9_with_nice_dcv_installs_nomachine_as_before(nomachine_setup):
    exit_code, calls = nomachine_setup(ROCKY_9, CP_CAP_DESKTOP_NM_VERSION='8.4.2_1', CP_CAP_DCV='true')

    assert exit_code == 0
    assert 'https://distribution.example/tools/nomachine/xfce4-rocky-98.tar.gz' in [args[1] for args in
                                                                                   _calls_of(calls, 'wget')]
    assert ['--startup'] in _calls_of(calls, 'nxserver')
    assert not _calls_of(calls, 'pipe_log_warn')


def test_rocky_10_reports_the_gnome_desktop_which_cannot_be_installed(nomachine_setup):
    exit_code, calls = nomachine_setup(ROCKY_10, GNOME_INSTALL_FAILS='true')

    assert exit_code == 1
    assert [args for args in _calls_of(calls, 'pipe_log_fail') if 'Failed to install NoMachine' in args[0]]
    assert not [args for args in _calls_of(calls, 'wget') if 'nomachine_' in args[1]]
    assert not _calls_of(calls, 'nxserver')


def test_rocky_9_installs_xfce_as_before(nomachine_setup, root):
    exit_code, calls = nomachine_setup(ROCKY_9, CP_CAP_DESKTOP_NM_VERSION='8.4.2_1')

    assert exit_code == 0
    wget_urls = [args[1] for args in _calls_of(calls, 'wget')]
    assert 'https://distribution.example/tools/nomachine/xfce4-rocky-98.tar.gz' in wget_urls
    assert 'https://distribution.example/tools/nomachine/nomachine_8.4.2_1_x86_64.rpm' in wget_urls
    assert _calls_of(calls, 'gnome_desktop_setup') == [['applies']]
    assert not _calls_of(calls, 'systemctl')
    assert _calls_of(calls, 'dbus-uuidgen') == [[]]
    assert 'DefaultDesktopCommand "/usr/bin/startxfce4"' in _cfg(root, 'node.cfg')


def test_rocky_9_keeps_the_xfce_session_and_the_keyboard_script_as_before(nomachine_setup, root):
    exit_code, calls = nomachine_setup(ROCKY_9, pid_1='bash')

    # Rocky Linux 9 does not need systemd
    assert exit_code == 0
    assert [args for args in _calls_of(calls, 'su') if 'xfce' in args[-1]]
    assert 'UserScriptAfterSessionStart "/etc/nomachine/set_xkb.sh"'.replace(
        '/etc/nomachine', str(root.join('etc', 'nomachine'))) in _cfg(root, 'node.cfg')
    assert root.join('etc', 'nomachine', 'set_xkb.sh').check(file=True)
    server_cfg = _cfg(root, 'server.cfg')
    assert 'CreateDisplay 1' in server_cfg
    assert '#EnableFirewallConfiguration 1' in server_cfg
