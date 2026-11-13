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

# Covers the install of NICE DCV by workflows/pipe-common/shell/nice_dcv_setup: which DCV build install_dcv takes for
# the major version of an RPM based OS, or the one CP_CAP_DCV_DISTRIBUTION names, and for the Ubuntu version; and how
# install_prerequisites handles the optional packages: those Rocky Linux 9 does not provide, and those of the keyboard
# layout switcher of the desktop.
# Runs install_optional_rpm_pkgs, install_optional_deb_pkgs, install_prerequisites and install_dcv with bash. yum, apt,
# rpm, gpg, wget, tar, the pipe_log_* commands and the python interpreter are replaced by stubs, which record their
# calls.

import os
import subprocess

import pytest

SHELL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'shell')
NICE_DCV_SETUP = os.path.join(SHELL_DIR, 'nice_dcv_setup')

DISTRO_URL = 'https://distribution.test/'
EL7 = 'nice-dcv-2021.2-11190-el7-x86_64'
EL8 = 'nice-dcv-2021.2-11445-el8-x86_64'
EL9 = 'nice-dcv-2024.0-19030-el9-x86_64'
OPTIONAL_PACKAGES = ['libXvMC', 'mesa-libxatracker', 'xfce4-xkb-plugin', 'setxkbmap', 'xorg-x11-xkb-utils', 'xrandr',
                     'xorg-x11-server-utils']
DEB_OPTIONAL_PACKAGES = ['xfce4-xkb-plugin', 'x11-xkb-utils']

# yum and apt fail a whole install if one of the packages is missing, as dnf does by default.
# STUB_CP_REPO_BROKEN fails every yum call that does not disable the cloud-pipeline repository
STUBS = r'''
record() { printf '%s\n' "$*" >> "$CALLS"; }
pipe_log_info() { record "info $1"; }
pipe_log_warn() { record "warn $1"; }
pipe_log_fail() { record "fail $1"; }
python_stub() { record "python $*"; }
rpm() { record "rpm $*"; }
gpg() { record "gpg $*"; }
wget() { record "wget $*"; }
tar() {
    record "tar $*"
    mkdir -p "$(basename "$2" .tgz)"
}
yum() {
    record "yum $*"
    case " $* " in
        *" --disablerepo=cloud-pipeline "*) ;;
        *) [ "$STUB_CP_REPO_BROKEN" == "true" ] && return 1 ;;
    esac
    local _arg
    for _arg in "$@"; do
        case " $STUB_MISSING " in
            *" $_arg "*) return 1 ;;
        esac
    done
    return 0
}
apt() {
    record "apt $*"
    local _arg
    for _arg in "$@"; do
        case " $STUB_MISSING " in
            *" $_arg "*) return 1 ;;
        esac
    done
    return 0
}
'''


def _function_source(name):
    with open(NICE_DCV_SETUP) as f:
        text = f.read()
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _run(tmpdir, call, **env_vars):
    calls = tmpdir.join('calls')
    calls.write('')
    work_dir = tmpdir.mkdir('work')
    script = STUBS + \
        _function_source('install_optional_rpm_pkgs') + \
        _function_source('install_optional_deb_pkgs') + \
        _function_source('install_prerequisites') + \
        _function_source('install_dcv') + \
        call + '\necho "result $?" >> "$CALLS"\n'
    env = dict(os.environ)
    for name in ('CP_VER', 'CP_VER_MAJOR', 'CP_CAP_DCV_DISTRIBUTION', 'CP_CAP_DCV_DISTRO_URL',
                 'STUB_MISSING', 'STUB_CP_REPO_BROKEN'):
        env.pop(name, None)
    env.update({
        'CALLS': str(calls),
        'IS_RPM_BASED': '0',
        'CP_OS': 'rocky',
        'GLOBAL_DISTRIBUTION_URL': DISTRO_URL,
        'CP_PYTHON_PATH': 'python_stub',
        'CP_PYTHON_VERSION': '3',
    })
    env.update(env_vars)
    subprocess.check_call(['bash', '-c', script], env=env, cwd=str(work_dir))
    return calls.read().splitlines()


