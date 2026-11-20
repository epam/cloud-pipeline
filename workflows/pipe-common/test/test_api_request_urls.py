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

"""
Tests of the URLs the API client sends.

The API rejects a request whose path is not normalized - a `//` in it - so a base URL that ends with
a slash, joined with an endpoint that starts with one, must still reach the API as one slash.
"""

import pytest

try:
    from unittest.mock import MagicMock, patch
except ImportError:
    from mock import MagicMock, patch

from pipeline.api import api as api_module
from pipeline.api.api import APIError, PipelineAPI
from pipeline.api.token import StaticToken

API_URL = 'https://api.example.com/pipeline/restapi/'


def _api():
    return PipelineAPI(api_url=API_URL, log_dir='/tmp', attempts=1, timeout=0, token=StaticToken('token'))


def _response(payload):
    response = MagicMock()
    response.status_code = 200
    response.json.return_value = {'status': 'OK', 'payload': payload}
    return response


def test_collapses_a_double_slash_in_the_path_and_keeps_the_scheme():
    url = _api().normalize_url('https://api.example.com/pipeline/restapi//cluster/pool/1')

    assert url == 'https://api.example.com/pipeline/restapi/cluster/pool/1'


def test_sends_a_request_with_a_normalized_path():
    with patch.object(api_module.requests, 'get', return_value=_response({'id': 1})) as get:
        _api().execute_request(API_URL + '/run/1', method='get')

    assert get.call_args[0][0] == 'https://api.example.com/pipeline/restapi/run/1'


def test_loads_a_node_pool_by_a_normalized_url():
    pool = {'id': 42, 'amiConfiguration': {'availability_zone': 'us-east-1c'}}
    with patch.object(api_module.requests, 'get', return_value=_response(pool)) as get:
        loaded = _api().load_node_pool(42)

    assert get.call_args[0][0] == 'https://api.example.com/pipeline/restapi/cluster/pool/42'
    assert loaded == pool


def test_answers_an_empty_pool_when_the_api_sends_none():
    with patch.object(api_module.requests, 'get', return_value=_response(None)):
        assert _api().load_node_pool(42) == {}


def test_joins_an_endpoint_with_a_single_slash():
    with patch.object(api_module.requests, 'request', return_value=_response({'id': 1})) as request:
        _api().load_datastorage(1)

    assert request.call_args[1]['url'] == 'https://api.example.com/pipeline/restapi/datastorage/1/load'


def test_lets_a_refusal_to_load_a_storage_through_as_it_is():
    api = _api()
    api._request = MagicMock(side_effect=APIError('ERROR: not found'))

    with pytest.raises(APIError):
        api.load_datastorage(1)


def test_wraps_a_failure_to_reach_the_api_for_a_storage():
    api = _api()
    api._request = MagicMock(side_effect=IOError('connection refused'))

    with pytest.raises(RuntimeError) as error:
        api.load_datastorage(1)

    assert not isinstance(error.value, APIError)
