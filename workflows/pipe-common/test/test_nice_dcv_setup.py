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

# Covers the NICE DCV setup of workflows/pipe-common/shell/nice_dcv_setup on an RPM-based OS: the packages it
# installs, the DCV distribution it takes, the configuration it writes and the services it starts, on Rocky Linux 10
# (GNOME on Wayland) and on the other distributions (Xfce on X11). Runs the script with bash, with the absolute paths
# it writes to replaced by temporary ones. yum, rpm, wget, tar, systemctl, dconf, curl, usermod, nohup, the pipe_log_*
# commands, nice_dcv_create_session and the Python of the run are replaced by stubs, which record their calls: one
# line per call, the command and its arguments separated by tabs. The tar stub creates the files of the DCV
# distribution.

import os
import stat
import subprocess

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
NICE_DCV_SETUP = os.path.join(SHELL_DIR, 'nice_dcv_setup')

RECORDING_STUB = '''#!/bin/bash
{ printf '%s' "$(basename "$0")"; [ $# -gt 0 ] && printf '\\t%s' "$@"; printf '\\n'; } >> "$CALLS"
'''

# rpm -q -f /usr/bin/rpm succeeds (an RPM-based OS), rpm -qa lists the DCV server only if it is said to be installed,
# and rpm -q coreutils-single succeeds only if the image is said to have it
RPM_STUB = RECORDING_STUB + '''if [ "$1" == "-qa" ] && [ "$DCV_INSTALLED" == "true" ]; then
    echo nice-dcv-server-2026.0.25174-1.el10.x86_64
fi
if [ "$1" == "-q" ] && [ "$2" == "coreutils-single" ]; then
    [ "$HAS_COREUTILS_SINGLE" == "true" ]
    exit $?
fi
'''

# yum fails to replace coreutils-single from the repositories of the run, if it is said to
YUM_STUB = RECORDING_STUB + '''if [ "$COREUTILS_SWAP_FAILS" == "true" ] && [[ " $* " == *" --allowerasing coreutils "* ]] \\
        && [[ " $* " != *" --disablerepo=cloud-pipeline "* ]]; then
    exit 1
fi
'''

# systemctl fails to start GDM, if it is said to
SYSTEMCTL_STUB = RECORDING_STUB + '''if [ "$GDM_FAILS" == "true" ] && [ "$1" == "start" ] && [ "$2" == "gdm" ]; then
    exit 1
fi
'''

# tar -xvzf <distribution>.tgz: the folder of the distribution with its packages
TAR_STUB = RECORDING_STUB + '''_distribution="$(basename "${@: -1}" .tgz)"
mkdir -p "$_distribution"
if [[ "$_distribution" == *el10* ]]; then
    _packages="nice-dcv-server-2026.0.25174-1.el10.x86_64.rpm nice-dcv-web-viewer-2026.0.25174-1.el10.x86_64.rpm
               nice-dcv-gnome-shell-extension-2026.0.81-1.el10.noarch.rpm
               nice-dcv-simple-external-authenticator-2026.0.301-1.el10.x86_64.rpm"
else
    _packages="nice-dcv-server-2024.0.19030-1.el9.x86_64.rpm nice-dcv-web-viewer-2024.0.19030-1.el9.x86_64.rpm
               nice-xdcv-2024.0.654-1.el9.x86_64.rpm nice-dcv-gl-2024.0.1078-1.el9.x86_64.rpm"
fi
for _package in $_packages; do
    touch "$_distribution/$_package"
done
'''

# The [security] section of /etc/dcv/dcv.conf of the DCV packages, with the keys that the setup changes
DCV_CONF = '''[license]

[connectivity]
#web-port=8444

[security]
#authentication="none"
'''

ROCKY_10 = {'CP_OS': 'rocky', 'CP_VER': '10.2', 'CP_VER_MAJOR': '10'}
ROCKY_9 = {'CP_OS': 'rocky', 'CP_VER': '9.8', 'CP_VER_MAJOR': '9'}

