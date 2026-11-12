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

# Covers the package names of the base packages install of scripts/pipeline-launch/launch.sh: which packages
# get_install_command_by_current_distr puts into the install command on Rocky Linux, RHEL, CentOS, Amazon Linux and
# Ubuntu, and the [INFO] lines it logs for the packages that it skips.
# Runs the function and check_package_installed of launch.sh with bash. check_installed is replaced by a stub, which
# reports the package manager of the OS, and rpm and dpkg by stubs first on PATH, which know the installed packages.
# Needs bash and GNU sed.

import os
import stat
import subprocess

import pytest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')

# rpm -q <package> and dpkg -s <package> succeed for the installed packages only
PACKAGE_DB_STUB = '''#!/bin/bash
case " $STUB_INSTALLED " in
    *" $2 "*) exit 0 ;;
esac
exit 1
'''


def _function_source(name):
    with open(LAUNCH_SH) as f:
        text = f.read()
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


@pytest.fixture
def install_command(tmpdir):
    stub_bin = tmpdir.mkdir('stub-bin')
    for name in ('rpm', 'dpkg'):
        stub = stub_bin.join(name)
        stub.write(PACKAGE_DB_STUB)
        os.chmod(str(stub), stat.S_IRWXU)
    script = _function_source('check_package_installed') \
        + _function_source('get_install_command_by_current_distr') \
        + 'get_install_command_by_current_distr _COMMAND "$STUB_PACKAGES"\necho "$_COMMAND"\n'

    def run(packages, cp_os='rocky', cp_ver='8.7', installed=(), ignore_installed='', log=None):
        # log: a list that gets the [INFO] lines of the function (its stderr)
        package_managers = ('apt-get', 'dpkg') if cp_os in ('ubuntu', 'debian') else ('yum', 'rpm')
        stubs = 'function check_installed { case "$1" in %s) return 0 ;; esac; return 1; }\n' \
                % '|'.join(package_managers)
        env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CP_OS': cp_os,
            'CP_VER': cp_ver,
            'CP_IGNORE_INSTALLED_PACKAGES': ignore_installed,
            'STUB_PACKAGES': packages,
            'STUB_INSTALLED': ' '.join(installed)
        }
        process = subprocess.run(['bash', '-c', stubs + script], env=env, stdout=subprocess.PIPE,
                                 stderr=subprocess.PIPE, check=True)
        output = process.stdout.decode('utf-8').strip()
        if log is not None:
            log.extend(process.stderr.decode('utf-8').splitlines())
        if not output:
            return []
        assert ' install ' in output, output
        return output.split(' install ', 1)[1].split()

    return run


def test_rocky_does_not_install_coreutils_single_next_to_coreutils(install_command):
    # They conflict: yum fails, and the run cannot install its base packages
    assert install_command('git coreutils', installed=('coreutils',)) == ['git']


def test_rocky_does_not_install_coreutils_next_to_coreutils_single(install_command):
    assert install_command('git coreutils', installed=('coreutils-single',)) == ['git']


def test_rocky_does_not_install_coreutils_next_to_coreutils_single_with_ignore_installed_packages(install_command):
    # The other packages are installed again, but coreutils cannot be installed next to coreutils-single
    assert install_command('git coreutils', installed=('git', 'coreutils-single'), ignore_installed='true') == ['git']


def test_rocky_installs_coreutils_if_neither_is_installed(install_command):
    assert install_command('git coreutils', installed=('git',)) == ['coreutils']


def test_rocky_keeps_the_coreutils_single_name(install_command):
    assert install_command('coreutils-single') == ['coreutils-single']


def test_rhel_does_not_install_coreutils_next_to_coreutils_single(install_command):
    assert install_command('git coreutils', cp_os='rhel', installed=('coreutils-single',)) == ['git']


@pytest.mark.parametrize('cp_os,cp_ver', [('amzn', '2023'), ('almalinux', '9.4'), ('centos', '9')])
def test_other_rpm_distributions_do_not_install_coreutils_next_to_coreutils_single(install_command, cp_os, cp_ver):
    assert install_command('git coreutils', cp_os=cp_os, cp_ver=cp_ver, installed=('coreutils-single',)) == ['git']


def test_skipped_coreutils_is_logged(install_command):
    log = []
    assert install_command('git coreutils', cp_ver='9.8', installed=('coreutils-single',), log=log) == ['git']
    assert log == ['[INFO] coreutils is not installed: the image has coreutils-single, which has the same commands']


def _base_packages():
    # The base packages that launch.sh installs at every run start
    with open(LAUNCH_SH) as f:
        for line in f:
            if line.startswith('_CP_INIT_DEPS_LIST='):
                return line.split('=', 1)[1].strip().strip('"')
    raise AssertionError('_CP_INIT_DEPS_LIST not found in ' + LAUNCH_SH)


