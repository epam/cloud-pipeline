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

    def test_nomachine_endpoint_on_centos_image(self):
        self.test_case = 'TC-EDGE-1'
        run_id, node_name = run_test("library/centos:7",
                                     "echo {test_case} && sleep infinity".format(test_case=self.test_case),
                                     no_machine=True,
                                     endpoints_structure={
                                         "NoMachine": "pipeline-{run_id}-8089-0"
                                     })
        self.nodes.add(node_name)

    def test_spark_endpoint_on_centos_image(self):
        self.test_case = 'TC-EDGE-15'
        run_id, node_name = run_test("library/centos:7",
                                     "echo {test_case} && sleep infinity".format(test_case=self.test_case),
                                     spark=True,
                                     endpoints_structure={
                                         "SparkUI": "pipeline-{run_id}-8088-1000"
                                     })
        self.nodes.add(node_name)

    def test_spark_and_no_machine_endpoint_on_centos_image(self):
        self.test_case = 'TC-EDGE-16'
        run_id, node_name = run_test("library/centos:7",
                                     "echo {test_case} && sleep infinity".format(test_case=self.test_case),
                                     spark=True,
                                     no_machine=True,
                                     endpoints_structure={
                                         "NoMachine": "pipeline-{run_id}-8089-0",
                                         "SparkUI": "pipeline-{run_id}-8088-1000"
                                     })
        self.nodes.add(node_name)
