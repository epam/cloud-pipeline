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

# Covers the end of cp-billing-srv's "init" (issue #4742): the container process waits on the
# billing agent, not on the "tail" of its log, so it exits with the agent's exit code when the agent
# dies, and Kubernetes restarts the container. SIGTERM still stops the agent first.
#
# The agent is a stub "java" on PATH. Each test takes a few seconds: "init" sleeps 5 seconds after
# the start. Run with `python3 -m unittest test_init` from inside deploy/docker/cp-billing-srv/tests,
# on Linux or macOS, with bash, nohup and tail on PATH.

import os
import shutil
import signal
import stat
import subprocess
import tempfile
import time
import unittest

THIS_DIR = os.path.dirname(os.path.abspath(__file__))
INIT_SCRIPT = os.path.join(os.path.dirname(THIS_DIR), 'init')
BASH = shutil.which('bash') or '/bin/bash'
TIMEOUT = 30


class TestInit(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp(prefix='cp-billing-srv-init-')
        self.home = os.path.join(self.tmp, 'billing')
        self.bin = os.path.join(self.tmp, 'bin')
        os.makedirs(self.home)
        os.makedirs(self.bin)
        self.marker = os.path.join(self.tmp, 'java-got-sigterm')
        self.init = None

    def tearDown(self):
        if self.init is not None and self.init.poll() is None:
            self._kill_init()
        shutil.rmtree(self.tmp, ignore_errors=True)

    def _kill_init(self):
        # init, its tail and the stub agent share one process group
        try:
            os.killpg(self.init.pid, signal.SIGKILL)
        except OSError:
            pass
        self.init.wait()

    def _stub_java(self, body):
        path = os.path.join(self.bin, 'java')
        with open(path, 'w') as f:
            f.write('#!/bin/bash\n' + body)
        os.chmod(path, os.stat(path).st_mode | stat.S_IXUSR | stat.S_IXGRP | stat.S_IXOTH)

    def _start_init(self):
        env = dict(os.environ)
        env['PATH'] = self.bin + os.pathsep + env.get('PATH', '')
        env['CP_BILLING_HOME'] = self.home
        env.pop('CP_BILLING_CA_BUNDLE', None)
        self.init = subprocess.Popen([BASH, INIT_SCRIPT], env=env, start_new_session=True,
                                     stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        return self.init

    def _wait_init(self):
        try:
            output, _ = self.init.communicate(timeout=TIMEOUT)
        except subprocess.TimeoutExpired:
            self._kill_init()
            self.fail('init did not exit within %d seconds' % TIMEOUT)
        return output

    def test_exits_with_the_agent_exit_code(self):
        self._stub_java('echo "Application run failed"\nexit 3\n')
        init = self._start_init()
        output = self._wait_init()
        self.assertEqual(3, init.returncode, output)
        # The agent's log still reaches the container output before the container ends
        self.assertIn(b'Application run failed', output)
        self.assertIn(b'exited with code 3', output)

    def test_exits_when_the_agent_dies_later(self):
        self._stub_java('sleep 7\nexit 1\n')
        init = self._start_init()
        output = self._wait_init()
        self.assertEqual(1, init.returncode, output)

    def test_sigterm_stops_the_agent(self):
        self._stub_java('trap \'touch "%s"; exit 0\' TERM\n'
                        'while true; do sleep 0.2; done\n' % self.marker)
        init = self._start_init()
        # The SIGTERM handler is set after the 5 second start delay
        time.sleep(6.5)
        self.assertIsNone(init.poll(), 'init ended before SIGTERM')
        init.send_signal(signal.SIGTERM)
        output = self._wait_init()
        self.assertEqual(143, init.returncode, output)
        self.assertTrue(os.path.exists(self.marker), 'the agent did not get SIGTERM')


if __name__ == '__main__':
    unittest.main()
