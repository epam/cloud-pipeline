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

import json

from mock import MagicMock, patch

from pipeline.api import PipelineAPI, Tool

TOOL_PAYLOAD = {
    'id': 1,
    'image': 'library/centos',
    'cpu': '1',
    'ram': '1Gi',
    'registry': 'registry:5000',
    'registryId': 1,
    'toolGroupId': 1
}


def load_tool(payload):
    response = MagicMock()
    response.json.return_value = {'status': 'OK', 'payload': payload}
    with patch('requests.get', return_value=response):
        return PipelineAPI(api_url='https://api/', log_dir='logs', token=MagicMock()) \
            .load_tool('library/centos', '')


def test_load_tool_reads_default_version():
    payload = dict(TOOL_PAYLOAD, defaultVersion='v2')

    assert load_tool(payload).default_version == 'v2'


def test_load_tool_without_default_version():
    assert load_tool(TOOL_PAYLOAD).default_version is None


def test_tool_json_does_not_hold_default_version():
    tool = Tool('library/centos', '1', '1Gi', 'registry:5000', 1, 1)
    tool.default_version = 'v2'

    fields = json.loads(tool.to_json())

    assert 'default_version' not in fields
    assert 'defaultVersion' not in fields
    assert fields['image'] == 'library/centos'