GNOME_MASKED_UNITS = ['NetworkManager.service', 'firewalld.service', 'chronyd.service', 'tuned.service',
                      'irqbalance.service', 'ModemManager.service', 'wpa_supplicant.service', 'cups.service',
                      'cups.socket', 'cups.path', 'avahi-daemon.service', 'avahi-daemon.socket', 'bluetooth.service',
                      'sleep.target', 'suspend.target', 'hibernate.target', 'hybrid-sleep.target',
                      'suspend-then-hibernate.target']
GNOME_SERVICES = [['mask', '--now'] + GNOME_MASKED_UNITS, ['start', 'dbus'], ['start', 'gdm'], ['start', 'dcvserver']]


def _stub(directory, name, body):
    stub = directory.join(name)
    stub.write(body)
    os.chmod(str(stub), stat.S_IRWXU)


@pytest.fixture
def root(tmpdir):
    """The temporary folder, which stands for / of the run."""
    root = tmpdir.mkdir('root')
    root.ensure('etc', 'dcv', 'dcv.conf').write(DCV_CONF)
    root.ensure('var', 'log', dir=True)
    return root


@pytest.fixture
def nice_dcv_setup(tmpdir, root):
    stub_bin = tmpdir.mkdir('stub-bin')
    for name in ('pipe_log_info', 'pipe_log_warn', 'pipe_log_fail', 'pipe_log_success', 'wget', 'dconf', 'curl',
                 'usermod', 'nohup', 'nice_dcv_create_session', 'python'):
        _stub(stub_bin, name, RECORDING_STUB)
    _stub(stub_bin, 'rpm', RPM_STUB)
    _stub(stub_bin, 'yum', YUM_STUB)
    _stub(stub_bin, 'systemctl', SYSTEMCTL_STUB)
    _stub(stub_bin, 'tar', TAR_STUB)
    work_dir = tmpdir.mkdir('work')
    calls = tmpdir.join('calls')
    with open(NICE_DCV_SETUP) as f:
        script = f.read() \
            .replace('/usr/bin/rpm -q -f /usr/bin/rpm', 'rpm -q -f /usr/bin/rpm') \
            .replace('/etc/dcv/', str(root.join('etc', 'dcv')) + '/') \
            .replace('/etc/dconf', str(root.join('etc', 'dconf'))) \
            .replace('/opt/dcv/extras', str(root.join('opt', 'dcv', 'extras'))) \
            .replace('/var/log/', str(root.join('var', 'log')) + '/')

    def run(os_version, has_coreutils_single=False, dcv_installed=False, **extra_env):
        if calls.exists():
            calls.remove()
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls),
            'CLOUD_PROVIDER': 'AWS',
            'OWNER': 'user1',
            'GLOBAL_DISTRIBUTION_URL': 'https://distribution.example/',
            'CP_PYTHON_PATH': str(stub_bin.join('python')),
            'COMMON_REPO_DIR': str(tmpdir.join('pipe-common')),
            'CP_DCV_RETRIES_COUNT': '1',
            'CP_DCV_RETRY_TIMEOUT': '0',
            'HAS_COREUTILS_SINGLE': 'true' if has_coreutils_single else 'false',
            'DCV_INSTALLED': 'true' if dcv_installed else 'false'
        }
        env.update({key: value for key, value in os_version.items() if value is not None})
        env.update(extra_env)
        exit_code = subprocess.call(['bash', '-c', script], env=env, cwd=str(work_dir))
        lines = calls.read().splitlines() if calls.exists() else []
        return exit_code, [line.split('\t') for line in lines]

    return run


def _calls_of(calls, command):
    return [call[1:] for call in calls if call[0] == command]


def _installed_packages(calls):
    return [arg for args in _calls_of(calls, 'yum') for arg in args if not arg.startswith('-')]


def _warnings(calls):
    return [args[0] for args in _calls_of(calls, 'pipe_log_warn')]


