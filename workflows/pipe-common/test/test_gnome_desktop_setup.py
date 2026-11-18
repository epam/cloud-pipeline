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

# Covers workflows/pipe-common/shell/gnome_desktop_setup: the distributions it applies to, the GNOME packages it
# installs, and the defaults and the services it configures. Runs the script with bash, with the dconf folder replaced
# by a temporary one. rpm, yum, systemctl, dconf and the pipe_log_* commands are replaced by stubs, which record their
# calls: one line per call, the command and its arguments separated by tabs.

import os
import stat
import subprocess

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
GNOME_DESKTOP_SETUP = os.path.join(SHELL_DIR, 'gnome_desktop_setup')

RECORDING_STUB = '''#!/bin/bash
{ printf '%s' "$(basename "$0")"; [ $# -gt 0 ] && printf '\\t%s' "$@"; printf '\\n'; } >> "$CALLS"
'''

# rpm -q -f /usr/bin/rpm succeeds on an RPM-based OS, rpm -q coreutils-single - if the image is said to have it
RPM_STUB = RECORDING_STUB + '''if [ "$1" == "-q" ] && [ "$2" == "-f" ]; then
    [ "$RPM_BASED" != "false" ]
    exit $?
fi
if [ "$1" == "-q" ] && [ "$2" == "coreutils-single" ]; then
    [ "$HAS_COREUTILS_SINGLE" == "true" ]
    exit $?
fi
'''

# yum fails for the commands, which contain YUM_FAILS_ON, unless they disable the cloud-pipeline repo,
# or always, if YUM_FAILS_ALWAYS is set
YUM_STUB = RECORDING_STUB + '''[ "$YUM_FAILS_ALWAYS" == "true" ] && exit 1
if [ "$YUM_FAILS_ON" ] && [[ " $* " == *" $YUM_FAILS_ON "* ]] && [[ " $* " != *" --disablerepo=cloud-pipeline "* ]]; then
    exit 1
fi
'''

DCONF_STUB = RECORDING_STUB + '''exit ${DCONF_EXIT_CODE:-0}
'''

ROCKY_10 = {'CP_OS': 'rocky', 'CP_VER': '10.2', 'CP_VER_MAJOR': '10'}

MASKED_UNITS = ['NetworkManager.service', 'firewalld.service', 'chronyd.service', 'tuned.service',
                'irqbalance.service', 'ModemManager.service', 'wpa_supplicant.service', 'cups.service', 'cups.socket',
                'cups.path', 'avahi-daemon.service', 'avahi-daemon.socket', 'bluetooth.service', 'sleep.target',
                'suspend.target', 'hibernate.target', 'hybrid-sleep.target', 'suspend-then-hibernate.target']

GROUP_EXCLUSIONS = ['--exclude=kernel', '--exclude=kernel-core', '--exclude=kernel-modules*',
                    '--exclude=kernel-tools*', '--exclude=kmod-kvdo', '--exclude=*-firmware', '--exclude=vdo']


def _stub(directory, name, body):
    stub = directory.join(name)
    stub.write(body)
    os.chmod(str(stub), stat.S_IRWXU)


@pytest.fixture
def dconf_dir(tmpdir):
    return tmpdir.join('dconf')


@pytest.fixture
def gnome_desktop_setup(tmpdir, dconf_dir):
    stub_bin = tmpdir.mkdir('stub-bin')
    for name in ('pipe_log_warn', 'pipe_log_fail', 'systemctl'):
        _stub(stub_bin, name, RECORDING_STUB)
    _stub(stub_bin, 'rpm', RPM_STUB)
    _stub(stub_bin, 'yum', YUM_STUB)
    _stub(stub_bin, 'dconf', DCONF_STUB)
    calls = tmpdir.join('calls')
    with open(GNOME_DESKTOP_SETUP) as f:
        script = f.read() \
            .replace('/usr/bin/rpm -q -f /usr/bin/rpm', 'rpm -q -f /usr/bin/rpm') \
            .replace('/etc/dconf', str(dconf_dir))

    def run(*args, os_version=None, **extra_env):
        if calls.exists():
            calls.remove()
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls)
        }
        env.update({key: value for key, value in (os_version or ROCKY_10).items() if value is not None})
        env.update(extra_env)
        exit_code = subprocess.call(['bash', '-c', script, 'gnome_desktop_setup'] + list(args), env=env)
        lines = calls.read().splitlines() if calls.exists() else []
        return exit_code, [line.split('\t') for line in lines]

    return run


