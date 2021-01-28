# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

from ..common_utils.entity_managers import PipelineManager
from ..common_utils.test_utils import format_name
from ..utils.pipeline_utils import *

MAX_REP_COUNT = 300


class TestTerminateInstanceDuringPipelineWork(object):

    pipeline_id = None
    run_id = None
    node_name = None
    state = FailureIndicator()
    test_case = 'TC-SCALING-8'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = format_name("terminate_instance_during_pipe_work_test")
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            run_id = run_pipe(pipeline_name)[0]
            cls.run_id = run_id
            logging.info("Pipeline run with ID {}.".format(run_id))
            node_state = wait_for_node_up(run_id, MAX_REP_COUNT)
            cls.node_name = get_node_name_from_cluster_state(node_state)
            logging.info("Used node {}.".format(cls.node_name))

            terminate_instance(run_id)
            wait_for_instance_termination(run_id, MAX_REP_COUNT)
            logging.info("Instance {} was terminated.".format(run_id))
        except BaseException as e:
            logging.error(e.message)
            cls.teardown_class()
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        terminate_node(cls.node_name)

        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))
            wait_for_instance_termination(cls.run_id, 30)

    def test_pipe_should_fail(self):
        try:
            status = wait_for_required_status("FAILURE", self.run_id, MAX_REP_COUNT, validation=False)
            assert status == "FAILURE", "Pipeline should be failed."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    def test_node_should_be_terminated(self):
        try:
            wait_for_node_termination(self.node_name, MAX_REP_COUNT)
            node = view_cluster_for_node(self.node_name)
            assert not node, "Node {} should be terminated.".format(self.node_name)
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    def test_cluster_should_not_have_node_without_label(self):
        try:
            assert len(get_nodes_without_labels(self.node_name)) == 0, "Cluster should not have nodes without labels."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))
