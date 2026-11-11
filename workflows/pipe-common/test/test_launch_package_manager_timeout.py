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

# The time limits of the apt-get, yum and apk calls of scripts/pipeline-launch/launch.sh: the wrappers, the install and
# upgrade commands, configure_package_manager (the Debian 8 refresh, and the Cloud Pipeline repo setup on Ubuntu 22.04
# and Rocky Linux 8.7) with a package manager call that hangs, and a check that every downloading call has a limit.
# Runs the shell code with bash and stub package managers that hang, with the absolute paths replaced with temporary
# ones. Needs bash and GNU coreutils. A test that hangs takes 2-4 s. Runs on Python 2.7 and 3.

import collections
import os
import re
import shutil
import subprocess
import tempfile
import threading
import unittest

try:
    from shutil import which
except ImportError:  # Python 2
    from distutils.spawn import find_executable as which

REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
LAUNCH_SH = os.path.join(REPO_ROOT, 'scripts', 'pipeline-launch', 'launch.sh')
BASH = which('bash') or '/bin/bash'
TIMEOUT = which('timeout') or '/usr/bin/timeout'
TIMEOUT_FUNCTIONS = ('check_installed', 'check_pkg_manager_timeout_supported', 'run_pkg_manager_with_timeout',
                     'run_pkg_manager_update', 'run_pkg_manager_install')

# Logs its arguments and DEBIAN_FRONTEND. Hangs as many times as the STUB_HANGS file says, then exits with STUB_RC
APT_GET_STUB = """#!/bin/bash
echo "$* | DEBIAN_FRONTEND=$DEBIAN_FRONTEND" >> "$STUB_LOG"
hangs=$(cat "$STUB_HANGS" 2>/dev/null || echo 0)
if [ "$hangs" -gt 0 ]; then
    echo $((hangs - 1)) > "$STUB_HANGS"
    sleep 300
fi
exit ${STUB_RC:-0}
"""

# Logs its arguments and runs the real timeout
TIMEOUT_LOGGING_STUB = '#!/bin/bash\necho "timeout $*" >> "$STUB_LOG"\nexec "%s" "$@"\n' % TIMEOUT

# An old busybox timeout: it takes the limit as "-t SECS" only, so "timeout SECS COMMAND" fails
TIMEOUT_OLD_BUSYBOX_STUB = '#!/bin/bash\necho "timeout: can\'t execute \'$1\': No such file or directory" >&2\nexit 127\n'

# A busybox 1.30 timeout: no -k, and on a timeout it exits with the status of the stopped command (TERM: 143)
TIMEOUT_BUSYBOX_STUB = '#!/bin/bash\n[ "$1" == "-k" ] && exit 1\n"%s" "$@"\nrc=$?\n[ $rc -eq 124 ] && exit 143\n' \
                       'exit $rc\n' % TIMEOUT

APT_UPDATE_CALL = 'update -qq -y --allow-insecure-repositories | DEBIAN_FRONTEND='

# A yum or an apk: logs its arguments, and hangs as many times as the STUB_HANGS file says, on the calls with
# STUB_HANG_ON in them
PKG_MANAGER_STUB = """#!/bin/bash
echo "$*" >> "$STUB_LOG"
hangs=$(cat "$STUB_HANGS" 2>/dev/null || echo 0)
if [ "$hangs" -gt 0 ] && [[ "$*" == *"$STUB_HANG_ON"* ]]; then
    echo $((hangs - 1)) > "$STUB_HANGS"
    sleep 300
fi
"""
YUM_PRIORITIES_CALL = '--disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y -q'

DIST_URL = 'https://dist.example/'
CP_APT_SOURCE = 'deb https://dist.example/tools/repos/ubuntu/22.04 stable main\n'
UBUNTU_SOURCES = 'deb http://archive.ubuntu.com/ubuntu/ jammy main restricted\n'
CLOUD_PIPELINE_REPO = '[cloud-pipeline]\nname=Cloud Pipeline\nbaseurl=https://dist.example/tools/repos/rocky/8.7\n' \
                      'enabled=1\ngpgcheck=0\n'

