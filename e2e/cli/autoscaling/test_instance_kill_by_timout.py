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
import logging

import pytest

from common_utils.entity_managers import PipelineManager
from utils.pipeline_utils import *

MAX_REP_COUNT = 600
DATA_TIME_FORMAT = "%H:%M:%S"


class TestTerminateNodeByTimeout(object):

    run_id = None
    pipeline_id = None
    state = FailureIndicator()
    test_case = "EPMCMBIBPC-98"

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename='tests.log', level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        pipeline_name = "instance_kill_by_timeout_test"
        cls.pipeline_id = PipelineManager.create(pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(pipeline_name, cls.pipeline_id))

        try:
            cls.run_id = run_pipe(pipeline_name, "-id", "13")[0]
            logging.info("Pipeline run with ID {}.".format(cls.run_id))
        except BaseException as e:
            cls.teardown_class()
            raise RuntimeError(e.message)

    @classmethod
    def teardown_class(cls):
        node_name = get_node_name(cls.run_id)
        terminate_node(node_name)

        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))
            wait_for_instance_termination(cls.run_id, 30)

    @pytest.mark.run(order=1)
    def test_pipeline_should_be_completed(self):
        try:
            wait_for_required_status("SUCCESS", self.run_id, MAX_REP_COUNT, validation=False)
            assert get_pipe_status(self.run_id) == "SUCCESS", "Pipeline should be completed with status 'SUCCESS'"
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    @pytest.mark.run(order=2)
    def test_node_should_be_terminated(self):
        try:
            node_name = get_node_name(self.run_id)
            if not node_name:
                pytest.fail("Cannot find node for run id {}".format(self.run_id))
            logging.info("Node {} in use.".format(node_name))
            if self.__wait_timeout(self.run_id):
                node = view_cluster_for_node(node_name)
                assert not node, "Node {} is not terminated".format(node_name)
            else:
                self.state.failure = True
                pytest.fail("Can not get correct waiting time.")
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    @pytest.mark.run(order=3)
    def test_instance_should_be_terminated(self):
        try:
            instance = describe_instance(self.run_id)
            assert not instance, 'The instance {} was not terminated.'.format(self.run_id)
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    @pytest.mark.run(order=4)
    def test_cluster_should_not_have_node_without_label(self):
        try:
            node_name = get_node_name(self.run_id)
            assert len(get_nodes_without_labels(node_name)) == 0, "Cluster should not have nodes without labels."
        except AssertionError as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))

    @staticmethod
    def __wait_timeout(run_id):
        pipe_info = view_runs(run_id, "-nd")
        if "Started" in pipe_info and "Completed" in pipe_info:
            pipeline_worked_time = (dt.datetime.strptime(pipe_info["Completed"], DATA_TIME_FORMAT)
                                    - dt.datetime.strptime(pipe_info["Started"], DATA_TIME_FORMAT)).total_seconds()
        else:
            return None
        waiting_time = 3600 - pipeline_worked_time
        if waiting_time > 3600:
            logging.error("Pipeline worked time id negative. Started time: {}, Completed time: {}"
                          .format(pipe_info["Started"], pipe_info["Completed"]))
            waiting_time = 3600
        if waiting_time < 0:
            waiting_time = 1
        logging.info("Start waiting for node termination: {} sec.".format(waiting_time))
        sleep(waiting_time)
        return waiting_time