def _downloaded_build(calls):
    prefix = 'wget ' + DISTRO_URL + 'tools/dcv/'
    builds = [call[len(prefix):-len('.tgz')] for call in calls if call.startswith(prefix) and call.endswith('.tgz')]
    assert len(builds) == 1, calls
    return builds[0]


@pytest.mark.parametrize('version, major, build', [
    ('7.9.2009', '7', EL7),
    ('8.9', '8', EL8),
    ('8.7', '8', EL8),
    ('9.8', '9', EL9),
    ('9.7', '9', EL9),
    ('10.0', '10', EL9),
])
def test_install_dcv_takes_the_build_of_the_major_version(tmpdir, version, major, build):
    calls = _run(tmpdir, 'install_dcv', CP_VER=version, CP_VER_MAJOR=major)

    assert _downloaded_build(calls) == build
    assert 'info DCV distribution to be installed: %s' % build in calls
    assert 'result 0' in calls


def test_install_dcv_takes_the_major_version_from_the_version_if_it_is_not_set(tmpdir):
    calls = _run(tmpdir, 'install_dcv', CP_VER='9.8')

    assert _downloaded_build(calls) == EL9


def test_install_dcv_takes_the_el8_build_if_the_version_is_not_a_number(tmpdir):
    calls = _run(tmpdir, 'install_dcv', CP_VER='rolling')

    assert _downloaded_build(calls) == EL8
    assert any(call.startswith('warn [WARNING] Cannot resolve a major version') for call in calls)


@pytest.mark.parametrize('major, imports_key', [('7', True), ('8', False), ('9', True)])
def test_install_dcv_imports_the_nice_key_for_the_el7_and_el9_builds(tmpdir, major, imports_key):
    calls = _run(tmpdir, 'install_dcv', CP_VER=major + '.0', CP_VER_MAJOR=major)

    assert (('rpm --import %stools/dcv/NICE-GPG-KEY' % DISTRO_URL) in calls) == imports_key


@pytest.mark.parametrize('major', ['7', '8', '9'])
def test_install_dcv_takes_the_build_the_parameter_names(tmpdir, major):
    calls = _run(tmpdir, 'install_dcv', CP_VER=major + '.0', CP_VER_MAJOR=major,
                 CP_CAP_DCV_DISTRIBUTION='nice-dcv-2023.1-16388-el9-x86_64')

    assert _downloaded_build(calls) == 'nice-dcv-2023.1-16388-el9-x86_64'


def test_install_dcv_installs_the_server_viewer_and_xdcv_packages_of_the_build(tmpdir):
    calls = _run(tmpdir, 'install_dcv', CP_VER='9.8', CP_VER_MAJOR='9')

    assert 'yum install -y nice-dcv-server-*.x86_64.rpm nice-dcv-web-viewer-*.x86_64.rpm ' \
           'nice-xdcv-*.x86_64.rpm' in calls
    assert 'info DCV successfully installed and configured' in calls


def test_install_prerequisites_requires_no_optional_package(tmpdir):
    calls = _run(tmpdir, 'install_prerequisites', CP_VER='9.8', CP_VER_MAJOR='9',
                 STUB_MISSING=' '.join(OPTIONAL_PACKAGES))

    main_install = [call for call in calls if call.startswith('yum -y install glx-utils ')]
    assert len(main_install) == 1
    for package in OPTIONAL_PACKAGES:
        assert (' %s ' % package) not in (main_install[0] + ' ')
        assert 'yum -y install %s' % package in calls
        assert 'yum -y install --disablerepo=cloud-pipeline %s' % package in calls
        assert 'warn [WARNING] Optional DCV prerequisite %s is not available, skipping it' % package in calls
    assert not any(call.startswith('fail ') for call in calls)
    assert any(call.startswith('python -m pip install') for call in calls)
    assert 'result 0' in calls


def test_install_prerequisites_installs_the_optional_packages_where_they_are(tmpdir):
    calls = _run(tmpdir, 'install_prerequisites', CP_VER='8.9', CP_VER_MAJOR='8')

    for package in OPTIONAL_PACKAGES:
        assert 'yum -y install %s' % package in calls
        assert 'yum -y install --disablerepo=cloud-pipeline %s' % package not in calls
    assert not any(call.startswith('warn ') for call in calls)
    assert 'result 0' in calls


