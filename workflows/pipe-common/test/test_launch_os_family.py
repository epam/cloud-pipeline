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

# Covers what scripts/pipeline-launch/launch.sh does for an OS family: the family of the /etc/os-release ID, the
# Cloud Pipeline yum repository setup, the systemd capability check and the CP_CAP_EXTRA_PKG_RHEL install, on RHEL
# (also the UBI images, ID "rhel") and the other OS. Runs the functions and the blocks of launch.sh with bash, with
# the absolute /etc and /var paths replaced by temporary ones. yum, rpm and yum-config-manager are replaced by stubs
# first on PATH, which record their calls, and curl by one that records its calls and runs the real curl. The Cloud
# Pipeline repository is a folder of the test, as a file:// URL. Needs bash, GNU sed and curl.

import os
import re
import shutil
import stat
import subprocess

import pytest

LAUNCH_SH = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))),
                         'scripts', 'pipeline-launch', 'launch.sh')

RECORDING_STUB = '''#!/bin/bash
printf '%s\\n' "$(basename "$0") $*" >> "$CALLS"
'''

# rpm -q <package> succeeds for the installed packages only
RPM_STUB = RECORDING_STUB + '''case " $STUB_INSTALLED " in
    *" $2 "*) exit 0 ;;
esac
exit 1
'''

CLOUD_PIPELINE_REPO = '[cloud-pipeline]\nname=Cloud Pipeline\nbaseurl=https://dist.example/tools/repos/rocky/8.7\n'
CURL_STUB = RECORDING_STUB + 'exec "$REAL_CURL" "$@"\n'
YUM_CONFIG_SKIP_IF_UNAVAILABLE_CALL = 'yum-config-manager --save --setopt=*.skip_if_unavailable=true'

# The repository file of the UBI 8 image, shortened
UBI_REPO = '[ubi-8-baseos-rpms]\nname = Red Hat Universal Base Image 8 (RPMs) - BaseOS\n' \
           'baseurl = https://cdn-ubi.redhat.com/content/public/ubi/dist/ubi8/8/$basearch/baseos/os\nenabled = 1\n'

OS_RELEASE = {
    'rhel': 'NAME="Red Hat Enterprise Linux"\nVERSION="8.10 (Ootpa)"\nID="rhel"\nID_LIKE="fedora"\n'
            'VERSION_ID="8.10"\nPLATFORM_ID="platform:el8"\n',
    'rhel9': 'NAME="Red Hat Enterprise Linux"\nVERSION="9.8 (Plow)"\nID="rhel"\nID_LIKE="fedora"\n'
             'VERSION_ID="9.8"\nPLATFORM_ID="platform:el9"\n',
    'rocky': 'NAME="Rocky Linux"\nID="rocky"\nID_LIKE="rhel centos fedora"\nVERSION_ID="9.8"\n',
    'centos': 'NAME="CentOS Linux"\nID="centos"\nID_LIKE="rhel fedora"\nVERSION_ID="7"\n',
    'ubuntu': 'NAME="Ubuntu"\nID=ubuntu\nID_LIKE=debian\nVERSION_ID="22.04"\nVERSION_CODENAME=jammy\n',
    'alpine': 'NAME="Alpine Linux"\nID=alpine\nVERSION_ID=3.20.3\n'
}

# An absolute /etc or /var path in the shell code
SYSTEM_PATH = re.compile(r'(?<![\w./-])/(etc|var)/')

PKG_MANAGER_FUNCTIONS = ('check_pkg_manager_timeout_supported', 'run_pkg_manager_with_timeout',
                         'run_pkg_manager_update', 'run_pkg_manager_install')


def _launch():
    with open(LAUNCH_SH) as f:
        return f.read()


def _function_source(name):
    text = _launch()
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _block_source(start_text, end_text):
    text = _launch()
    start = text.index(start_text)
    end = text.index(end_text, start) + len(end_text)
    return text[start:end]


def _stub(directory, name, body):
    stub = directory.join(name)
    stub.write(body)
    os.chmod(str(stub), stat.S_IRWXU)


