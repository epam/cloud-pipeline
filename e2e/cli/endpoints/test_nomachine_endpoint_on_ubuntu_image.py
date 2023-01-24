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

from .utils import terminate_node_with_retry, run_test


class TestNoMachineEndpoints(object):

    nodes = set()
    test_case = ''

    @classmethod
    def teardown_class(cls):
        for node in cls.nodes:
            terminate_node_with_retry(node)
            logging.info("Node %s was terminated" % node)

    def test_nomachine_endpoint_on_ubuntu_16_image(self):
        self.test_case = 'TC-EDGE-2'
        run_id, node_name = run_test("library/ubuntu:16.04",
                                     "echo {test_case} && sleep infinity".format(test_case=self.test_case),
                                     no_machine=True,
                                     endpoints_structure={
                                         "NoMachine": "pipeline-{run_id}-8089-0"
                                     })
        self.nodes.add(node_name)

    def test_nomachine_endpoint_on_ubuntu_18_image(self):
        self.test_case = 'TC-EDGE-3'
        run_id, node_name = run_test("library/ubuntu:18.04",
                                     "echo {test_case} && sleep infinity".format(test_case=self.test_case),
                                     no_machine=True,
                                     endpoints_structure={
                                         "NoMachine": "pipeline-{run_id}-8089-0"
                                     })
        self.nodes.add(node_name)
