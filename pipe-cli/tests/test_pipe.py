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
from mock import patch
from pipe import *
from tests.test_utils.assertions_utils import *
from tests.test_utils.mocked_requests import *
from tests.test_utils.stdout_parsers import *

server_date = dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
raw_date = dt.datetime.now(pytz.utc).strftime("%Y-%m-%d %H:%M:%S.%f")
zoned_date = dt.datetime.now().strftime("%Y-%m-%dT%H:%M:%SZ")

API = "https://pipeline-cloud/pipeline/restapi/"
API_TOKEN = "hz"

pipeline_id = "1"
pipeline_id_2 = "2"
pipeline_name = "pipe_1"
pipeline_name_2 = "pipe_2"
v_1 = "v0.1"
v_2 = "v0.2"
parameter_name = "param_1"
parameter_name_2 = "param_2"
parameter_value = "test_value"
parameter_value_2 = "test_value_2"
parameter_type = "input"
repo = "my/rep"
run_id = "777"
name_task = "test_task"
parent_id = "666"
node_ip = "111.11.11.111"
node_ip_2 = "222.22.22.222"
node_name = "ip-111-11-11-111"
node_name_2 = "ip-222-22-22-222"
uid_1 = "uid_1"
uid_2 = "uid_2"
system_info = {
    "machineID": "m1",
    "operatingSystem": "linux"
}
instance_disk = 100
instance_type = "m4"
docker_image = "image"
test_user = "user"


def setup_module(module):
    os.environ["API"] = API
    os.environ["API_TOKEN"] = API_TOKEN


class TestViewPipes(object):

    @patch('src.api.pipeline.Pipeline.get')
    @patch('src.api.user.User.get_permissions')
    def test_view_pipe(self, permissions_mock, get_mock):
        permission_name = "permission_name"
        expected_permissions = [build_permission_model(name=permission_name, principal=True, write_allowed=True,
                                                       execute_denied=True, read_denied=True)]
        permissions_mock.return_value = expected_permissions, test_user
        expected = build_pipeline_model(identifier=pipeline_id, name=pipeline_name,
                                        current_version=build_version(v_1, None, draft=None,
                                                                      run_parameters=build_run_parameters(
                                                                          v_1,
                                                                          parameters=[build_run_parameter(
                                                                              value=parameter_value,
                                                                              parameter_type=parameter_type)])
                                                                      ),
                                        current_version_name=v_1,
                                        versions=[build_version(v_1, server_date),
                                                  build_version(v_2, server_date)],
                                        storage_rules=[
                                            build_storage_rule(pipeline_id, server_date, "bucket/1"),
                                            build_storage_rule(pipeline_id, server_date, "bucket/2")]
                                        )
        get_mock.return_value = expected
        versions = True
        parameters = True
        storage_rules = True
        actual_pipe, actual_permissions = parse_view_pipe_stdout(get_stdout_string(
            lambda: view_pipe(pipeline_id, versions, parameters, storage_rules, True)))
        assert actual_pipe.name == expected.name
        assert actual_pipe.identifier == expected.identifier
        assert actual_pipe.description == expected.description
        assert actual_pipe.created_date == expected.created_date
        assert actual_pipe.current_version_name == expected.current_version_name
        assert actual_pipe.repository == expected.repository
        assert_versions(actual_pipe.versions, expected.versions)
        assert_storage_rules(actual_pipe.storage_rules, expected.storage_rules)
        assert_permissions(actual_permissions, expected_permissions)

    @requests_mock.mock()
    def test_view_pipes(self, mock):
        expected = [build_pipeline_model(identifier=pipeline_id, name=pipeline_name,
                                         created_date=server_date, repository=repo,
                                         current_version_name=v_1),
                    build_pipeline_model(identifier=pipeline_id_2, name=pipeline_name_2,
                                         created_date=server_date, repository=repo,
                                         current_version_name=v_1)]
        mock.get(mocked_url('pipeline/loadAll'), text=mock_load_pipes(pipeline_id, pipeline_id_2,
                                                                      pipeline_name,
                                                                      pipeline_name_2, raw_date,
                                                                      v_1, repo))
        actual = view_pipes_stdout(get_stdout_string(lambda: view_all_pipes()))
        assert len(actual) == len(expected)
        for (actual_pipe, expected_pipe) in zip(actual, expected):
            assert actual_pipe.name == expected_pipe.name
            assert actual_pipe.identifier == expected_pipe.identifier
            assert actual_pipe.description == expected_pipe.description
            assert actual_pipe.created_date == expected_pipe.created_date
            assert actual_pipe.current_version_name == expected_pipe.current_version_name
            assert actual_pipe.repository == expected_pipe.repository
            assert_versions(actual_pipe.versions, expected_pipe.versions)
            assert_storage_rules(actual_pipe.storage_rules, expected_pipe.storage_rules)


