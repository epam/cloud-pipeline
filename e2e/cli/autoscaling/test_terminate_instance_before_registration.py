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

import logging

import pytest

from common_utils.entity_managers import PipelineManager
from utils.pipeline_utils import *

MAX_REP_COUNT = 150


class TestTerminateInstanceBeforeKubeRegistration(object):

    pipeline_id = None
    run_id = None
    state = FailureIndicator()
    test_case = "EPMCMBIBPC-176"

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = "test_terminate_instance_before_registration"
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            run_id = run_pipe(pipeline_name, "-id", "11")[0]
            cls.run_id = run_id
            logging.info("Pipeline run with ID {}.".format(cls.run_id))
            wait_for_required_status("SCHEDULED", run_id, MAX_REP_COUNT)
            wait_for_instance_creation(run_id, MAX_REP_COUNT)
            logging.info("Instance {} created.".format(run_id))
            terminate_instance(run_id)
            wait_for_instance_termination(run_id, MAX_REP_COUNT)
            logging.info("Instance {} terminated.".format(run_id))
        except BaseException as e:
            logging.error(e.message)
            cls.teardown_class()
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        node_name = get_node_name(cls.run_id)
        terminate_node(node_name)
        logging.info("Node {} was terminated".format(node_name))

        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))
            wait_for_instance_termination(cls.run_id, 150)

    @pytest.mark.run(order=2)
    def test_pipe_should_still_wait(self):
        try:
            status = get_pipe_status(self.run_id)
            if status != "SUCCESS":
                status = wait_for_required_status("RUNNING", self.run_id, 400, validation=False)
            assert status == "RUNNING" or status == "SUCCESS", \
                "Pipeline should wait for node registration. Current status: {}".format(status)
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    @pytest.mark.run(order=1)
    def test_new_node_should_be_created(self):
        try:
            wait_for_node_up(self.run_id, 400, validation=False)
            node_name = get_node_name(self.run_id)
            logging.info("Node {} in use.".format(node_name))
            assert len(get_cluster_state_for_run_id(self.run_id)) == 1, "Cluster should have exact one extra node."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    @pytest.mark.run(order=3)
    def test_cluster_should_not_have_node_without_label(self):
        try:
            node_name = get_node_name(self.run_id)
            assert len(get_nodes_without_labels(node_name)) == 0, "Cluster should not have nodes without labels."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))
