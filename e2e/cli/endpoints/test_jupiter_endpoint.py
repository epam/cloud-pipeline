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

from e2e.cli.endpoints.utils import *
import re


class TestJupiterEndpoints(object):
    pipeline_id = None
    run_ids = []
    nodes = set()
    state = FailureIndicator()
    test_case = ''

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')

    @classmethod
    def teardown_class(cls):
        for node in cls.nodes:
            terminate_node(node)
            logging.info("Node %s was terminated" % node)

    @pipe_test
    def test_jupiter_endpoint(self):
        self.test_case = 'TC-EDGE-12'
        run_id, node_name, result, message = run_test("library/jupyter-lab",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      endpoints_structure={
                                                          "JupyterLab": "pipeline-{run_id}-8888-0"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
        if not result:
            raise RuntimeError(message)

    @pipe_test
    def test_jupiter_endpoint_friendly_url(self):
        self.test_case = 'TC-EDGE-13'
        run_id, node_name, result, message = run_test("library/jupyter-lab",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      friendly_url='friendly',
                                                      endpoints_structure={
                                                          "JupyterLab": "friendly",
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
        if not result:
            raise RuntimeError(message)

    @pipe_test
    def test_jupiter_and_no_machine_endpoint_friendly_url(self):
        self.test_case = 'TC-EDGE-14'
        run_id, node_name, result, message = run_test("library/jupyter-lab",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      friendly_url='friendly',
                                                      no_machine=True,
                                                      endpoints_structure={
                                                          "JupyterLab": "friendly-JupyterLab",
                                                          "NoMachine": "friendly-NoMachine"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
        if not result:
            raise RuntimeError(message)