# Prints the Cloud Pipeline repo file. If CURL_FAILS_AFTER is set, fails from the second call on, as with no network
CURL_REPO_STUB = '#!/bin/bash\nif [ -n "$CURL_FAILS_AFTER" ]; then\n    [ -f "$CURL_FAILS_AFTER" ] && exit 7\n' \
                 '    touch "$CURL_FAILS_AFTER"\nfi\ncat <<"EOR"\n%sEOR\n' % CLOUD_PIPELINE_REPO

# A command that check_installed does not find, whether the host has it or not
NOT_INSTALLED = 'function check_installed { [ "$1" != "%s" ] && command -v "$1" >/dev/null 2>&1; }\n'

# An absolute /etc or /var path in the shell code
SYSTEM_PATH = re.compile(r'(?<![\w./-])/(etc|var)/')


def _read(path):
    with open(path) as f:
        return f.read()


def _function_source(text, name):
    start = text.index('function %s {' % name)
    end = text.index('\n}\n', start) + 3
    return text[start:end]


def _stub(directory, name, body):
    path = os.path.join(directory, name)
    with open(path, 'w') as f:
        f.write(body)
    os.chmod(path, 0o755)


CompletedProcess = collections.namedtuple('CompletedProcess', 'returncode stdout stderr')


def _run(args, env, timeout_sec):
    # subprocess.run is Python 3 only. The process is killed at the limit, so a test that hangs fails
    proc = subprocess.Popen(args, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, universal_newlines=True)
    timer = threading.Timer(timeout_sec, proc.kill)
    timer.start()
    try:
        stdout, stderr = proc.communicate()
    finally:
        timer.cancel()
    return CompletedProcess(proc.returncode, stdout, stderr)


