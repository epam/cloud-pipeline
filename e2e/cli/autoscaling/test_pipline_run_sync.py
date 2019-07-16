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


class TestPipelineRunSync(object):

    test_case = "EPMCMBIBPC-1071"
    state = FailureIndicator()
    pipeline_id = None
    pipeline_name = None

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        cls.pipeline_name = "test_pipeline_run_sync"
        cls.pipeline_id = PipelineManager.create(cls.pipeline_name)
        logging.info("Pipeline {} with ID {} created.".format(cls.pipeline_name, cls.pipeline_id))

    @classmethod
    def teardown_class(cls):
        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline {} deleted".format(cls.pipeline_id))

    def test_pipe_run_sync(self):
        try:
            status = run_pipe(self.pipeline_name, "-id", "22", "--sync")[1]
            assert status == 'SUCCESS'
        except BaseException as e:
            logging.info("Case {} failed!".format(self.test_case))
            self.state.failure = True
            pytest.fail("Test case {} failed.\n{}".format(self.test_case, e.message))