def test_rocky_10_installs_the_gnome_desktop_instead_of_xfce(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    yum_calls = _calls_of(calls, 'yum')
    assert ['-y', 'groupinstall', '--exclude=kernel', '--exclude=kernel-core', '--exclude=kernel-modules*',
            '--exclude=kernel-tools*', '--exclude=kmod-kvdo', '--exclude=*-firmware', '--exclude=vdo',
            'Server with GUI'] in yum_calls
    assert ['-y', 'install', 'egl-utils'] in yum_calls
    # Rocky Linux 10 and EPEL 10 have neither an X server, nor Xfce
    packages = _installed_packages(calls)
    assert 'Xfce' not in packages
    assert 'epel-release' not in packages
    assert not [package for package in packages if package.startswith('xorg-x11-server')]
    assert not _calls_of(calls, 'pipe_log_fail')


def test_rocky_10_replaces_coreutils_single_before_the_gnome_desktop(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_10, has_coreutils_single=True)

    assert exit_code == 0
    yum_calls = _calls_of(calls, 'yum')
    swap = yum_calls.index(['-y', 'install', '--allowerasing', 'coreutils'])
    group = [i for i, args in enumerate(yum_calls) if 'Server with GUI' in args][0]
    assert swap < group


def test_rocky_10_replaces_coreutils_single_from_an_upstream_repo_if_needed(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_10, has_coreutils_single=True, COREUTILS_SWAP_FAILS='true')

    assert exit_code == 0
    yum_calls = _calls_of(calls, 'yum')
    assert yum_calls.index(['-y', 'install', '--allowerasing', 'coreutils']) + 1 == \
        yum_calls.index(['-y', 'install', '--allowerasing', '--disablerepo=cloud-pipeline', 'coreutils'])


def test_rocky_10_keeps_coreutils_if_the_image_has_the_full_one(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_10, has_coreutils_single=False)

    assert exit_code == 0
    assert not [args for args in _calls_of(calls, 'yum') if '--allowerasing' in args]


def test_rocky_10_installs_the_el10_distribution_with_the_gnome_shell_extension(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    assert _calls_of(calls, 'wget') == [
        ['https://distribution.example/tools/dcv/nice-dcv-2026.0-25174-el10-x86_64.tgz']]
    assert ['install', '-y', 'nice-dcv-server-2026.0.25174-1.el10.x86_64.rpm',
            'nice-dcv-web-viewer-2026.0.25174-1.el10.x86_64.rpm',
            'nice-dcv-gnome-shell-extension-2026.0.81-1.el10.noarch.rpm'] in _calls_of(calls, 'yum')
    assert not [package for package in _installed_packages(calls) if 'xdcv' in package]


def test_rocky_10_distribution_can_be_overridden(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_10, CP_CAP_DCV_DISTRIBUTION='nice-dcv-2026.1-26000-el10-x86_64')

    assert exit_code == 0
    assert _calls_of(calls, 'wget') == [
        ['https://distribution.example/tools/dcv/nice-dcv-2026.1-26000-el10-x86_64.tgz']]


@pytest.mark.parametrize('os_version, distribution, wayland', [
    ({'CP_OS': 'centos', 'CP_VER': '7.9', 'CP_VER_MAJOR': '7'}, 'nice-dcv-2021.2-11190-el7-x86_64', False),
    ({'CP_OS': 'rocky', 'CP_VER': '8.7', 'CP_VER_MAJOR': '8'}, 'nice-dcv-2021.2-11445-el8-x86_64', False),
    ({'CP_OS': 'rocky', 'CP_VER': '9.8', 'CP_VER_MAJOR': '9'}, 'nice-dcv-2024.0-19030-el9-x86_64', False),
    # The major version is taken from CP_VER, if CP_VER_MAJOR is not set
    ({'CP_OS': 'rocky', 'CP_VER': '10.2', 'CP_VER_MAJOR': None}, 'nice-dcv-2026.0-25174-el10-x86_64', True),
    ({'CP_OS': 'rhel', 'CP_VER': '10.1', 'CP_VER_MAJOR': '10'}, 'nice-dcv-2026.0-25174-el10-x86_64', True),
    ({'CP_OS': 'centos', 'CP_VER': '10', 'CP_VER_MAJOR': '10'}, 'nice-dcv-2026.0-25174-el10-x86_64', True),
    # DCV does not support Wayland on the other distributions, so they keep the X11 packages, as before
    ({'CP_OS': 'amzn', 'CP_VER': '2023', 'CP_VER_MAJOR': '2023'}, 'nice-dcv-2024.0-19030-el9-x86_64', False),
    ({'CP_OS': 'ol', 'CP_VER': '10.0', 'CP_VER_MAJOR': '10'}, 'nice-dcv-2024.0-19030-el9-x86_64', False),
])
def test_distribution_depends_on_the_os(nice_dcv_setup, os_version, distribution, wayland):
    exit_code, calls = nice_dcv_setup(os_version)

    assert exit_code == 0
    assert _calls_of(calls, 'wget') == [['https://distribution.example/tools/dcv/%s.tgz' % distribution]]
    packages = _installed_packages(calls)
    assert ('Server with GUI' in packages) == wayland
    assert ('Xfce' in packages) != wayland


def test_unknown_major_version_takes_the_el8_distribution(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup({'CP_OS': 'rocky', 'CP_VER': 'latest', 'CP_VER_MAJOR': None})

    assert exit_code == 0
    assert _calls_of(calls, 'wget') == [
        ['https://distribution.example/tools/dcv/nice-dcv-2021.2-11445-el8-x86_64.tgz']]
    assert [warning for warning in _warnings(calls) if "CP_VER='latest'" in warning]


def test_rocky_10_does_not_lock_the_session_when_its_last_client_disconnects(nice_dcv_setup, root):
    exit_code, _ = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    dcv_conf = root.join('etc', 'dcv', 'dcv.conf').read().splitlines()
    assert dcv_conf[dcv_conf.index('[security]') + 1] == 'os-auto-lock=false'
    assert dcv_conf.count('os-auto-lock=false') == 1
    assert 'authentication="none"' in dcv_conf
    assert 'web-port=8100' in dcv_conf


def test_rocky_10_sets_the_gnome_defaults(nice_dcv_setup, root):
    exit_code, calls = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    defaults = root.join('etc', 'dconf', 'db', 'local.d', '00-cloud-pipeline').read().splitlines()
    assert "picture-uri=''" in defaults
    assert "picture-uri-dark=''" in defaults
    assert "sources=[('xkb', 'us'), ('xkb', 'fr'), ('xkb', 'de')]" in defaults
    assert 'lock-enabled=false' in defaults
    assert 'idle-delay=uint32 0' in defaults
    assert "welcome-dialog-last-shown-version='999'" in defaults
    # An idle desktop of the run shall not suspend the node
    assert "sleep-inactive-ac-type='nothing'" in defaults
    assert "sleep-inactive-battery-type='nothing'" in defaults
    assert root.join('etc', 'dconf', 'profile', 'user').read() == 'user-db:user\nsystem-db:local\n'
    assert _calls_of(calls, 'dconf') == [['update']]
    # The Xfce launcher is not used on Wayland
    assert not root.join('opt', 'dcv', 'extras', 'xfce4launch').exists()


def test_rocky_10_login_screen_neither_blanks_nor_suspends(nice_dcv_setup, root):
    exit_code, _ = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    assert root.join('etc', 'dconf', 'db', 'gdm.d', '00-cloud-pipeline').read() == \
        "[org/gnome/desktop/session]\nidle-delay=uint32 0\n\n" \
        "[org/gnome/settings-daemon/plugins/power]\n" \
        "sleep-inactive-ac-type='nothing'\nsleep-inactive-battery-type='nothing'\n"


def test_rocky_10_takes_the_keyboard_layouts_of_the_run(nice_dcv_setup, root):
    exit_code, _ = nice_dcv_setup(ROCKY_10, CP_DCV_XKB_LAYOUTS='us,de')

    assert exit_code == 0
    defaults = root.join('etc', 'dconf', 'db', 'local.d', '00-cloud-pipeline').read().splitlines()
    assert "sources=[('xkb', 'us'), ('xkb', 'de')]" in defaults


def test_rocky_10_puts_the_local_database_right_after_the_user_one(nice_dcv_setup, root):
    # The databases of a profile take precedence in their order: the distro one shall not override the local one
    root.ensure('etc', 'dconf', 'profile', 'user').write('user-db:user\nsystem-db:distro\n')

    exit_code, _ = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    assert root.join('etc', 'dconf', 'profile', 'user').read() == 'user-db:user\nsystem-db:local\nsystem-db:distro\n'


def test_rocky_10_puts_the_local_database_first_into_a_profile_without_the_user_one(nice_dcv_setup, root):
    root.ensure('etc', 'dconf', 'profile', 'user').write('system-db:distro\n')

    exit_code, _ = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    assert root.join('etc', 'dconf', 'profile', 'user').read() == 'system-db:local\nsystem-db:distro\n'


def test_rocky_10_keeps_a_dconf_profile_which_has_the_local_database(nice_dcv_setup, root):
    profile = 'user-db:user\nsystem-db:local\nsystem-db:site\nsystem-db:distro\n'
    root.ensure('etc', 'dconf', 'profile', 'user').write(profile)

    exit_code, _ = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    assert root.join('etc', 'dconf', 'profile', 'user').read() == profile


def test_rocky_10_starts_gdm_without_the_other_services_of_the_desktop(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_10)

    assert exit_code == 0
    assert _calls_of(calls, 'systemctl') == GNOME_SERVICES
    assert _calls_of(calls, 'nice_dcv_create_session') == [[]]


def test_rocky_10_reports_gdm_which_cannot_be_started(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_10, GDM_FAILS='true')

    assert exit_code == 0
    assert [warning for warning in _warnings(calls) if 'GDM cannot be started' in warning]
    assert ['start', 'dcvserver'] in _calls_of(calls, 'systemctl')


def test_rocky_10_with_dcv_preinstalled_configures_the_desktop_at_each_start(nice_dcv_setup, root):
    root.ensure('etc', 'dconf', 'profile', 'user').write('user-db:user\nsystem-db:distro\n')

    for _ in range(2):
        exit_code, calls = nice_dcv_setup(ROCKY_10, dcv_installed=True)

        assert exit_code == 0
        # Nothing is installed or configured in DCV itself
        assert not _calls_of(calls, 'yum')
        assert not _calls_of(calls, 'wget')
        assert '#authentication="none"' in root.join('etc', 'dcv', 'dcv.conf').read().splitlines()
        # The desktop is configured and started
        assert root.join('etc', 'dconf', 'db', 'local.d', '00-cloud-pipeline').check(file=True)
        assert root.join('etc', 'dconf', 'db', 'gdm.d', '00-cloud-pipeline').check(file=True)
        assert _calls_of(calls, 'dconf') == [['update']]
        assert _calls_of(calls, 'systemctl') == GNOME_SERVICES

    assert root.join('etc', 'dconf', 'profile', 'user').read() == 'user-db:user\nsystem-db:local\nsystem-db:distro\n'


def test_rocky_9_installs_xfce_and_xdcv_as_before(nice_dcv_setup):
    exit_code, calls = nice_dcv_setup(ROCKY_9)

    assert exit_code == 0
    packages = _installed_packages(calls)
    assert 'epel-release' in packages
    assert 'xorg-x11-server-Xorg' in packages
    assert ['-y', 'groupinstall', 'Xfce'] in _calls_of(calls, 'yum')
    assert 'Server with GUI' not in packages
    assert ['install', '-y', 'nice-dcv-server-2024.0.19030-1.el9.x86_64.rpm',
            'nice-dcv-web-viewer-2024.0.19030-1.el9.x86_64.rpm',
            'nice-xdcv-2024.0.654-1.el9.x86_64.rpm'] in _calls_of(calls, 'yum')


def test_rocky_9_keeps_the_xfce_session_and_the_services_as_before(nice_dcv_setup, root):
    exit_code, calls = nice_dcv_setup(ROCKY_9)

    assert exit_code == 0
    assert root.join('opt', 'dcv', 'extras', 'xfce4launch').check(file=True)
    assert not root.join('etc', 'dconf').exists()
    assert not [line for line in root.join('etc', 'dcv', 'dcv.conf').read().splitlines()
                if line.startswith('os-auto-lock')]
    assert _calls_of(calls, 'systemctl') == [['start', 'dbus'], ['start', 'dcvserver']]
    assert not _calls_of(calls, 'dconf')