@pytest.mark.parametrize('cp_os,cp_ver,installed', [
    # What the rockylinux/rockylinux:9.8, registry.access.redhat.com/ubi9/ubi and amazonlinux:2023 images have
    ('rocky', '9.8', ('curl-minimal', 'coreutils-single', 'tzdata')),
    ('rhel', '9.8', ('curl-minimal', 'coreutils-single', 'tzdata', 'acl')),
    ('amzn', '2023', ('curl-minimal', 'coreutils-single', 'tzdata'))])
def test_base_packages_install_skips_curl_and_coreutils_next_to_the_minimal_packages(install_command, cp_os, cp_ver,
                                                                                    installed):
    # They conflict: yum fails, and the run gets none of the base packages
    expected = [package for package in _base_packages().split()
                if package not in ('curl', 'coreutils') and package not in installed]
    log = []
    assert install_command(_base_packages(), cp_os=cp_os, cp_ver=cp_ver, installed=installed, log=log) == expected
    assert 'git' in expected and 'wget' in expected and 'fuse' in expected
    assert len(log) == 2 and log[0].startswith('[INFO] curl is not installed') \
        and log[1].startswith('[INFO] coreutils is not installed'), log


@pytest.mark.parametrize('cp_os,cp_ver', [('almalinux', '9.4'), ('centos', '9'), ('amzn', '2023')])
def test_other_rpm_distributions_do_not_install_curl_next_to_curl_minimal(install_command, cp_os, cp_ver):
    assert install_command('git curl', cp_os=cp_os, cp_ver=cp_ver, installed=('curl-minimal',)) == ['git']


def test_rocky_does_not_install_curl_next_to_curl_minimal_with_ignore_installed_packages(install_command):
    # The other packages are installed again, but curl cannot be installed next to curl-minimal
    assert install_command('git curl', cp_ver='9.8', installed=('git', 'curl-minimal'), ignore_installed='true') \
        == ['git']


def test_rocky_installs_curl_if_neither_is_installed(install_command):
    log = []
    assert install_command('git curl', cp_ver='9.8', installed=('git',), log=log) == ['curl']
    assert log == []


def test_skipped_curl_is_logged(install_command):
    # E.g. curl in CP_CAP_EXTRA_PKG: the curl of curl-minimal has no sftp, scp or ldap
    log = []
    assert install_command('curl', cp_ver='9.8', installed=('curl-minimal',), log=log) == []
    assert log == ['[INFO] curl is not installed: the image has curl-minimal, '
                   'its curl supports file, ftp(s) and http(s) only']


def test_rocky_does_not_install_curl_again(install_command):
    assert install_command('git curl', cp_ver='8.9', installed=('git', 'curl')) == []


def test_rocky_keeps_the_curl_minimal_name(install_command):
    assert install_command('curl-minimal', cp_ver='9.8') == ['curl-minimal']


def test_ubuntu_installs_curl(install_command):
    assert install_command('git curl', cp_os='ubuntu', cp_ver='22.04') == ['git', 'curl']


def test_rocky_and_rhel_name_procps_procps_ng(install_command):
    assert install_command('procps git') == ['procps-ng', 'git']
    assert install_command('git procps', cp_os='rhel', cp_ver='9.4') == ['git', 'procps-ng']


def test_rocky_and_rhel_keep_the_procps_ng_name(install_command):
    assert install_command('procps-ng') == ['procps-ng']
    assert install_command('procps-ng', cp_os='rhel') == ['procps-ng']


def test_procps_installed_as_procps_ng_is_not_installed_again(install_command):
    assert install_command('procps', installed=('procps-ng',)) == []


def test_centos_keeps_the_package_names(install_command):
    assert install_command('procps coreutils', cp_os='centos', cp_ver='7') == ['procps', 'coreutils']


def test_ubuntu_keeps_the_package_names(install_command):
    assert install_command('procps coreutils', cp_os='ubuntu', cp_ver='22.04') == ['procps', 'coreutils']


@pytest.mark.parametrize('cp_os,cp_ver', [('rocky', '8.7'), ('rocky', '9.8'), ('centos', '8')])
def test_python_package_names_are_kept(install_command, cp_os, cp_ver):
    # E.g. CP_CAP_EXTRA_PKG: "python" was renamed to "python2" for the Python 2 of the run, and "python3-devel"
    # to "python23-devel" with it
    assert install_command('python3-devel python3.12 python-unversioned-command', cp_os=cp_os, cp_ver=cp_ver) \
        == ['python3-devel', 'python3.12', 'python-unversioned-command']