def test_install_prerequisites_falls_back_to_the_upstream_repositories(tmpdir):
    calls = _run(tmpdir, 'install_prerequisites', CP_VER='9.8', CP_VER_MAJOR='9', STUB_CP_REPO_BROKEN='true')

    assert any(call.startswith('yum -y install --disablerepo=cloud-pipeline glx-utils ') for call in calls)
    assert 'yum -y groupinstall --disablerepo=cloud-pipeline Xfce' in calls
    for package in OPTIONAL_PACKAGES:
        assert 'yum -y install --disablerepo=cloud-pipeline %s' % package in calls
    assert not any(call.startswith('fail ') for call in calls)
    assert 'result 0' in calls


def test_install_prerequisites_fails_if_a_required_package_is_missing(tmpdir):
    calls = _run(tmpdir, 'install_prerequisites', CP_VER='9.8', CP_VER_MAJOR='9', STUB_MISSING='gdm')

    assert 'fail [ERROR] Cannot install system prerequisites for DCV' in calls
    for package in OPTIONAL_PACKAGES:
        assert 'yum -y install %s' % package not in calls
    assert not any(call.startswith('python ') for call in calls)
    assert 'result 0' not in calls


@pytest.mark.parametrize('version, build', [
    ('20.04', 'nice-dcv-2021.2-11190-ubuntu2004-x86_64'),
    ('22.04', 'nice-dcv-2022.2-14521-ubuntu2204-x86_64'),
    ('24.04', 'nice-dcv-2024.0-19030-ubuntu2404-x86_64'),
])
def test_install_dcv_takes_the_build_of_the_ubuntu_version(tmpdir, version, build):
    calls = _run(tmpdir, 'install_dcv', IS_RPM_BASED='1', CP_OS='ubuntu', CP_VER=version)

    assert _downloaded_build(calls) == build
    assert any(call.startswith('apt install -y ./nice-dcv-server') for call in calls)
    assert 'info DCV successfully installed and configured' in calls
    assert 'result 0' in calls


def test_install_dcv_fails_on_an_ubuntu_version_without_a_build(tmpdir):
    sentinel = tmpdir.join('sentinel')
    sentinel.write('')

    calls = _run(tmpdir, 'install_dcv', IS_RPM_BASED='1', CP_OS='ubuntu', CP_VER='26.04')

    assert 'fail [ERROR] Unsupported Ubuntu version: 26.04' in calls
    assert not any(call.startswith('wget') or call.startswith('apt') for call in calls)
    assert 'result 0' not in calls
    assert sentinel.check(file=True)


def test_install_prerequisites_installs_the_optional_ubuntu_packages_after_the_required_ones(tmpdir):
    calls = _run(tmpdir, 'install_prerequisites', IS_RPM_BASED='1', CP_OS='ubuntu', CP_VER='24.04',
                 STUB_MISSING=DEB_OPTIONAL_PACKAGES[0])

    installs = [call for call in calls if call.startswith('apt install')]
    assert installs[0] == 'apt install -y --no-install-recommends xfce4 dbus-x11 xfce4-terminal firefox'
    assert installs[1:] == ['apt install -y --no-install-recommends %s' % package
                            for package in DEB_OPTIONAL_PACKAGES]
    assert 'warn [WARNING] Optional DCV prerequisite %s is not available, skipping it' % DEB_OPTIONAL_PACKAGES[0] \
        in calls
    assert not any(call.startswith('fail ') for call in calls)
    assert 'result 0' in calls


def test_install_prerequisites_skips_the_optional_ubuntu_packages_if_the_required_ones_fail(tmpdir):
    calls = _run(tmpdir, 'install_prerequisites', IS_RPM_BASED='1', CP_OS='ubuntu', CP_VER='24.04',
                 STUB_MISSING='firefox')

    assert 'fail [ERROR] Cannot install system prerequisites for DCV' in calls
    for package in DEB_OPTIONAL_PACKAGES:
        assert 'apt install -y --no-install-recommends %s' % package not in calls
