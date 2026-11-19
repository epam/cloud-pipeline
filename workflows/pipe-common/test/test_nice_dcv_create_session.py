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

# Covers the creation of a NICE DCV session by workflows/pipe-common/shell/nice_dcv_create_session: the options of
# "dcv create-session" on X11 (DCV has its X server, Xdcv) and on Wayland (no Xdcv and the DCV GNOME Shell extension,
# e.g. Rocky Linux 10), and the GDM login on Wayland: passwordless for the user of a session, who is listed on the
# login screen. Runs the script with bash, with the PAM configuration of GDM and the users folder of AccountsService
# replaced by temporary ones. dcv, Xdcv, rpm, busctl and the pipe_log_* commands are replaced by stubs, which record
# their calls: one line per call, the command and its arguments separated by tabs. The busctl stub stands for
# accounts-daemon: it fails, unless the daemon is said to be available.

import os
import stat
import subprocess

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
NICE_DCV_CREATE_SESSION = os.path.join(SHELL_DIR, 'nice_dcv_create_session')

RECORDING_STUB = '''#!/bin/bash
{ printf '%s' "$(basename "$0")"; [ $# -gt 0 ] && printf '\\t%s' "$@"; printf '\\n'; } >> "$CALLS"
'''

DCV_STUB = RECORDING_STUB + '''exit ${DCV_EXIT_CODE:-0}
'''

# rpm -q nice-dcv-gnome-shell-extension succeeds only if the extension is said to be installed
RPM_STUB = RECORDING_STUB + '''if [ "$1" == "-q" ] && [ "$2" == "nice-dcv-gnome-shell-extension" ]; then
    [ "$GNOME_EXTENSION" == "true" ]
    exit $?
fi
exit 1
'''

# /etc/pam.d/gdm-password of the Rocky Linux 10 GDM package
GDM_PASSWORD_PAM = '''auth     [success=done ignore=ignore default=bad] pam_selinux_permit.so
auth        substack      gdm-password-auth-substack
auth        optional      pam_gnome_keyring.so
auth        include       postlogin

account     required      pam_nologin.so
account     include       password-auth

password    substack       password-auth
-password   optional       pam_gnome_keyring.so use_authtok

session     required      pam_selinux.so close
session     required      pam_loginuid.so
session     required      pam_selinux.so open
session     optional      pam_keyinit.so force revoke
session     required      pam_namespace.so
session     include       password-auth
session     optional      pam_gnome_keyring.so auto_start
session     include       postlogin
'''

# busctl call ... CacheUser s <user>: accounts-daemon registers the user, if it is said to be available
BUSCTL_STUB = RECORDING_STUB + '''[ "$ACCOUNTS_DAEMON" == "true" ]
'''

ACCOUNTS_USER = '[User]\nSystemAccount=false\n'


def _stub(directory, name, body):
    stub = directory.join(name)
    stub.write(body)
    os.chmod(str(stub), stat.S_IRWXU)


@pytest.fixture
def gdm_password_pam(tmpdir):
    pam = tmpdir.ensure('pam.d', 'gdm-password')
    pam.write(GDM_PASSWORD_PAM)
    return pam


@pytest.fixture
def accounts_users(tmpdir):
    """The users folder of AccountsService, which does not exist before."""
    return tmpdir.join('AccountsService', 'users')


@pytest.fixture
def nice_dcv_create_session(tmpdir, gdm_password_pam, accounts_users):
    stub_bin = tmpdir.mkdir('stub-bin')
    for name in ('pipe_log_info', 'pipe_log_warn', 'pipe_log_fail'):
        _stub(stub_bin, name, RECORDING_STUB)
    _stub(stub_bin, 'dcv', DCV_STUB)
    _stub(stub_bin, 'rpm', RPM_STUB)
    _stub(stub_bin, 'busctl', BUSCTL_STUB)
    calls = tmpdir.join('calls')
    with open(NICE_DCV_CREATE_SESSION) as f:
        script = f.read() \
            .replace('/etc/pam.d/gdm-password', str(gdm_password_pam)) \
            .replace('/var/lib/AccountsService/users', str(accounts_users))

    def run(desktop='wayland', args=(), dcv_exit_code=0, accounts_daemon=False, **extra_env):
        """desktop: 'wayland' - the GNOME Shell extension and no Xdcv, 'x11' - Xdcv, 'unknown' - neither."""
        if desktop == 'x11':
            _stub(stub_bin, 'Xdcv', RECORDING_STUB)
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls),
            'OWNER': 'john.doe',
            'GNOME_EXTENSION': 'true' if desktop == 'wayland' else 'false',
            'DCV_EXIT_CODE': str(dcv_exit_code),
            'ACCOUNTS_DAEMON': 'true' if accounts_daemon else 'false'
        }
        env.update(extra_env)
        exit_code = subprocess.call(['bash', '-c', script, 'nice_dcv_create_session'] + list(args), env=env)
        lines = calls.read().splitlines() if calls.exists() else []
        return exit_code, [line.split('\t') for line in lines]

    return run


def _calls_of(calls, command):
    return [call[1:] for call in calls if call[0] == command]


def _login_rule(user):
    return 'auth        sufficient    pam_succeed_if.so quiet user = %s' % user