def _check_installed_stub(cp_os, extra=()):
    # check_installed finds the package manager of the OS only, whatever the host has
    commands = (('apt-get', 'dpkg') if cp_os in ('ubuntu', 'debian') else ('yum', 'rpm')) + tuple(extra)
    return 'function check_installed { case "$1" in %s) return 0 ;; esac; return 1; }\n' % '|'.join(commands)


@pytest.fixture
def shell(tmpdir):
    stub_bin = tmpdir.mkdir('stub-bin')
    for name in ('yum', 'yum-config-manager'):
        _stub(stub_bin, name, RECORDING_STUB)
    _stub(stub_bin, 'rpm', RPM_STUB)
    _stub(stub_bin, 'curl', CURL_STUB)
    real_curl = shutil.which('curl')
    root = tmpdir.mkdir('root')
    repos = tmpdir.mkdir('repos')
    for directory in ('etc/yum.repos.d', 'var/log'):
        root.join(directory).ensure(dir=True)
    calls = tmpdir.join('calls')

    def run(code, **env):
        run_env = {
            'PATH': str(stub_bin) + os.pathsep + os.environ['PATH'],
            'CALLS': str(calls),
            'REAL_CURL': real_curl or 'curl-is-not-installed'
        }
        run_env.update(env)
        code = SYSTEM_PATH.sub(lambda path: str(root) + path.group(0), code)
        proc = subprocess.Popen(['bash', '-c', code], env=run_env, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        stdout, stderr = proc.communicate()
        recorded = calls.read().splitlines() if calls.check() else []
        return proc.returncode, stdout.decode('utf-8'), stderr.decode('utf-8'), recorded

    run.root = root
    run.repos = repos
    run.real_curl = real_curl
    return run


def _define_distro(shell, os_release):
    shell.root.join('etc', 'os-release').write(OS_RELEASE[os_release])
    code = _function_source('define_distro_name_and_version') \
        + 'define_distro_name_and_version\necho "$CP_OS $CP_OS_FAMILY $CP_VER $CP_VER_MAJOR"\n'
    exit_code, stdout, stderr, calls = shell(code)
    assert exit_code == 0, stderr
    return stdout.split()


@pytest.mark.parametrize('os_release,expected', [
    ('rhel', ['rhel', 'rhel', '8.10', '8']),
    ('rhel9', ['rhel', 'rhel', '9.8', '9']),
    ('rocky', ['rocky', 'rhel', '9.8', '9']),
    ('centos', ['centos', 'rhel', '7', '7']),
    ('ubuntu', ['ubuntu', 'debian', '22.04', '22']),
    ('alpine', ['alpine', 'linux', '3.20.3', '3'])
])
def test_os_family_of_the_os_release_id(shell, os_release, expected):
    # RHEL and its UBI images have ID "rhel", which was the "linux" family
    assert _define_distro(shell, os_release) == expected


def _configure_package_manager(shell, cp_os, cp_ver, **env):
    if not shell.real_curl:
        pytest.skip('no curl')
    code = ''.join(_function_source(name) for name in PKG_MANAGER_FUNCTIONS) \
        + _check_installed_stub(cp_os, extra=('yum-config-manager',)) \
        + 'function configure_package_manager_pip { echo "PIP CONFIGURED"; }\n' \
        + _function_source('configure_package_manager') \
        + 'configure_package_manager\n'
    return shell(code, CP_OS=cp_os, CP_VER=cp_ver, CP_REPO_ENABLED='true',
                 CP_REPO_BASE_URL_DEFAULT='file://' + str(shell.repos), **env)


def _repo_url(shell, cp_os, cp_ver):
    return 'file://%s/%s/%s/cloud-pipeline.repo' % (shell.repos, cp_os, cp_ver)


def _publish_repo(shell, cp_os, cp_ver):
    shell.repos.join(cp_os, cp_ver, 'cloud-pipeline.repo').write(CLOUD_PIPELINE_REPO, ensure=True)


def _image_repos(shell):
    # The repositories of a UBI based CUDA image
    shell.root.join('etc', 'yum.repos.d', 'ubi.repo').write(UBI_REPO)
    shell.root.join('etc', 'yum.repos.d', 'cuda.repo').write('[cuda]\nbaseurl=https://developer.download.nvidia.com\n')


@pytest.mark.parametrize('cp_ver', ['8.10', '9.8'])
def test_rhel_keeps_the_repositories_of_the_image_without_a_cloud_pipeline_repo_of_its_version(shell, cp_ver):
    # The public repository has no rhel/<version> folder: the run checks it once, and says that it is not configured.
    # The Rocky Linux folder of the same version is not used
    _image_repos(shell)
    _publish_repo(shell, 'rocky', cp_ver)

    exit_code, stdout, stderr, calls = _configure_package_manager(shell, 'rhel', cp_ver)

    assert exit_code == 0, stderr
    assert '[INFO] The Cloud Pipeline repository has no packages for rhel %s (%s), it is not configured. ' \
           'The packages are installed from the repositories of the image' \
           % (cp_ver, _repo_url(shell, 'rhel', cp_ver)[:-len('/cloud-pipeline.repo')]) in stdout
    assert 'Failed to configure' not in stdout
    assert calls == ['curl -skf -o /dev/null --max-time 30 ' + _repo_url(shell, 'rhel', cp_ver)]
    assert not shell.root.join('etc', 'yum.repos.d', 'cloud-pipeline.repo').check()
    assert shell.root.join('etc', 'yum.repos.d', 'ubi.repo').read() == UBI_REPO
    assert 'PIP CONFIGURED' in stdout


def test_rhel_removes_the_nvidia_repo_without_a_cloud_pipeline_repo(shell):
    # As CentOS and Rocky Linux do: the nvidia repositories make the run initialization fail
    _image_repos(shell)

    _configure_package_manager(shell, 'rhel', '9.8')

    assert not shell.root.join('etc', 'yum.repos.d', 'cuda.repo').check()


def test_rhel_does_not_skip_the_unavailable_repos_of_the_image_without_a_cloud_pipeline_repo(shell):
    # skip_if_unavailable lets the other repositories fail where the Cloud Pipeline repository has the packages
    _image_repos(shell)

    exit_code, stdout, stderr, calls = _configure_package_manager(shell, 'rhel', '8.10')

    assert YUM_CONFIG_SKIP_IF_UNAVAILABLE_CALL not in calls


def test_rhel_configures_the_cloud_pipeline_repo_of_a_deployment_that_has_its_version(shell):
    _image_repos(shell)
    _publish_repo(shell, 'rhel', '8.10')

    exit_code, stdout, stderr, calls = _configure_package_manager(shell, 'rhel', '8.10')

    assert exit_code == 0, stderr
    assert calls == ['curl -skf -o /dev/null --max-time 30 ' + _repo_url(shell, 'rhel', '8.10'),
                     'curl -sk ' + _repo_url(shell, 'rhel', '8.10'),
                     'yum --disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y -q',
                     YUM_CONFIG_SKIP_IF_UNAVAILABLE_CALL]
    assert shell.root.join('etc', 'yum.repos.d', 'cloud-pipeline.repo').read() == CLOUD_PIPELINE_REPO
    assert not shell.root.join('etc', 'yum.repos.d', 'cuda.repo').check()
    assert 'is not configured' not in stdout
    assert 'Failed to configure' not in stdout


@pytest.mark.parametrize('timeout_sec,expected', [('5', '5'), ('', '30'), ('0', '30'), ('ten', '30')])
def test_the_rhel_repo_check_is_limited_by_cp_repo_access_timeout_sec(shell, timeout_sec, expected):
    exit_code, stdout, stderr, calls = _configure_package_manager(shell, 'rhel', '9.8',
                                                                  CP_REPO_ACCESS_TIMEOUT_SEC=timeout_sec)

    assert exit_code == 0, stderr
    assert calls == ['curl -skf -o /dev/null --max-time %s %s' % (expected, _repo_url(shell, 'rhel', '9.8'))]


def test_rocky_configures_the_cloud_pipeline_repo_without_a_check(shell):
    _publish_repo(shell, 'rocky', '8.7')

    exit_code, stdout, stderr, calls = _configure_package_manager(shell, 'rocky', '8.7')

    assert exit_code == 0, stderr
    assert calls == ['curl -sk ' + _repo_url(shell, 'rocky', '8.7'),
                     'yum --disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y -q',
                     YUM_CONFIG_SKIP_IF_UNAVAILABLE_CALL]
    assert shell.root.join('etc', 'yum.repos.d', 'cloud-pipeline.repo').read() == CLOUD_PIPELINE_REPO
    assert 'is not configured' not in stdout


def _systemd_condition():
    # The condition of the "Setup systemd" block, up to "; then"
    block = _block_source('if ( check_cp_cap "CP_CAP_SYSTEMD_CONTAINER"', '; then\n')
    return block[len('if '):-len('; then\n')]


def _systemd_requested(shell, cp_os, systemctl=True, **env):
    code = _function_source('check_cp_cap') \
        + _check_installed_stub(cp_os, extra=('systemctl',) if systemctl else ()) \
        + 'if %s; then echo REQUESTED; else echo SKIPPED; fi\n' % _systemd_condition()
    exit_code, stdout, stderr, calls = shell(code, CP_OS=cp_os, **env)
    assert exit_code == 0, stderr
    return stdout.strip() == 'REQUESTED'


@pytest.mark.parametrize('cp_os', ['rhel', 'rocky', 'centos'])
def test_systemd_is_set_up_on_rhel_rocky_and_centos(shell, cp_os):
    assert _systemd_requested(shell, cp_os, CP_CAP_SYSTEMD_CONTAINER='true')
    assert _systemd_requested(shell, cp_os, CP_CAP_KUBE='true')


@pytest.mark.parametrize('cp_os', ['ubuntu', 'amzn'])
def test_systemd_is_not_set_up_on_the_other_os(shell, cp_os):
    assert not _systemd_requested(shell, cp_os, CP_CAP_SYSTEMD_CONTAINER='true')


def test_systemd_is_not_set_up_on_rhel_unless_requested_or_without_systemctl(shell):
    assert not _systemd_requested(shell, 'rhel')
    assert not _systemd_requested(shell, 'rhel', systemctl=False, CP_CAP_SYSTEMD_CONTAINER='true')


def _install_extra_packages(shell, cp_os, cp_ver, installed=(), **env):
    block = _block_source('if [ "$CP_PIPE_COMMON_ENABLED" != "false" ]; then\n      EXTRA_PKG_INSTALL_COMMAND=',
                          '\nfi\n')
    code = _check_installed_stub(cp_os) \
        + 'function run_pkg_manager_install { echo "INSTALL: $*"; }\n' \
        + _function_source('check_package_installed') \
        + _function_source('get_install_command_by_current_distr') \
        + block
    exit_code, stdout, stderr, calls = shell(code, CP_OS=cp_os, CP_VER=cp_ver, STUB_INSTALLED=' '.join(installed),
                                             **env)
    assert exit_code == 0, stderr
    return stdout.splitlines()


def test_rhel_installs_the_rhel_extra_packages_with_their_rhel_names(shell):
    # UBI images have coreutils-single, and name procps procps-ng
    output = _install_extra_packages(shell, 'rhel', '9.8', installed=('coreutils-single',),
                                     CP_CAP_EXTRA_PKG_RHEL='procps coreutils wget')

    assert 'Installing extra packages for rhel: procps coreutils wget' in output
    assert 'INSTALL: yum -y -q install procps-ng wget' in output


def test_rhel_installs_the_common_extra_packages_and_not_the_debian_ones(shell):
    output = _install_extra_packages(shell, 'rhel', '8.10', CP_CAP_EXTRA_PKG='git', CP_CAP_EXTRA_PKG_DEB='tree')

    assert 'Installing COMMON extra packages: git' in output
    assert 'INSTALL: yum -y -q install git' in output
    assert not [line for line in output if 'tree' in line]


@pytest.mark.parametrize('cp_os,cp_ver', [('rocky', '9.8'), ('centos', '7')])
def test_rocky_and_centos_install_the_rhel_extra_packages(shell, cp_os, cp_ver):
    output = _install_extra_packages(shell, cp_os, cp_ver, CP_CAP_EXTRA_PKG_RHEL='wget')

    assert 'Installing extra packages for %s: wget' % cp_os in output
    assert 'INSTALL: yum -y -q install wget' in output


def test_ubuntu_does_not_install_the_rhel_extra_packages(shell):
    output = _install_extra_packages(shell, 'ubuntu', '22.04', CP_CAP_EXTRA_PKG_RHEL='wget')

    assert not [line for line in output if 'wget' in line]
