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

import ast
import os

import pytest
from packaging.version import Version

SETUP_PY = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'setup.py')


def _package_version():
    # setup.py calls setup() on import, so only its _package_version function is taken
    with open(SETUP_PY) as setup_file:
        tree = ast.parse(setup_file.read())
    function = next(node for node in tree.body if isinstance(node, ast.FunctionDef) and node.name == '_package_version')
    namespace = {}
    exec(compile(ast.Module(body=[function], type_ignores=[]), SETUP_PY, 'exec'), namespace)
    return namespace['_package_version']


@pytest.mark.parametrize('version, package_version', [
    ('0.16.0.20123.9069d5f762e9880808799f98a6f05bed9cdae5e4',
     '0.16.0.20123+9069d5f762e9880808799f98a6f05bed9cdae5e4'),
    ('0.16.0.20123', '0.16.0.20123'),
    ('0.10', '0.10'),
])
def test_package_version_is_pep_440(version, package_version):
    assert _package_version()(version) == package_version
    assert str(Version(package_version)) == package_version
