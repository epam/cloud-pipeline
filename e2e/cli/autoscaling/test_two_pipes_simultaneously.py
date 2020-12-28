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
from e2e.cli.utils.pipeline_utils import *

MAX_REP_COUNT = 120


class TestTwoPipesRunSimultaneously(object):

    pipeline_id = None
    first_run_id = None
    second_run_id = None
    first_node_name = None
    second_node_name = None
    state = FailureIndicator()
    test_case = 'EPMCMBIBPC-168'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = "two_pipes_simultaneously_test"
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            first_run_id = run_pipe(pipeline_name, "-id", "19")[0]
            cls.first_run_id = first_run_id
            logging.info("Pipeline run with ID {}.".format(first_run_id))

            second_run_id = run_pipe(pipeline_name, "-id", "19")[0]
            cls.second_run_id = second_run_id
            logging.info("Pipeline run with ID {}.".format(second_run_id))

            wait_for_required_status("RUNNING", first_run_id, MAX_REP_COUNT)
            wait_for_required_status("RUNNING", second_run_id, MAX_REP_COUNT)

            cls.first_node_name = get_node_name(first_run_id)
            logging.info("Node {} in use.".format(cls.first_node_name))

            cls.second_node_name = get_node_name(second_run_id)
            logging.info("Node {} in use.".format(cls.second_node_name))
        except BaseException as e:
            logging.error(e.message)
            cls.teardown_class()
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        stop_pipe(cls.first_run_id)
        stop_pipe(cls.second_run_id)

        sleep(60)

        terminate_node(cls.first_node_name)
        logging.info("Node {} was terminated".format(cls.first_node_name))
        terminate_node(cls.second_node_name)
        logging.info("Node {} was terminated".format(cls.second_node_name))

        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))
            wait_for_instance_termination(cls.first_run_id, 150)
            wait_for_instance_termination(cls.second_run_id, 150)

    def test_should_be_created_two_nodes(self):
        try:
            cluster_state_for_first_node = get_cluster_state_for_run_id(self.first_run_id)
            assert len(cluster_state_for_first_node) == 1, "Cluster should have exact one node for run id {}."\
                .format(self.first_run_id)
            cluster_state_for_second_node = get_cluster_state_for_run_id(self.second_run_id)
            assert len(cluster_state_for_second_node) == 1, "Cluster should have exact one node for run id {}."\
                .format(self.second_run_id)
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    def test_node_should_have_correct_labels(self):
        try:
            first_runid_label = get_runid_label(self.first_node_name)
            assert self.first_run_id == first_runid_label, "Node {} has incorrect label.".format(self.first_node_name)
            second_runid_label = get_runid_label(self.second_node_name)
            assert self.second_run_id == second_runid_label, "Node {} has incorrect label.".format(self.second_node_name)
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    def test_cluster_should_not_have_node_without_label(self):
        try:
            assert len(get_nodes_without_labels(self.first_node_name)) == 0, \
                "Cluster should not have nodes without labels."
            assert len(get_nodes_without_labels(self.second_node_name)) == 0, \
                "Cluster should not have nodes without labels."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))
