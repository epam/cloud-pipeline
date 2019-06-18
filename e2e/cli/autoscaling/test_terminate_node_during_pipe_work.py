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

MAX_REP_COUNT = 100


class TestTerminateNodeDuringPipelineWork(object):

    pipeline_id = None
    run_id = None
    node_name = None
    state = FailureIndicator()
    test_case = 'EPMCMBIBPC-166'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = "terminate_node_during_pipe_works_test"
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            run_id = run_pipe(pipeline_name, "-id", "18")[0]
            cls.run_id = run_id
            logging.info("Pipeline run with ID {}.".format(run_id))

            node_state = wait_for_node_up(run_id, MAX_REP_COUNT)
            cls.node_name = get_node_name_from_cluster_state(node_state)
            logging.info("Used node {}.".format(cls.node_name))

            terminate_node(cls.node_name)
            wait_for_node_termination(cls.node_name, MAX_REP_COUNT)
            logging.info("Node {} was terminated.".format(cls.node_name))
        except BaseException as e:
            logging.error(e.message)
            cls.teardown_class()
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))
            wait_for_instance_termination(cls.run_id, 150)

    def test_pipeline_should_be_stopped(self):
        try:
            status = wait_for_required_status("STOPPED", self.run_id, MAX_REP_COUNT, validation=False)
            assert status == "STOPPED", "Pipeline should be stopped."
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