class PackageManagerShellTestCase(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.stub_bin = os.path.join(self.tmp, 'stub-bin')
        os.makedirs(self.stub_bin)
        self.log = os.path.join(self.tmp, 'calls.log')
        self.hangs = os.path.join(self.tmp, 'hangs')
        _stub(self.stub_bin, 'apt-get', APT_GET_STUB)
        self.launch = _read(LAUNCH_SH)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def run_bash(self, code, hangs=0, rc=0, **env):
        with open(self.hangs, 'w') as f:
            f.write(str(hangs))
        script = ''.join(_function_source(self.launch, name) for name in TIMEOUT_FUNCTIONS) + code + '\n'
        run_env = {'PATH': self.stub_bin + os.pathsep + os.environ.get('PATH', ''),
                   'STUB_LOG': self.log, 'STUB_HANGS': self.hangs, 'STUB_RC': str(rc)}
        run_env.update(env)
        return _run([BASH, '-c', script], env=run_env, timeout_sec=120)

    def calls(self):
        if not os.path.exists(self.log):
            return []
        return _read(self.log).splitlines()


class RunPkgManagerTest(PackageManagerShellTestCase):

    def test_a_hanging_refresh_is_stopped_and_tried_once_more(self):
        proc = self.run_bash('run_pkg_manager_update apt-get update -qq', hangs=99,
                             CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(124, proc.returncode)
        self.assertEqual(['update -qq | DEBIAN_FRONTEND='] * 2, self.calls())
        self.assertEqual(2, proc.stderr.count("[WARN] 'apt-get update -qq' did not finish in 2 seconds and was stopped"))
        self.assertEqual(1, proc.stderr.count('[WARN] Refreshing the package lists once more: apt-get update -qq'))

    def test_a_refresh_that_hangs_once_passes_on_its_second_try(self):
        # The apt stall depends on when the downloads fail
        proc = self.run_bash('run_pkg_manager_update apt-get update -qq', hangs=1,
                             CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['update -qq | DEBIAN_FRONTEND='] * 2, self.calls())

    def test_a_failing_refresh_keeps_its_exit_code_and_is_not_tried_again(self):
        proc = self.run_bash('run_pkg_manager_update apt-get update -qq', rc=100,
                             CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(100, proc.returncode)
        self.assertEqual(1, len(self.calls()))
        self.assertNotIn('did not finish', proc.stderr)

    def test_a_hanging_install_is_stopped_and_not_tried_again(self):
        proc = self.run_bash('run_pkg_manager_install apt-get install -y git', hangs=99,
                             CP_PKG_MANAGER_INSTALL_TIMEOUT_SEC='2')
        self.assertEqual(124, proc.returncode)
        self.assertEqual(['install -y git | DEBIAN_FRONTEND='], self.calls())
        self.assertIn("[WARN] 'apt-get install -y git' did not finish in 2 seconds and was stopped", proc.stderr)

    def test_the_default_limits(self):
        # 600 s for a refresh, 3600 s for an install, and KILL 60 s after TERM
        _stub(self.stub_bin, 'timeout', TIMEOUT_LOGGING_STUB)
        proc = self.run_bash('run_pkg_manager_update apt-get update -qq && run_pkg_manager_install apt-get install -y git')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['timeout -k 1 10 true',
                          'timeout -k 60 600 apt-get update -qq', 'update -qq | DEBIAN_FRONTEND=',
                          'timeout -k 60 3600 apt-get install -y git', 'install -y git | DEBIAN_FRONTEND='],
                         self.calls())

    def test_limit_0_means_no_limit(self):
        _stub(self.stub_bin, 'timeout', TIMEOUT_LOGGING_STUB)
        proc = self.run_bash('run_pkg_manager_update apt-get update -qq && run_pkg_manager_install apt-get install -y git',
                             CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='0', CP_PKG_MANAGER_INSTALL_TIMEOUT_SEC='0')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['update -qq | DEBIAN_FRONTEND=', 'install -y git | DEBIAN_FRONTEND='], self.calls())

    def test_a_limit_that_is_not_a_number_means_no_limit_with_a_warning(self):
        _stub(self.stub_bin, 'timeout', TIMEOUT_LOGGING_STUB)
        proc = self.run_bash('run_pkg_manager_install apt-get install -y git; echo "$CP_PKG_MANAGER_TIMED_OUT"',
                             CP_PKG_MANAGER_INSTALL_TIMEOUT_SEC='10m')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['install -y git | DEBIAN_FRONTEND='], self.calls())
        self.assertEqual('false', proc.stdout.strip())
        self.assertIn("[WARN] The time limit '10m' is not a number of seconds, 'apt-get install -y git' runs with no "
                      "limit", proc.stderr)

    def test_without_a_usable_timeout_the_calls_run_with_no_limit(self):
        _stub(self.stub_bin, 'timeout', TIMEOUT_OLD_BUSYBOX_STUB)
        proc = self.run_bash('run_pkg_manager_update apt-get update -qq && run_pkg_manager_install apt-get install -y git')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['update -qq | DEBIAN_FRONTEND=', 'install -y git | DEBIAN_FRONTEND='], self.calls())
        self.assertEqual(1, proc.stderr.count('[WARN] No usable timeout command'))

    def test_a_timeout_that_exits_with_the_status_of_the_stopped_command(self):
        _stub(self.stub_bin, 'timeout', TIMEOUT_BUSYBOX_STUB)
        proc = self.run_bash('run_pkg_manager_update apt-get update -qq', hangs=99,
                             CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(143, proc.returncode)
        self.assertEqual(2, len(self.calls()))
        self.assertEqual(2, proc.stderr.count('did not finish in 2 seconds'))


class InstallCommandTest(PackageManagerShellTestCase):

    def setUp(self):
        super(InstallCommandTest, self).setUp()
        self.lists = os.path.join(self.tmp, 'lists') + '/'
        os.makedirs(self.lists)
        open(os.path.join(self.lists, 'old_Packages'), 'w').close()
        # No package is installed
        _stub(self.stub_bin, 'dpkg', '#!/bin/bash\nexit 1\n')

    def _install(self, manager, evaluate=False, **kwargs):
        # The package manager of the image is the only one check_installed finds
        stubs = 'function check_installed { [ "$1" == "%s" ] || [ "$1" == dpkg ] || [ "$1" == timeout ]; }\n' % manager
        function = _function_source(self.launch, 'get_install_command_by_current_distr') \
            .replace('/var/lib/apt/lists/', self.lists)
        # The command removes the package lists: never the host's
        self.assertNotIn('/var/lib/apt/lists', function)
        code = stubs + _function_source(self.launch, 'check_package_installed') + function \
            + 'get_install_command_by_current_distr _CMD "git wget"\necho "$_CMD"\n' \
            + ('eval "$_CMD"\n' if evaluate else '')
        return self.run_bash(code, **kwargs)

    def test_apt_get(self):
        proc = self._install('apt-get')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual('rm -rf %s; run_pkg_manager_update apt-get update -y -qq --allow-insecure-repositories; '
                         'DEBIAN_FRONTEND=noninteractive run_pkg_manager_install apt-get -y -qq --allow-unauthenticated '
                         '-o Dpkg::Options::="--force-confold" install  git wget' % self.lists, proc.stdout.strip())

    def test_apk(self):
        proc = self._install('apk')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual('run_pkg_manager_update apk update -q 1>/dev/null; run_pkg_manager_install apk -q add  git wget',
                         proc.stdout.strip())

    def test_apt_get_installs_after_a_refresh_that_hung(self):
        proc = self._install('apt-get', evaluate=True, hangs=1, CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['update -y -qq --allow-insecure-repositories | DEBIAN_FRONTEND='] * 2
                         + ['-y -qq --allow-unauthenticated -o Dpkg::Options::=--force-confold install git wget'
                            ' | DEBIAN_FRONTEND=noninteractive'],
                         self.calls())
        self.assertFalse(os.path.exists(self.lists))


class UpgradeInstalledPackagesTest(PackageManagerShellTestCase):

    def _upgrade(self, manager, **kwargs):
        # The package manager of the image is the only one check_installed finds
        stubs = 'function check_installed { [ "$1" == "%s" ] || [ "$1" == timeout ]; }\n' % manager
        function = _function_source(self.launch, 'upgrade_installed_packages') \
            .replace('/var/lib/apt/lists/', os.path.join(self.tmp, 'lists') + '/')
        # The upgrade removes the package lists: never the host's
        self.assertNotIn('/var/lib/apt/lists', function)
        return self.run_bash(stubs + function + 'upgrade_installed_packages', **kwargs)

    def test_apt_get_upgrades_after_a_refresh_that_hung(self):
        proc = self._upgrade('apt-get', hangs=1, CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['update -y -qq --allow-insecure-repositories | DEBIAN_FRONTEND='] * 2
                         + ['-y -qq --allow-unauthenticated -o Dpkg::Options::=--force-confdef '
                            '-o Dpkg::Options::=--force-confold upgrade | DEBIAN_FRONTEND='],
                         self.calls())

    def test_a_yum_update_that_hangs_is_stopped_and_not_tried_again(self):
        _stub(self.stub_bin, 'yum', PKG_MANAGER_STUB)
        proc = self._upgrade('yum', hangs=1, CP_PKG_MANAGER_INSTALL_TIMEOUT_SEC='2')
        self.assertEqual(124, proc.returncode)
        self.assertEqual(['update -q -y'], self.calls())
        self.assertIn("[WARN] 'yum update -q -y' did not finish in 2 seconds and was stopped", proc.stderr)

    def test_apk_upgrades_after_a_refresh_that_hung(self):
        _stub(self.stub_bin, 'apk', PKG_MANAGER_STUB)
        proc = self._upgrade('apk', hangs=1, CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['update -q', 'update -q', 'upgrade -q'], self.calls())


class ConfigurePackageManagerTestCase(PackageManagerShellTestCase):
    # Runs configure_package_manager with its /etc and /var paths moved into the temporary directory

    def setUp(self):
        super(ConfigurePackageManagerTestCase, self).setUp()
        self.root = os.path.join(self.tmp, 'root')
        for directory in ('etc/apt/apt.conf.d', 'etc/apt/sources.list.d', 'etc/yum.repos.d', 'var/log'):
            os.makedirs(os.path.join(self.root, directory))

    def path(self, path):
        return self.root + path

    def configure(self, cp_os, cp_ver, code='', **kwargs):
        function = SYSTEM_PATH.sub(lambda path: self.root + path.group(0),
                                   _function_source(self.launch, 'configure_package_manager'))
        self.assertIsNone(SYSTEM_PATH.search(function))
        code += 'function configure_package_manager_pip { :; }\n'
        env = {'CP_OS': cp_os, 'CP_VER': cp_ver, 'CP_REPO_ENABLED': 'true', 'GLOBAL_DISTRIBUTION_URL': DIST_URL}
        env.update(kwargs.pop('env', {}))
        return self.run_bash(code + function + 'configure_package_manager', **dict(env, **kwargs))


class ConfigurePackageManagerUbuntuTest(ConfigurePackageManagerTestCase):
    # An attempt of the Cloud Pipeline repo setup refreshes the package lists, installs curl, apt-transport-https and
    # gnupg, adds the repo and refreshes the package lists again

    def setUp(self):
        super(ConfigurePackageManagerUbuntuTest, self).setUp()
        with open(self.path('/etc/apt/sources.list'), 'w') as f:
            f.write(UBUNTU_SOURCES)
        _stub(self.stub_bin, 'apt-key', '#!/bin/bash\ncat > /dev/null\n')
        _stub(self.stub_bin, 'curl', '#!/bin/bash\necho KEY\n')

    def test_the_cloud_pipeline_repo_is_set_up_when_the_first_refresh_hangs_once(self):
        proc = self.configure('ubuntu', '22.04', hangs=1, CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual([APT_UPDATE_CALL, APT_UPDATE_CALL,
                          'install curl apt-transport-https gnupg -y -qq | DEBIAN_FRONTEND=',
                          APT_UPDATE_CALL], self.calls())
        self.assertNotIn('Failed to configure', proc.stdout)
        self.assertEqual(CP_APT_SOURCE + UBUNTU_SOURCES, _read(self.path('/etc/apt/sources.list')))

    def test_an_attempt_fails_when_the_refresh_hangs_twice_and_the_next_one_sets_the_repo_up(self):
        proc = self.configure('ubuntu', '22.04', hangs=2, CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual([APT_UPDATE_CALL] * 3
                         + ['install curl apt-transport-https gnupg -y -qq | DEBIAN_FRONTEND=', APT_UPDATE_CALL],
                         self.calls())
        self.assertEqual(1, proc.stdout.count('Failed to configure'))
        self.assertIn('[ERROR] (attempt: 1) Failed to configure', proc.stdout)
        self.assertEqual(CP_APT_SOURCE + UBUNTU_SOURCES, _read(self.path('/etc/apt/sources.list')))


class ConfigurePackageManagerDebian8Test(ConfigurePackageManagerTestCase):

    def test_the_jessie_refresh_is_tried_again_when_it_hangs(self):
        with open(self.path('/etc/apt/sources.list'), 'w') as f:
            f.write('deb http://deb.debian.org/debian jessie-updates main\n')
        proc = self.configure('debian', '8', hangs=1, CP_REPO_ENABLED='false', CP_PKG_MANAGER_UPDATE_TIMEOUT_SEC='2')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(['update -y --allow-insecure-repositories | DEBIAN_FRONTEND='] * 2, self.calls())
        self.assertEqual('', _read(self.path('/etc/apt/sources.list')))
        self.assertIn('jessie main', _read(self.path('/etc/apt/sources.list.d/jessie.list')))


class ConfigurePackageManagerYumTest(ConfigurePackageManagerTestCase):
    # yum writes to /var/log/yum.cp.log, so the run log gets its timeouts as well

    def setUp(self):
        super(ConfigurePackageManagerYumTest, self).setUp()
        _stub(self.stub_bin, 'yum', PKG_MANAGER_STUB)
        _stub(self.stub_bin, 'yum-config-manager', '#!/bin/bash\n')
        _stub(self.stub_bin, 'curl', CURL_REPO_STUB)

    def configure_rocky(self, code='', **kwargs):
        return self.configure('rocky', '8.7', code=code, env={'CP_PKG_MANAGER_INSTALL_TIMEOUT_SEC': '2'}, **kwargs)

    def test_a_yum_that_hangs_once_is_reported_in_the_run_log_and_tried_again(self):
        proc = self.configure_rocky(hangs=1)
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(1, proc.stdout.count('[WARN] (attempt: 1) yum did not finish in time and was stopped, see '))
        self.assertEqual(1, proc.stdout.count('Failed to configure'))
        self.assertEqual([YUM_PRIORITIES_CALL] * 2, self.calls())
        self.assertEqual(CLOUD_PIPELINE_REPO, _read(self.path('/etc/yum.repos.d/cloud-pipeline.repo')))
        self.assertIn("'yum %s' did not finish" % YUM_PRIORITIES_CALL, _read(self.path('/var/log/yum.cp.log')))

    def test_a_timeout_is_not_reported_for_the_next_attempts_when_curl_fails(self):
        # Attempts 2 and 3 do not run yum, so the timeout of attempt 1 must not be reported for them
        proc = self.configure_rocky(hangs=1, CURL_FAILS_AFTER=os.path.join(self.tmp, 'curl.fetched'))
        self.assertEqual(1, proc.stdout.count('did not finish'))
        self.assertIn('[WARN] (attempt: 1) yum did not finish', proc.stdout)
        self.assertEqual(3, proc.stdout.count('Failed to configure'))

    def test_a_yum_utils_install_that_hangs_is_reported_in_the_run_log(self):
        proc = self.configure_rocky(code=NOT_INSTALLED % 'yum-config-manager', hangs=1, STUB_HANG_ON='yum-utils')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual(1, proc.stdout.count('[WARN] yum did not finish in time and was stopped, see '))
        self.assertNotIn('Failed to configure', proc.stdout)
        self.assertEqual(CLOUD_PIPELINE_REPO, _read(self.path('/etc/yum.repos.d/cloud-pipeline.repo')))

    def test_a_yum_utils_install_that_passes_is_not_reported(self):
        proc = self.configure_rocky(code=NOT_INSTALLED % 'yum-config-manager')
        self.assertEqual(0, proc.returncode, proc.stderr)
        self.assertEqual([YUM_PRIORITIES_CALL, YUM_PRIORITIES_CALL.replace('yum-priorities', 'yum-utils')],
                         self.calls())
        self.assertNotIn('did not finish', proc.stdout)


class LaunchPackageManagerCallsTest(unittest.TestCase):
    # An apt-get, yum or apk call that downloads: update, install, upgrade or add in the same command (up to ;, &, |
    # or the quote that ends the command text; an escaped quote, as in -o Dpkg::Options::=\"...\", does not end it)
    DOWNLOADING_CALL = re.compile(r'\b(apt-get|yum|apk)\b(?=(?:[^;&|"\\]|\\.)*\b(update|install|upgrade|add)\b)')
    TIME_LIMIT = re.compile(r'\brun_pkg_manager_(update|install) $')

    def test_every_downloading_call_has_a_time_limit(self):
        unlimited = []
        for number, line in enumerate(_read(LAUNCH_SH).splitlines(), 1):
            if line.lstrip().startswith('#'):
                continue
            for call in self.DOWNLOADING_CALL.finditer(line):
                if not self.TIME_LIMIT.search(line[:call.start()]):
                    unlimited.append('%d: %s' % (number, line.strip()))
        self.assertEqual([], unlimited)

    def test_the_check_finds_the_calls_with_no_limit(self):
        line = 'check_installed "apt-get" && { CMD="rm -rf lists; apt-get update -y -qq; ' \
               'apt-get -y -o Dpkg::Options::=\\"--force-confold\\" upgrade";  };'
        calls = [line[:call.start()] for call in self.DOWNLOADING_CALL.finditer(line)]
        self.assertEqual(2, len(calls))
        self.assertFalse(any(self.TIME_LIMIT.search(prefix) for prefix in calls))


if __name__ == '__main__':
    unittest.main()
