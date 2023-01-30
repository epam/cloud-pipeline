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
import time

from .utils import run_test
from ..utils.pipeline_utils import terminate_node_with_retry


class TestJupiterEndpoints(object):

    nodes = set()
    test_case = ''

    @classmethod
    def teardown_class(cls):
        for node in cls.nodes:
            terminate_node_with_retry(node)
            logging.info("Node %s was terminated" % node)

    def test_jupiter_endpoint(self):
        self.test_case = 'TC-EDGE-12'
        run_id, node_name = run_test("library/jupyter-lab",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     endpoints_structure={
                                         "JupyterLab": "pipeline-{run_id}-8888-0"
                                     })
        self.nodes.add(node_name)

    def test_jupiter_endpoint_friendly_url(self):
        self.test_case = 'TC-EDGE-13'
        run_id, node_name = run_test("library/jupyter-lab",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url='friendly',
                                     endpoints_structure={
                                         "JupyterLab": "friendly",
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_jupiter_and_no_machine_endpoint_friendly_url(self):
        self.test_case = 'TC-EDGE-14'
        run_id, node_name = run_test("library/jupyter-lab",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url='friendly',
                                     no_machine=True,
                                     endpoints_structure={
                                         "JupyterLab": "friendly-JupyterLab",
                                         "NoMachine": "friendly-NoMachine"
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)