def _calls_of(calls, command):
    return [call[1:] for call in calls if call[0] == command]


@pytest.mark.parametrize('os_version, applies', [
    ({'CP_OS': 'rocky', 'CP_VER': '10.2', 'CP_VER_MAJOR': '10'}, True),
    ({'CP_OS': 'rhel', 'CP_VER': '10.1', 'CP_VER_MAJOR': '10'}, True),
    ({'CP_OS': 'centos', 'CP_VER': '10', 'CP_VER_MAJOR': '10'}, True),
    ({'CP_OS': 'rocky', 'CP_VER': '11.0', 'CP_VER_MAJOR': '11'}, True),
    # The major version is taken from CP_VER, if CP_VER_MAJOR is not set
    ({'CP_OS': 'rocky', 'CP_VER': '10.2', 'CP_VER_MAJOR': None}, True),
    ({'CP_OS': 'rocky', 'CP_VER': '9.8', 'CP_VER_MAJOR': '9'}, False),
    ({'CP_OS': 'rocky', 'CP_VER': '8.7', 'CP_VER_MAJOR': '8'}, False),
    ({'CP_OS': 'amzn', 'CP_VER': '2023', 'CP_VER_MAJOR': '2023'}, False),
    ({'CP_OS': 'ol', 'CP_VER': '10.0', 'CP_VER_MAJOR': '10'}, False),
    ({'CP_OS': 'ubuntu', 'CP_VER': '24.04', 'CP_VER_MAJOR': '24'}, False),
    ({'CP_OS': 'rocky', 'CP_VER': 'latest', 'CP_VER_MAJOR': None}, False),
    ({'CP_OS': None, 'CP_VER': None, 'CP_VER_MAJOR': None}, False),
])
def test_applies_to_rocky_rhel_and_centos_10_and_later(gnome_desktop_setup, os_version, applies):
    exit_code, _ = gnome_desktop_setup('applies', os_version=os_version)

    assert (exit_code == 0) == applies


def test_does_not_apply_to_an_os_which_is_not_rpm_based(gnome_desktop_setup):
    exit_code, _ = gnome_desktop_setup('applies', RPM_BASED='false')

    assert exit_code != 0


def test_install_installs_the_gnome_desktop_without_a_kernel(gnome_desktop_setup):
    exit_code, calls = gnome_desktop_setup('install', 'Task')

    assert exit_code == 0
    assert _calls_of(calls, 'yum') == [['-y', 'groupinstall'] + GROUP_EXCLUSIONS + ['Server with GUI']]
    assert not _calls_of(calls, 'pipe_log_fail')


def test_install_replaces_coreutils_single_first(gnome_desktop_setup):
    exit_code, calls = gnome_desktop_setup('install', 'Task', HAS_COREUTILS_SINGLE='true')

    assert exit_code == 0
    assert _calls_of(calls, 'yum') == [['-y', 'install', '--allowerasing', 'coreutils'],
                                       ['-y', 'groupinstall'] + GROUP_EXCLUSIONS + ['Server with GUI']]


@pytest.mark.parametrize('failing, retry', [
    ('coreutils', ['-y', 'install', '--allowerasing', '--disablerepo=cloud-pipeline', 'coreutils']),
    ('GUI', ['-y', 'groupinstall', '--disablerepo=cloud-pipeline'] + GROUP_EXCLUSIONS + ['Server with GUI']),
])
def test_install_retries_with_an_upstream_repo(gnome_desktop_setup, failing, retry):
    exit_code, calls = gnome_desktop_setup('install', 'Task', HAS_COREUTILS_SINGLE='true', YUM_FAILS_ON=failing)

    assert exit_code == 0
    assert retry in _calls_of(calls, 'yum')