class TestViewRuns(object):

    @patch('src.api.pipeline_run.PipelineRun.get')
    @patch('src.api.pipeline_run.PipelineRun.get_estimated_price')
    def test_view_run(self, get_estimated_price, get):
        expected_pipe = build_run_model(identifier=run_id, status="RUNNING", pipeline=pipeline_name,
                                        pipeline_id=pipeline_id,
                                        parameters=[build_run_parameter(name=pipeline_name,
                                                                        value=parameter_value),
                                                    build_run_parameter(name=pipeline_name,
                                                                        value=parameter_value_2)],
                                        scheduled_date=server_date, start_date=server_date, version=v_1,
                                        tasks=[build_task_model(created=server_date, started=server_date,
                                                                finished=server_date, name=name_task,
                                                                status="SUCCESS")],
                                        parent_id=parent_id,
                                        instance=build_instance_model(node_ip=node_ip))
        get.return_value = expected_pipe
        expected_price = build_pipeline_run_price()
        get_estimated_price.return_value = expected_price
        node_details = True
        parameters_details = True
        tasks_details = True
        actual_pipe = parse_view_run_stdout(get_stdout_string(
            lambda: view_run(run_id, node_details, parameters_details, tasks_details)))
        assert actual_pipe.identifier == expected_pipe.identifier
        assert actual_pipe.status == expected_pipe.status
        assert actual_pipe.instance == expected_pipe.instance
        assert actual_pipe.version == expected_pipe.version
        assert actual_pipe.scheduled_date == expected_pipe.scheduled_date
        assert actual_pipe.start_date == expected_pipe.start_date
        assert actual_pipe.end_date == expected_pipe.end_date
        assert actual_pipe.instance == expected_pipe.instance
        assert_run_parameter(actual_pipe.parameters, expected_pipe.parameters)
        assert_tasks(actual_pipe.tasks, expected_pipe.tasks)

    @patch('src.api.pipeline.Pipeline.get')
    @requests_mock.mock()
    def test_view_runs(self, get_mock, list_mock):
        status = "ANY"
        date_from = None
        date_to = None
        pipeline = pipeline_name
        find = "test"
        top = 2
        pipeline_model = build_pipeline_model(identifier=pipeline_id, name=pipeline_name,
                                              created_date=server_date, repository=repo,
                                              current_version_name=v_1)
        get_mock.return_value = pipeline_model
        elements = [mock_element(identifier="1", pipeline_id=pipeline_id,
                                 pipeline_name=pipeline_name, version=v_1, start_date=raw_date,
                                 end_date=raw_date, parameters=[mock_parameter(name=parameter_name,
                                                                               value=parameter_value),
                                                                mock_parameter(name="parent-id", value=parent_id)],
                                 instance=mock_instance(node_ip), status="RUNNING"),
                    mock_element(identifier="2", pipeline_id=pipeline_id,
                                 pipeline_name=pipeline_name, version="v0.2", start_date=raw_date,
                                 end_date=raw_date, parameters=[mock_parameter(name=parameter_name,
                                                                               value=parameter_value),
                                                                mock_parameter(name=parameter_name_2,
                                                                               value=parameter_value_2),
                                                                mock_parameter(name="parent-id", value=parent_id)],
                                 instance=mock_instance(node_ip_2), status="SUCCESS")]
        list_mock.post(mocked_url('run/filter'), text=mock_run_filter(elements=elements))
        expected_pipes = [build_run_model(identifier="1", status="RUNNING", pipeline=pipeline_name,
                                          pipeline_id=pipeline_id, scheduled_date=server_date,
                                          start_date=server_date, version=v_1, parent_id=str(parent_id)),
                          build_run_model(identifier="2", status="SUCCESS", pipeline=pipeline_name,
                                          pipeline_id=pipeline_id, scheduled_date=server_date,
                                          start_date=server_date, version=v_2, parent_id=str(parent_id))]
        actual_pipe = parse_view_runs(get_stdout_string(
            lambda: view_all_runs(status, date_from, date_to, pipeline, parent_id, find, top)))
        assert len(actual_pipe) == len(expected_pipes)
        for (actual, expected) in zip(actual_pipe, expected_pipes):
            assert actual.identifier == expected.identifier
            assert actual.parent_id == expected.parent_id
            assert actual.pipeline == expected.pipeline
            assert actual.version == expected.version
            assert actual.status == expected.status
            assert actual.scheduled_date == expected.scheduled_date


