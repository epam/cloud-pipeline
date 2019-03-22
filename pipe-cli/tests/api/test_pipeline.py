# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import datetime as dt
import pytz
import requests_mock
from src.api.pipeline import Pipeline
from tests.test_utils.assertions_utils import *
from tests.test_utils.build_models import *
from tests.test_utils.mocked_requests import *

server_date = dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
raw_date = dt.datetime.now(pytz.utc).strftime("%Y-%m-%d %H:%M:%S.%f")

API = "https://pipeline-cloud/pipeline/restapi/"
API_TOKEN = "hz"


def setup_module(module):
    os.environ["API"] = API
    os.environ["API_TOKEN"] = API_TOKEN


class TestPipeline(object):

    pipeline_id = "1"
    run_id = "1"
    pipeline_name = "test_pipe"
    instance_type = "test_type"
    instance_disk = "11"
    repository = "my/repo"
    docker_image = "my/awesome/docker/image"
    v_1 = "v1"
    v_2 = "v2"

    @requests_mock.mock()
    def test_get_pipeline_model(self, mock):
        mock.get(mocked_url('pipeline/find?id={}'.format(self.pipeline_id)),
                 text=mock_pipe(self.pipeline_id, self.pipeline_name, raw_date, self.v_1))
        actual = Pipeline.get(self.pipeline_id, load_storage_rules=False, load_versions=False,
                              load_run_parameters=False)
        expected = build_pipeline_model(identifier=self.pipeline_id, name=self.pipeline_name)
        assert actual.name == expected.name
        assert actual.identifier == expected.identifier
        assert actual.storage_rules == expected.storage_rules
        assert actual.description == expected.description
        assert actual.created_date == expected.created_date
        assert actual.repository == expected.repository
        assert actual.versions == expected.versions

    @requests_mock.mock()
    def test_get_pipeline_model_with_versions(self, mock):
        mock.get(mocked_url('pipeline/find?id={}'.format(self.pipeline_id)),
                 text=mock_pipe(self.pipeline_id, self.pipeline_name, raw_date, self.v_1))
        mock.get(mocked_url('pipeline/{}/versions'.format(self.pipeline_id)),
                 text=mock_versions(raw_date, self.v_1, self.v_2))
        actual = Pipeline.get(self.pipeline_id, load_storage_rules=False, load_versions=True,
                              load_run_parameters=False)
        expected = build_pipeline_model(identifier=self.pipeline_id, name=self.pipeline_name,
                                        current_version=build_version(self.v_1, server_date),
                                        current_version_name=self.v_1,
                                        versions=[build_version(self.v_1, server_date),
                                                  build_version(self.v_2, server_date)])
        assert actual.name == expected.name
        assert actual.identifier == expected.identifier
        assert actual.storage_rules == expected.storage_rules
        assert actual.description == expected.description
        assert actual.created_date == expected.created_date
        assert actual.current_version_name == expected.current_version_name
        assert_version(actual.current_version, expected.current_version)
        assert actual.repository == expected.repository
        assert len(actual.versions) == len(expected.versions)
        for (actual_version, expected_version) in zip(actual.versions, expected.versions):
            assert_version(actual_version, expected_version)

    @requests_mock.mock()
    def test_get_pipeline_model_with_bucket(self, mock):
        mock.get(mocked_url('pipeline/find?id={}'.format(self.pipeline_id)),
                 text=mock_pipe(self.pipeline_id, self.pipeline_name, raw_date, self.v_1))
        mock.get(mocked_url('datastorage/rule/load?pipelineId={}'.format(self.pipeline_id)),
                 text=mock_pipeline_datastorage(self.pipeline_id, raw_date))
        actual = Pipeline.get(self.pipeline_id, load_storage_rules=True, load_versions=False,
                              load_run_parameters=False)
        expected = build_pipeline_model(identifier=self.pipeline_id, name=self.pipeline_name,
                                        storage_rules=[build_storage_rule(self.pipeline_id, server_date, "bucket/1"),
                                                       build_storage_rule(self.pipeline_id, server_date, "bucket/2")])
        assert actual.name == expected.name
        assert actual.identifier == expected.identifier
        assert actual.description == expected.description
        assert actual.created_date == expected.created_date
        assert actual.repository == expected.repository
        assert actual.versions == expected.versions
        assert_storage_rules(actual.storage_rules, expected.storage_rules)

    @requests_mock.mock()
    def test_get_pipeline_model_with_run_parameters(self, mock):
        mock.get(mocked_url('pipeline/find?id={}'.format(self.pipeline_id)),
                 text=mock_pipe(self.pipeline_id, self.pipeline_name, raw_date, self.v_1))
        mock.get(mocked_url('pipeline/{}/parameters?version={}'.format(self.pipeline_id, self.v_1)),
                 text=mock_run_parameters(self.instance_disk, self.instance_type))
        actual = Pipeline.get(self.pipeline_id, load_storage_rules=False, load_versions=False,
                              load_run_parameters=True)
        expected = build_pipeline_model(identifier=self.pipeline_id, name=self.pipeline_name,
                                        current_version=build_version(self.v_1, server_date,
                                                                      run_parameters=build_run_parameters(
                                                                          self.v_1,
                                                                          parameters=[
                                                                              build_run_parameter(
                                                                                  value="param",
                                                                                  parameter_type="input")])),
                                        current_version_name=self.v_1)
        assert actual.name == expected.name
        assert actual.identifier == expected.identifier
        assert actual.storage_rules == expected.storage_rules
        assert actual.description == expected.description
        assert actual.created_date == expected.created_date
        assert actual.current_version_name == expected.current_version_name
        assert_version(actual.current_version, expected.current_version)
        assert actual.repository == expected.repository
        assert actual.versions == expected.versions

    @requests_mock.mock()
    def test_price(self, mock):
        mock.post(mocked_url("pipeline/{}/price?version={}".format(self.pipeline_id, self.pipeline_name)),
                  text=mock_price(self.instance_disk, self.instance_type))
        actual = Pipeline.get_estimated_price(self.pipeline_id, self.pipeline_name, self.instance_type,
                                              self.instance_disk)
        expected = build_instance_price()
        assert actual.instance_type == expected.instance_type
        assert actual.instance_disk == expected.instance_disk
        assert actual.price_per_hour == expected.price_per_hour
        assert actual.minimum_time_price == expected.minimum_time_price
        assert actual.maximum_time_price == expected.maximum_time_price
        assert actual.average_time_price == expected.average_time_price

    @requests_mock.mock()
    def test_launch_pipeline(self, mock):
        mock.post(mocked_url('run'), text=mock_run(self.pipeline_id, docker_image=self.docker_image,
                                                   identifier=self.run_id))
        actual = Pipeline.launch_pipeline(self.pipeline_id, self.v_1, [build_run_parameter()],
                                          self.docker_image, None, None)
        expected = build_run_model(identifier=self.run_id, status="SCHEDULED", pipeline="image",
                                   pipeline_id=self.pipeline_id, parameters=[build_run_parameter()])
        assert actual.identifier == expected.identifier
        assert actual.status == expected.status
        assert actual.version == expected.version
        assert actual.pipeline == expected.pipeline
        assert actual.pipeline_id == expected.pipeline_id
        assert actual.parent_id == expected.parent_id
        assert actual.start_date == expected.start_date
        assert actual.end_date == expected.end_date
        assert actual.scheduled_date == expected.scheduled_date
        assert actual.tasks == expected.tasks
        assert actual.instance == expected.instance
        assert_run_parameter(actual.parameters, actual.parameters)