def test_install_failure_is_reported(gnome_desktop_setup):
    exit_code, calls = gnome_desktop_setup('install', 'Task', YUM_FAILS_ALWAYS='true')

    assert exit_code != 0
    assert [args for args in _calls_of(calls, 'pipe_log_fail') if 'GNOME' in args[0] and args[1] == 'Task']


def test_configure_stops_the_services_which_shall_not_run_in_a_container(gnome_desktop_setup):
    exit_code, calls = gnome_desktop_setup('configure', 'Task')

    assert exit_code == 0
    assert _calls_of(calls, 'systemctl') == [['mask', '--now'] + MASKED_UNITS]


def test_configure_sets_the_gnome_defaults(gnome_desktop_setup, dconf_dir):
    exit_code, calls = gnome_desktop_setup('configure', 'Task')

    assert exit_code == 0
    defaults = dconf_dir.join('db', 'local.d', '00-cloud-pipeline').read().splitlines()
    assert "picture-uri=''" in defaults
    assert "picture-uri-dark=''" in defaults
    assert "sources=[('xkb', 'us'), ('xkb', 'fr'), ('xkb', 'de')]" in defaults
    assert 'lock-enabled=false' in defaults
    assert 'idle-delay=uint32 0' in defaults
    assert "welcome-dialog-last-shown-version='999'" in defaults
    # An idle desktop of the run shall not suspend the node
    assert "sleep-inactive-ac-type='nothing'" in defaults
    assert "sleep-inactive-battery-type='nothing'" in defaults
    assert dconf_dir.join('profile', 'user').read() == 'user-db:user\nsystem-db:local\n'
    assert _calls_of(calls, 'dconf') == [['update']]


def test_configure_takes_the_keyboard_layouts_of_the_run(gnome_desktop_setup, dconf_dir):
    exit_code, _ = gnome_desktop_setup('configure', 'Task', CP_DESKTOP_KEYBOARD_LAYOUTS='us,de')

    assert exit_code == 0
    defaults = dconf_dir.join('db', 'local.d', '00-cloud-pipeline').read().splitlines()
    assert "sources=[('xkb', 'us'), ('xkb', 'de')]" in defaults


@pytest.mark.parametrize('profile, expected', [
    # The databases of a profile take precedence in their order: the distro one shall not override the local one
    ('user-db:user\nsystem-db:distro\n', 'user-db:user\nsystem-db:local\nsystem-db:distro\n'),
    ('system-db:distro\n', 'system-db:local\nsystem-db:distro\n'),
    ('user-db:user\nsystem-db:local\nsystem-db:distro\n', 'user-db:user\nsystem-db:local\nsystem-db:distro\n'),
])
def test_configure_puts_the_local_database_right_after_the_user_one(gnome_desktop_setup, dconf_dir, profile, expected):
    dconf_dir.ensure('profile', 'user').write(profile)

    # The configuration is applied at each start of a desktop
    assert gnome_desktop_setup('configure', 'Task')[0] == 0
    assert gnome_desktop_setup('configure', 'Task')[0] == 0

    assert dconf_dir.join('profile', 'user').read() == expected


def test_configure_reports_defaults_which_cannot_be_applied(gnome_desktop_setup):
    exit_code, calls = gnome_desktop_setup('configure', 'Task', DCONF_EXIT_CODE='1')

    assert exit_code == 0
    assert [args for args in _calls_of(calls, 'pipe_log_warn') if 'dconf' in args[0] and args[1] == 'Task']


def test_unknown_command_is_rejected(gnome_desktop_setup):
    exit_code, calls = gnome_desktop_setup('remove')

    assert exit_code == 2
    assert not calls