class TestViewCluster(object):

    @requests_mock.mock()
    def test_view_cluster(self, mock):
        addresses = [build_address_model(address=node_ip, address_type="internalIP"),
                     build_address_model(address=node_name, address_type="Hostname")]
        node_1 = build_cluster_node_model(uid=uid_1, name=node_name, created=zoned_date,
                                          pipeline_run=mock_element(identifier="1", pipeline_name=pipeline_name))
        node_2 = build_cluster_node_model(uid=uid_2, name=node_name_2, created=zoned_date,
                                          pipeline_run=mock_element(identifier="2", pipeline_name=pipeline_name))
        mock.get(mocked_url('cluster/node/loadAll'),
                 text=mock_cluster_load_all([mock_node(node_1, addresses), mock_node(node_2, addresses)]))
        expected_addresses = self.convert_addresses_to_strings(addresses)
        expected_cluster_state = [
            ViewClusterStdoutModel(node_name, pipeline_name, "1", expected_addresses, server_date),
            ViewClusterStdoutModel(node_name_2, pipeline_name, "2", expected_addresses, server_date)]
        actual_cluster_state = parse_view_cluster(get_stdout_string(lambda: view_all_cluster()))
        assert len(actual_cluster_state) == len(expected_cluster_state)
        for (actual, expected) in zip(actual_cluster_state, expected_cluster_state):
            assert actual.name == expected.name
            assert actual.pipeline == expected.pipeline
            assert actual.run == expected.run
            assert actual.created == expected.created
            assert_addresses(actual.addresses, expected.addresses)

    @requests_mock.mock()
    def test_view_cluster_for_node(self, mock):
        addresses = [build_address_model(address=node_ip, address_type="internalIP"),
                     build_address_model(address=node_name, address_type="Hostname")]
        labels = {
            "runid": run_id,
        }
        pod = build_pod_model(uid="pod_uid", name="pod_name", namespace="default", node_name=node_name, phase="running")
        pods = [mock_node_pods(pod)]
        node_1 = build_cluster_node_model(uid=uid_1, name=node_name, created=zoned_date,
                                          pipeline_run=mock_element(identifier="1", pipeline_name=pipeline_name),
                                          addresses=addresses, pods=pods, labels=labels, system_info=system_info)
        node_2 = build_cluster_node_model(uid=uid_2, name=node_name_2, created=zoned_date,
                                          pipeline_run=mock_element(identifier="2", pipeline_name=pipeline_name),
                                          addresses=addresses)
        mock.get(mocked_url('cluster/node/loadAll'),
                 text=mock_cluster_load_all([mock_node(node_1, addresses),
                                             mock_node(node_2, addresses)]))
        mock.get(mocked_url('cluster/node/{}/load'.format(node_name)),
                 text=mock_node_load(node_1, addresses))
        expected_node = node_1
        expected_node.created = server_date
        expected_node.run = build_run_model(pipeline=pipeline_name)
        expected_node.addresses = self.convert_addresses_to_strings(addresses)
        expected_node.pods = [pod]
        actual_node = parse_view_cluster_for_node(get_stdout_string(
            lambda: view_cluster_for_node(node_name)))
        assert actual_node.name == expected_node.name
        assert actual_node.created == expected_node.created
        assert actual_node.run.pipeline == expected_node.run.pipeline
        assert actual_node.labels == expected_node.labels
        assert actual_node.addresses == expected_node.addresses
        assert actual_node.system_info == expected_node.system_info
        assert_pods(actual_node.pods, expected_node.pods)

    @staticmethod
    def convert_addresses_to_strings(addresses):
        addresses_with_strings = []
        for address in addresses:
            addresses_with_strings.append('{} ({})'.format(address.address, address.address_type))
        return addresses_with_strings


