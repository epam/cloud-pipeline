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

MAX_REP_COUNT = 120


class TestStartStopPipe(object):

    pipeline_id = None
    node_name = None
    latest_run_id = None
    state = FailureIndicator()
    test_case = 'EPMCMBIBPC-169'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = "start_stop_pipe_test"
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            run_id = run_pipe(pipeline_name, "-id", "17")[0]
            logging.info("Pipeline run with ID {}.".format(run_id))
            wait_for_required_status("SCHEDULED", run_id, MAX_REP_COUNT)
            wait_for_instance_creation(run_id, MAX_REP_COUNT)
            logging.info("Instance {} created.".format(run_id))
            stop_pipe(run_id)
            wait_for_required_status("STOPPED", run_id, MAX_REP_COUNT)
            sleep(60)
            logging.info("Pipeline {} stopped.".format(run_id))

            node_state = wait_for_node_up(run_id, MAX_REP_COUNT)
            cls.node_name = get_node_name_from_cluster_state(node_state)
            logging.info("Used node {}.".format(cls.node_name))
            cls.latest_run_id = run_id
        except BaseException as e:
            logging.error(e.message)
            cls.teardown_class()
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        terminate_node(cls.node_name)
        logging.info("Node {} was terminated".format(cls.node_name))

        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))
            wait_for_instance_termination(cls.latest_run_id, 150)

    def test_created_only_one_node(self):
        try:
            assert len(get_cluster_state_for_run_id(self.latest_run_id)) == 1,\
                "Cluster should have only one extra node."
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

    def test_node_should_have_latest_id(self):
        try:
            node_runid_label = get_runid_label(self.node_name)
            assert node_runid_label == self.latest_run_id, \
                "The runid label of the node must be equal to the runid of the pipeline."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))
