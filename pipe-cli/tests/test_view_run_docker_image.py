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

from mock import patch

from pipe import view_run
from src.model.pipeline_run_model import PipelineRunModel
from tests.test_utils.build_models import build_pipeline_run_price
from tests.test_utils.stdout_parsers import get_stdout_string

RUN_ID = 1
DOCKER_IMAGE = 'registry:443/library/centos:7'
DIGEST = 'sha256:6c3c624b58dbbcd3c0dd82b4c53f04194d1247c6eebdaab7c610cf7d66709b3b'
DOCKER_IMAGE_ROW = 'Docker image:'
DIGEST_ROW = 'Docker image digest:'


def _run(**fields):
    payload = {'id': RUN_ID, 'status': 'STOPPED', 'dockerImage': DOCKER_IMAGE}
    payload.update(fields)
    return PipelineRunModel.load(payload)


def _row(output, key):
    for line in output.splitlines():
        if line.strip().startswith(key):
            return line.strip()[len(key):].strip()
    return None


@patch('src.api.pipeline_run.PipelineRun.get_estimated_price')
@patch('src.api.pipeline_run.PipelineRun.get')
def _view_run(run, get, get_estimated_price, output=None):
    get.return_value = run
    get_estimated_price.return_value = build_pipeline_run_price()
    return get_stdout_string(lambda: view_run(RUN_ID, False, False, False, output=output))


def test_load_reads_docker_image_digest():
    run = _run(actualDockerImageDigest=DIGEST)

    assert run.docker_image == DOCKER_IMAGE
    assert run.docker_image_digest == DIGEST


def test_load_leaves_digest_empty_for_run_launched_with_tag():
    assert _run().docker_image_digest is None


def test_view_run_prints_docker_image_and_digest():
    output = _view_run(_run(actualDockerImageDigest=DIGEST))

    assert _row(output, DOCKER_IMAGE_ROW) == DOCKER_IMAGE
    assert _row(output, DIGEST_ROW) == DIGEST


def test_view_run_prints_no_digest_for_run_launched_with_tag():
    output = _view_run(_run())

    assert _row(output, DOCKER_IMAGE_ROW) == DOCKER_IMAGE
    assert _row(output, DIGEST_ROW) is None


def test_view_run_json_contains_docker_image_and_digest():
    output = json.loads(_view_run(_run(actualDockerImageDigest=DIGEST), output='json'))

    assert output['dockerImage'] == DOCKER_IMAGE
    assert output['dockerImageDigest'] == DIGEST