class TestRun(object):
    pipeline = "{}@{}".format(pipeline_name, v_1)
    parameters = False
    yes = True
    run_params = [parameter_name, parameter_value, parameter_value_2, parameter_value_2]
    cmd_template = "template"
    timeout = 10
    quiet = False
    config = None

    @requests_mock.mock()
    def test_pipeline_run_with_parameters(self, mock):
        price = build_instance_price(instance_type=instance_type, instance_disk=instance_disk)
        mock.get(mocked_url('pipeline/find?id={}'.format(pipeline_name)),
                 text=mock_pipe(pipeline_id, pipeline_name, raw_date, v_1))
        mock.get(mocked_url('pipeline/{}/parameters?version={}'.format(pipeline_id, v_1)),
                 text=mock_run_parameters(instance_disk, instance_type))
        mock.post(mocked_url("pipeline/{}/price?version={}".format(pipeline_id, v_1)),
                  text=mock_price(instance_disk, instance_type))
        mock.post(mocked_url('run'), text=mock_run(pipeline_id, docker_image=docker_image, identifier=run_id))
        actual_stdout = parse_run_stdout(
            get_stdout_string(lambda: PipelineRunOperations.run(self.pipeline, self.config, self.parameters, self.yes,
                                                                self.run_params,
                                                                instance_disk, instance_type, docker_image,
                                                                self.cmd_template,
                                                                self.timeout, self.quiet, None, None, False)))
        assert actual_stdout.run_id == run_id
        assert actual_stdout.pipeline_name == pipeline_name
        assert actual_stdout.version == v_1
        assert_instance_price(actual_stdout.price, price)

    @requests_mock.mock()
    def test_pipeline_run(self, mock):
        mock.get(mocked_url('pipeline/find?id={}'.format(pipeline_name)),
                 text=mock_pipe(pipeline_id, pipeline_name, raw_date, v_1))
        mock.get(mocked_url('pipeline/{}/parameters?version={}'.format(pipeline_id, v_1)),
                 text=mock_run_parameters(instance_disk, instance_type))
        mock.post(mocked_url("pipeline/{}/price?version={}".format(pipeline_id, v_1)),
                  text=mock_price(instance_disk, instance_type))
        mock.post(mocked_url('run'), text=mock_run(pipeline_id, docker_image=docker_image, identifier=run_id))
        actual_stdout = get_stdout_string(
            lambda: PipelineRunOperations.run(None, None, self.parameters, self.yes, self.run_params,
                                              instance_disk, instance_type, docker_image,
                                              self.cmd_template, self.timeout, self.quiet, None, None,
                                              False))
        expected_stdout = "Pipeline run scheduled with RunId: {}\n".format(run_id)
        assert actual_stdout == expected_stdout

    @requests_mock.mock()
    def test_run_with_missing_args(self, mock):
        mock.get(mocked_url('pipeline/find?id={}'.format(pipeline_name)),
                 text=mock_pipe(pipeline_id, pipeline_name, raw_date, v_1))
        mock.get(mocked_url('pipeline/{}/parameters?version={}'.format(pipeline_id, v_1)),
                 text=mock_run_parameters(instance_disk, instance_type))
        mock.post(mocked_url("pipeline/{}/price?version={}".format(pipeline_id, v_1)),
                  text=mock_price(instance_disk, instance_type))
        mock.post(mocked_url('run'), text=mock_run(pipeline_id, docker_image=docker_image, identifier=run_id))
        expected_stdout = \
            "Docker image, instance type, instance disk and cmd template are required parameters " \
            "if pipeline was not provided.\n"
        actual_stdout = get_stdout_string(
            lambda: PipelineRunOperations.run(None, None, self.parameters, self.yes, self.run_params,
                                              None, None, None, self.cmd_template, self.timeout,
                                              self.quiet, None, None, False))
        assert actual_stdout == expected_stdout

    @requests_mock.mock()
    def test_run_with_parameters_info(self, mock):
        mock.get(mocked_url('pipeline/find?id={}'.format(pipeline_name)),
                 text=mock_pipe(pipeline_id, pipeline_name, raw_date, v_1))
        mock.get(mocked_url('pipeline/{}/parameters?version={}'.format(pipeline_id, v_1)),
                 text=mock_run_parameters(instance_disk, instance_type, param_name=parameter_name,
                                          param_value=parameter_value))
        mock.post(mocked_url("pipeline/{}/price?version={}".format(pipeline_id, v_1)),
                  text=mock_price(instance_disk, instance_type))
        mock.post(mocked_url('run'), text=mock_run(pipeline_id, docker_image=docker_image, identifier=run_id))
        expected_params = [build_run_parameter(name=parameter_name, value=parameter_value,
                                               parameter_type=parameter_type)]
        parameters = True
        actual_stdout = parse_parameters_info_stdout(
            get_stdout_string(
                lambda: PipelineRunOperations.run(pipeline_name, None, parameters, self.yes, self.run_params,
                                                  instance_disk, instance_type, docker_image,
                                                  self.cmd_template, self.timeout, self.quiet, None, None, False)))
        assert actual_stdout.pipeline_name == pipeline_name
        assert actual_stdout.version == v_1
        assert_run_parameter(actual_stdout.parameters, expected_params)


