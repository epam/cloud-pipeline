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

# pipe-common runs on Python 3 only: every Python source of the package, of its scripts and the Spark UI proxy
# compiles there without a syntax warning (a print statement, an invalid escape sequence in a string, ...).
# Most scripts are only run in a job container, so no other test imports them.

import os
import unittest
import warnings

PIPE_COMMON_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PYTHON_SCRIPTS_WITHOUT_SUFFIX = [os.path.join('shell', 'spark_setup_ui')]


def _python_sources():
    for top_dir in ['pipeline', 'scripts']:
        for root, _, file_names in os.walk(os.path.join(PIPE_COMMON_DIR, top_dir)):
            for file_name in sorted(file_names):
                if file_name.endswith('.py'):
                    yield os.path.relpath(os.path.join(root, file_name), PIPE_COMMON_DIR)
    for path in PYTHON_SCRIPTS_WITHOUT_SUFFIX:
        yield path


class Python3SourcesTest(unittest.TestCase):

    def test_sources_compile_without_warnings(self):
        failures = []
        sources = list(_python_sources())
        for path in sources:
            with open(os.path.join(PIPE_COMMON_DIR, path), 'rb') as f:
                source = f.read()
            with warnings.catch_warnings():
                warnings.simplefilter('error')
                try:
                    compile(source, path, 'exec', dont_inherit=True)
                except (SyntaxError, SyntaxWarning, DeprecationWarning) as e:
                    failures.append('{}: {}'.format(path, e))
        self.assertGreater(len(sources), 50)
        self.assertEqual([], failures)


if __name__ == '__main__':
    unittest.main()
