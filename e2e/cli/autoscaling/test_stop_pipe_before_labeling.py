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

MAX_REP_COUNT = 300


class TestStopPipelineBeforeLabeling(object):

    pipeline_id = None
    run_id = None
    pipeline_name = None
    node_name = None
    new_node_name = None  # should be equal node_name. needed to terminate instance if test failed
    new_run_id = None
    state = FailureIndicator()
    test_case = "EPMCMBIBPC-162"

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = "stop_pipe_before_labeling_test"
        cls.pipeline_name = pipeline_name
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            run_id = run_pipe(pipeline_name, "-id", "21")[0]
            cls.run_id = run_id
            logging.info("Pipeline run with ID {}.".format(cls.run_id))
            wait_for_required_status("SCHEDULED", run_id, MAX_REP_COUNT)
            instance = wait_for_instance_creation(run_id, MAX_REP_COUNT)
            private_ip = get_private_ip(instance)
            logging.info("Started to find node {}.".format(private_ip))
            node_state = wait_for_node_up_without_id(MAX_REP_COUNT * 5, private_ip)
            cls.node_name = get_node_name_from_cluster_state(node_state)
            logging.info("Node {} in use.".format(private_ip))
            stop_pipe(run_id)
            sleep(60)
            wait_for_required_status("STOPPED", run_id, MAX_REP_COUNT)
            logging.info("Pipeline {} stopped.".format(run_id))
        except BaseException as e:
            logging.error(e.message)
            cls.teardown_class()
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        terminate_node(cls.node_name)

        stop_pipe(cls.new_run_id)
        terminate_node(cls.new_node_name)

        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))
            wait_for_instance_termination(cls.run_id, 150)

    @pytest.mark.run(order=1)
    def test_node_should_have_label(self):
        try:
            node_state = wait_for_node_up(self.run_id, MAX_REP_COUNT, validation=False)
            assert node_state, "Cluster should have node with label {}.".format(self.run_id)
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    @pytest.mark.run(order=2)
    def test_node_should_reassign(self):
        try:
            run_id = run_pipe(self.pipeline_name, "-id", "21")[0]
            self.new_run_id = run_id
            logging.info("Pipeline run with ID {}.".format(run_id))
            node_state = wait_for_node_up(run_id, MAX_REP_COUNT)
            self.new_node_name = get_node_name_from_cluster_state(node_state)
            logging.info("new node {}".format(self.node_name))
            assert len(get_cluster_state_for_run_id(self.run_id)) == 0, \
                "Node with label {} should be reassigned.".format(self.run_id)
            assert len(get_cluster_state_for_run_id(self.new_run_id)) == 1,\
                "Cluster should have exact one node for run {}.".format(run_id)
        except BaseException as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    @pytest.mark.run(order=3)
    def test_cluster_should_not_have_node_without_label(self):
        try:
            assert len(get_nodes_without_labels(self.node_name)) == 0, "Cluster should not have nodes without labels."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))