class TestStop(object):

    @patch('src.api.pipeline.Pipeline.get')
    @requests_mock.mock()
    def test_pipeline_stop(self, mock_get, mock):
        pipeline_run_model = build_run_model(identifier=run_id, status="RUNNING", version=v_1)
        mock.post(mocked_url('run/{}/status'.format(run_id)),
                  text=mock_pipeline_run(pipeline_run_model))
        pipeline_model = build_pipeline_model(identifier=pipeline_id, name=pipeline_name,
                                              created_date=server_date, repository=repo,
                                              current_version_name=v_1)
        mock_get.return_value = pipeline_model
        expected_stdout = 'RunID {} of "{}@{}" stopped\n'.format(run_id, pipeline_name, v_1)
        actual_stdout = get_stdout_string(lambda: PipelineRunOperations.stop(run_id, True))
        assert actual_stdout == expected_stdout


class TestTerminateNode(object):

    @requests_mock.mock()
    def test_terminate_node(self, mock):
        addresses = [build_address_model(address=node_ip, address_type="internalIP"),
                     build_address_model(address=node_name, address_type="Hostname")]
        labels = {
            "runid": run_id,
        }
        pod = build_pod_model(uid="pod_uid", name="pod_name", namespace="default", node_name=node_name, phase="running")
        pods = [mock_node_pods(pod)]
        node_1 = build_cluster_node_model(uid=uid_1, name=node_name, created=zoned_date,
                                          pipeline_run=mock_element(identifier="1", pipeline_name=pipeline_name),
                                          addresses=addresses, pods=pods, labels=labels, system_info=system_info)
        mock.get(mocked_url('cluster/node/{}/load'.format(node_name)), text=mock_node_load(node_1, addresses))
        mock.delete(mocked_url('cluster/node/{}'.format(node_name)), text=mock_node_load(node_1, addresses))

        expected_stdout = 'Node {} was terminated\n'.format(node_name)
        actual_stdout = get_stdout_string(lambda: terminate_node_calculation(node_name, True))
        assert actual_stdout == expected_stdout



