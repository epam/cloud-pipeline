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
# get_install_command_by_current_distr puts into the install command on Rocky Linux, RHEL, CentOS and Ubuntu.
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

    def run(packages, cp_os='rocky', cp_ver='8.7', installed=(), ignore_installed=''):
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
        output = subprocess.check_output(['bash', '-c', stubs + script], env=env).decode('utf-8').strip()
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
