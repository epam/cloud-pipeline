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

import os
import sys

try:
    from unittest.mock import MagicMock
except ImportError:
    from mock import MagicMock

for _module in ['pykube', 'jwt', 'pipeline']:
    if _module not in sys.modules:
        sys.modules[_module] = MagicMock()

try:
    import distutils.version  # noqa: F401
except ImportError:
    sys.modules['distutils'] = MagicMock()
    sys.modules['distutils.version'] = MagicMock()

_NODEUP_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir, 'nodeup.py')

if 'aws_nodeup' not in sys.modules:
    if sys.version_info[0] >= 3:
        import importlib.util
        _spec = importlib.util.spec_from_file_location('aws_nodeup', _NODEUP_PATH)
        _nodeup = importlib.util.module_from_spec(_spec)
        sys.modules['aws_nodeup'] = _nodeup
        _spec.loader.exec_module(_nodeup)
    else:
        import imp
        imp.load_source('aws_nodeup', _NODEUP_PATH)
