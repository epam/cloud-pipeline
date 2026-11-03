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

import base64
import os
import sys
import tarfile
import types

try:
    from io import BytesIO
except ImportError:
    from StringIO import StringIO as BytesIO

# ``pipeline/__init__.py`` pulls in luigi and other heavyweight, environment-specific
# transitive dependencies unrelated to pack_script_contents. Stub the ``pipeline`` and
# ``pipeline.common`` packages in sys.modules so the real ``pipeline/common/common.py``
# loads without importing that package __init__.
_PIPE_COMMON_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
if _PIPE_COMMON_DIR not in sys.path:
    sys.path.insert(0, _PIPE_COMMON_DIR)

if 'pipeline' not in sys.modules:
    _pipeline_pkg = types.ModuleType('pipeline')
    _pipeline_pkg.__path__ = [os.path.join(_PIPE_COMMON_DIR, 'pipeline')]
    sys.modules['pipeline'] = _pipeline_pkg

if 'pipeline.common' not in sys.modules:
    _pipeline_common_pkg = types.ModuleType('pipeline.common')
    _pipeline_common_pkg.__path__ = [os.path.join(_PIPE_COMMON_DIR, 'pipeline', 'common')]
    sys.modules['pipeline.common'] = _pipeline_common_pkg

from pipeline.common.common import pack_powershell_script_contents, pack_script_contents


class TestPackScriptContents(object):

    def test_payload_is_a_plain_base64_string_without_the_bytes_repr(self):
        packed = pack_script_contents('#!/bin/bash\necho hello\n')
        lines = packed.split('\n')
        payload = lines[lines.index('EOF') - 1]
        assert not payload.startswith("b'")
        assert not payload.endswith("'")
        # Raises if payload is not valid base64.
        base64.b64decode(payload)

    def test_payload_extracts_back_to_the_original_script(self):
        script_contents = '#!/bin/bash\necho hello\n'
        packed = pack_script_contents(script_contents)
        lines = packed.split('\n')
        payload = lines[lines.index('EOF') - 1]

        with tarfile.open(fileobj=BytesIO(base64.b64decode(payload)), mode='r:gz') as tar:
            extracted = tar.extractfile('init.sh').read().decode('utf-8')
        assert extracted == script_contents


class TestPackPowershellScriptContents(object):

    def test_payload_is_a_plain_base64_string_without_the_bytes_repr(self):
        packed = pack_powershell_script_contents('echo hello')
        lines = packed.split('\n')
        payload = lines[lines.index('"@') - 1]
        assert not payload.startswith("b'")
        assert not payload.endswith("'")
        # Raises if payload is not valid base64.
        base64.b64decode(payload)

    def test_payload_extracts_back_to_the_original_script(self):
        script_contents = 'echo hello'
        packed = pack_powershell_script_contents(script_contents)
        lines = packed.split('\n')
        payload = lines[lines.index('"@') - 1]

        with tarfile.open(fileobj=BytesIO(base64.b64decode(payload)), mode='r:gz') as tar:
            extracted = tar.extractfile('init.ps1').read().decode('utf-8')
        assert extracted == script_contents