def test_wayland_session_is_a_virtual_one_without_an_initialization_script(nice_dcv_create_session):
    exit_code, calls = nice_dcv_create_session()

    assert exit_code == 0
    assert _calls_of(calls, 'dcv') == [['create-session', '--type', 'virtual', '--owner', 'john.doe',
                                        '--user', 'john.doe', 'johndoe-session']]
    assert not _calls_of(calls, 'pipe_log_fail')


def test_wayland_session_does_not_use_a_configured_initialization_script(nice_dcv_create_session):
    exit_code, calls = nice_dcv_create_session(CP_DCV_INIT_SCRIPT='/opt/custom/init')

    assert exit_code == 0
    assert '--init' not in _calls_of(calls, 'dcv')[0]
    assert [args for args in _calls_of(calls, 'pipe_log_warn') if '/opt/custom/init' in args[0]]


def test_wayland_gdm_login_is_passwordless_for_the_user_of_the_session(nice_dcv_create_session, gdm_password_pam):
    exit_code, _ = nice_dcv_create_session()

    assert exit_code == 0
    # The rule goes first, as "sufficient" ends the auth stack only for the modules after it
    assert gdm_password_pam.read() == _login_rule('john.doe') + '\n' + GDM_PASSWORD_PAM


def test_wayland_gdm_login_rule_is_added_once(nice_dcv_create_session, gdm_password_pam):
    assert nice_dcv_create_session()[0] == 0
    assert nice_dcv_create_session()[0] == 0

    assert gdm_password_pam.read().splitlines().count(_login_rule('john.doe')) == 1


def test_wayland_gdm_login_is_passwordless_for_the_given_user_only(nice_dcv_create_session, gdm_password_pam,
                                                                   accounts_users):
    exit_code, calls = nice_dcv_create_session(args=['jane'])

    assert exit_code == 0
    assert gdm_password_pam.read() == _login_rule('jane') + '\n' + GDM_PASSWORD_PAM
    assert accounts_users.listdir() == [accounts_users.join('jane')]
    assert _calls_of(calls, 'dcv') == [['create-session', '--type', 'virtual', '--owner', 'jane',
                                        '--user', 'jane', 'jane-session']]


def test_wayland_session_is_created_without_the_gdm_pam_config(nice_dcv_create_session, gdm_password_pam):
    gdm_password_pam.remove()

    exit_code, calls = nice_dcv_create_session()

    assert exit_code == 0
    assert not gdm_password_pam.exists()
    assert len(_calls_of(calls, 'dcv')) == 1
    assert [args for args in _calls_of(calls, 'pipe_log_warn') if 'password' in args[0]]


def test_wayland_user_of_the_session_is_registered_by_accounts_daemon(nice_dcv_create_session, accounts_users):
    # accounts-daemon does not watch its folder of users, so the daemon itself registers the user
    exit_code, calls = nice_dcv_create_session(accounts_daemon=True)

    assert exit_code == 0
    assert _calls_of(calls, 'busctl') == [['call', 'org.freedesktop.Accounts', '/org/freedesktop/Accounts',
                                           'org.freedesktop.Accounts', 'CacheUser', 's', 'john.doe']]
    assert not accounts_users.exists()


def test_wayland_user_of_the_session_is_written_to_accounts_service_without_its_daemon(nice_dcv_create_session,
                                                                                      accounts_users):
    exit_code, calls = nice_dcv_create_session(accounts_daemon=False)

    assert exit_code == 0
    assert len(_calls_of(calls, 'busctl')) == 1
    assert accounts_users.join('john.doe').read() == ACCOUNTS_USER


def test_wayland_keeps_the_accounts_service_record_of_a_user(nice_dcv_create_session, accounts_users):
    # AccountsService keeps its own record of a user, who logged in before
    record = '[User]\nSession=gnome\nIcon=/home/john.doe/.face\nSystemAccount=false\n'
    accounts_users.ensure('john.doe').write(record)

    exit_code, _ = nice_dcv_create_session()

    assert exit_code == 0
    assert accounts_users.join('john.doe').read() == record


@pytest.mark.parametrize('desktop', ['x11', 'unknown'])
def test_x11_session_uses_the_xfce_initialization_script(nice_dcv_create_session, gdm_password_pam, accounts_users,
                                                         desktop):
    exit_code, calls = nice_dcv_create_session(desktop=desktop)

    assert exit_code == 0
    assert _calls_of(calls, 'dcv') == [['create-session', '--owner', 'john.doe', '--user', 'john.doe',
                                        '--init', '/opt/dcv/extras/xfce4launch', 'johndoe-session']]
    # An X11 session has no GDM login screen, so neither its PAM configuration, nor the users are changed
    assert gdm_password_pam.read() == GDM_PASSWORD_PAM
    assert not accounts_users.exists()
    assert not _calls_of(calls, 'busctl')


def test_x11_session_uses_a_configured_initialization_script(nice_dcv_create_session):
    exit_code, calls = nice_dcv_create_session(desktop='x11', CP_DCV_INIT_SCRIPT='/opt/custom/init')

    assert exit_code == 0
    assert _calls_of(calls, 'dcv')[0][-3:] == ['--init', '/opt/custom/init', 'johndoe-session']


@pytest.mark.parametrize('desktop', ['wayland', 'x11'])
def test_failed_session_is_reported(nice_dcv_create_session, desktop):
    _, calls = nice_dcv_create_session(desktop=desktop, dcv_exit_code=1)

    assert [args for args in _calls_of(calls, 'pipe_log_fail') if 'john.doe' in args[0]]
