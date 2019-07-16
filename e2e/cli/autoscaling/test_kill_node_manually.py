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


class TestTerminateNodeManually(object):

    node_name = None
    run_id = None
    pipeline_id = None
    state = FailureIndicator()
    test_case = 'EPMCMBIBPC-101'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = "kill_node_manually_test"
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            cls.run_id = run_pipe(pipeline_name, "-id", "14")[0]
            logging.info("Pipeline run with ID {}.".format(cls.run_id))
            wait_for_required_status("RUNNING", cls.run_id, MAX_REP_COUNT)
            stop_pipe(cls.run_id)
            wait_for_required_status("STOPPED", cls.run_id, MAX_REP_COUNT)
            sleep(60)
            logging.info("Pipeline {} stopped.".format(cls.run_id))

            cls.node_name = get_node_name(cls.run_id)
            logging.info("Used node {}.".format(cls.node_name))

            terminate_node(cls.node_name)
            wait_for_node_termination(cls.node_name, MAX_REP_COUNT)
            logging.info("Node {} terminated.".format(cls.node_name))
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

    def test_node_should_be_terminated(self):
        try:
            node = view_cluster_for_node(self.node_name)
            assert not node, "Node {} is not terminated".format(self.node_name)
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    def test_instance_should_be_terminated(self):
        try:
            wait_for_instance_termination(self.run_id, MAX_REP_COUNT)
            instance = describe_instance(self.run_id)
            assert not instance, 'The instance {} was not terminated.'.format(self.run_id)
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
