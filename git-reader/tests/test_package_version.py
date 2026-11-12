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

# Covers the package version of setup.py: the build writes <major>.<minor>.<patch>.<build number>.<commit> into
# gitreader/version.py, and setuptools on Python 3.12 rejects the commit in the release segment of a version.
# Each test runs "setup.py --version" on a copy of setup.py and the gitreader package, with the version.py the test
# writes. The tests are skipped where setuptools is not installed.
#
# Run: python -m unittest discover -s git-reader/tests

import os
import shutil
import subprocess
import sys
import tempfile
import unittest

GIT_READER_HOME = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


class TestPackageVersion(unittest.TestCase):

    def setUp(self):
        try:
            import setuptools  # noqa: F401
        except ImportError:
            self.skipTest('setuptools is not installed for %s' % sys.executable)
        self.tmp = tempfile.mkdtemp(prefix='gitreader-package-version-')
        shutil.copy(os.path.join(GIT_READER_HOME, 'setup.py'), self.tmp)
        os.makedirs(os.path.join(self.tmp, 'gitreader'))
        shutil.copy(os.path.join(GIT_READER_HOME, 'gitreader', '__init__.py'), os.path.join(self.tmp, 'gitreader'))

    def tearDown(self):
        if hasattr(self, 'tmp'):
            shutil.rmtree(self.tmp, ignore_errors=True)

    def _package_version(self, version):
        with open(os.path.join(self.tmp, 'gitreader', 'version.py'), 'w') as f:
            f.write("__version__='%s'\n" % version)
        env = dict(os.environ, PYTHONDONTWRITEBYTECODE='1')
        process = subprocess.Popen([sys.executable, 'setup.py', '--version'], cwd=self.tmp, env=env,
                                   stdout=subprocess.PIPE, stderr=subprocess.STDOUT, universal_newlines=True)
        output, _ = process.communicate()
        if process.returncode != 0:
            self.fail('setup.py --version exited with %s:\n%s' % (process.returncode, output))
        return output.strip().splitlines()[-1]

    def test_build_version_carries_the_commit_as_the_local_label(self):
        self.assertEqual('0.16.0.22672+3fb74609faffe405749207d3af849c631b328956',
                         self._package_version('0.16.0.22672.3fb74609faffe405749207d3af849c631b328956'))

    def test_build_version_without_a_commit_stays_as_is(self):
        self.assertEqual('0.16.0.22672', self._package_version('0.16.0.22672'))

    def test_release_version_stays_as_is(self):
        self.assertEqual('0.17.0', self._package_version('0.17.0'))

    def test_default_version_stays_as_is(self):
        self.assertEqual('0.0.1', self._package_version('0.0.1'))


if __name__ == '__main__':
    unittest.main()
