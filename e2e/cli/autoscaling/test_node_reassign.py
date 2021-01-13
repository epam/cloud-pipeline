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
import os

import pytest

from common_utils.entity_managers import PipelineManager
from common_utils.test_utils import format_name
from e2e.cli.utils.pipeline_utils import *

MAX_REP_COUNT = 120


class TestNodeReassign(object):

    first_node_name = None
    second_node_name = None
    pipeline_id = None
    first_run_id = None
    second_run_id = None
    state = FailureIndicator()
    test_case = 'EPMCMBIBPC-69'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = format_name("node_reassign_test")
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            run_id = run_pipe(pipeline_name, "-id", "16")[0]
            cls.first_run_id = run_id
            logging.info("Pipeline run with ID {}.".format(cls.first_run_id))
            wait_for_node_up(run_id, MAX_REP_COUNT)
            node_name = get_node_name(run_id)
            logging.info("Node {} appeared in cluster state.".format(node_name))
            cls.first_node_name = node_name
            wait_for_run_node_job(node_name, MAX_REP_COUNT)
            logging.info("Job name: {}".format(node_job_running(node_name)))

            stop_pipe(run_id)
            sleep(60)
            wait_for_required_status("STOPPED", run_id, MAX_REP_COUNT)
            logging.info("Pipeline {} stopped.".format(cls.first_run_id))
            wait_for_end_of_job(node_name, MAX_REP_COUNT)

            run_id = run_pipe(pipeline_name, "-id", "16")[0]
            cls.second_run_id = run_id
            logging.info("Pipeline {} launched".format(run_id))
            node_state = wait_for_node_up(run_id, MAX_REP_COUNT)
            node_name = get_node_name_from_cluster_state(node_state)
            logging.info("Node {} in use.".format(node_name))
            cls.second_node_name = node_name
        except BaseException as e:
            logging.error(e.message)
            cls.teardown_class()
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        terminate_node(cls.first_node_name)
        terminate_node(cls.second_node_name)

        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))
            wait_for_instance_termination(cls.first_run_id, 150)
            wait_for_instance_termination(cls.second_run_id, 150)

    def test_node_ip_should_be_the_same(self):
        try:
            assert self.first_node_name == self.second_node_name, "Node should be reassigned"
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    def test_cluster_should_have_one_node_with_latest_id(self):
        try:
            assert len(get_cluster_state_for_run_id(self.second_run_id)) == 1, \
                "Cluster should have only one extra node."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    def test_cluster_should_not_have_node_with_first_id(self):
        try:
            assert len(get_cluster_state_for_run_id(self.first_run_id)) == 0, \
                "Cluster should not have node with first runid."
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
