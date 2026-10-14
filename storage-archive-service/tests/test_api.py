# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
Tests of the requests this client sends, for the two calls where the request itself is the risk.

No HTTP happens here: `_request` is replaced, and what the tests assert on is the method, the endpoint
and the body it was handed. That is deliberately narrow. The endpoint string and the body shape are the
part no other test can catch - a wrong one answers 404 or a refusal at run time, in a deployment, with
nothing in this service able to tell that it asked wrongly rather than being told no.
"""

from sas.api import CloudPipelineApi


class _Recorder:
    """A stand-in for `_request` that records the call and answers with a fixed payload."""

    def __init__(self, payload):
        self.payload = payload
        self.calls = []

    def __call__(self, method, endpoint, params=None, body=None, retry=True):
        self.calls.append({'method': method, 'endpoint': endpoint, 'body': body})
        return self.payload


def _api(payload, monkeypatch) -> tuple[CloudPipelineApi, _Recorder]:
    api = CloudPipelineApi('https://api.example.com/pipeline/restapi', 'token')
    recorder = _Recorder(payload)
    # `monkeypatch.setattr` on the instance, not the class: the replacement goes away with the test
    # either way, and this makes it obvious that only this client is affected.
    monkeypatch.setattr(api, '_request', recorder)
    return api, recorder


def test_reads_a_region_by_its_numeric_id(monkeypatch):
    api, recorder = _api({'id': 2, 'regionId': 'eu-central-1', 'provider': 'AWS',
                          'tempCredentialsRole': 'arn:aws:iam::222:role/temp'}, monkeypatch)

    region = api.load_region(2)

    # The numeric id of the platform's region record, which is what a storage carries as `regionId` -
    # not the cloud's own name for the region, which is what the answer calls `regionId`.
    assert recorder.calls == [{'method': 'GET', 'endpoint': 'cloud/region/2', 'body': None}]
    assert region.code == 'eu-central-1'
    assert region.temp_credentials_role == 'arn:aws:iam::222:role/temp'


def test_reads_a_storage_with_the_role_it_names(monkeypatch):
    api, recorder = _api({'id': 437, 'name': 'test2222', 'type': 'S3', 'path': 'bucket/team',
                          'regionId': 1, 'tempCredentialsRole': 'arn:aws:iam::111:role/storage'},
                         monkeypatch)

    storage = api.load_storage(437)

    assert recorder.calls == [{'method': 'GET', 'endpoint': 'datastorage/437/load', 'body': None}]
    assert storage.temp_credentials_role == 'arn:aws:iam::111:role/storage'
