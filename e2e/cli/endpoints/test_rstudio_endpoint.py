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
import re
import time

import pytest

from .utils import run_test
from ..utils.pipeline_utils import terminate_node_with_retry


class TestRStudioEndpoints(object):

    nodes = set()
    test_case = ''

    @classmethod
    def teardown_class(cls):
        for node in cls.nodes:
            terminate_node_with_retry(node)
            logging.info("Node %s was terminated" % node)

    def test_rstudio_endpoint(self):
        self.test_case = 'TC-EDGE-4'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     endpoints_structure={
                                         "RStudio": "pipeline-{run_id}-8788-0"
                                     })
        self.nodes.add(node_name)

    def test_rstudio_and_no_machine_endpoint(self):
        self.test_case = 'TC-EDGE-5'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     endpoints_structure={
                                         "RStudio": "pipeline-{run_id}-8788-0",
                                         "NoMachine": "pipeline-{run_id}-8089-0"
                                     }, no_machine=True)
        self.nodes.add(node_name)

    def test_rstudio_endpoint_friendly_url(self):
        self.test_case = 'TC-EDGE-6'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url="friendly",
                                     endpoints_structure={
                                         "RStudio": "friendly"
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_and_no_machine_endpoint_friendly_url(self):
        self.test_case = 'TC-EDGE-7'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url="friendly",
                                     no_machine=True,
                                     endpoints_structure={
                                         "RStudio": "friendly-RStudio",
                                         "NoMachine": "friendly-NoMachine"
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_endpoint_friendly_domain_url(self):
        self.test_case = 'TC-EDGE-8'
        pytest.skip("Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url="friendly.com",
                                     url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                     endpoints_structure={
                                         "RStudio": "https://friendly.com:\\d*",
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_and_no_machine_endpoint_friendly_domain_url(self):
        self.test_case = 'TC-EDGE-9'
        pytest.skip("Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url="friendly.com",
                                     url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                     endpoints_structure={
                                         "RStudio": "https://friendly.com.*/RStudio",
                                         "NoMachine": "https://friendly.com.*/NoMachine"
                                     }, no_machine=True)
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_endpoint_friendly_domain_and_endpoint_url(self):
        self.test_case = 'TC-EDGE-10'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url="friendly.com/friendly",
                                     check_access=False,
                                     url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                     endpoints_structure={
                                         "RStudio": "https://friendly.com.*/friendly",
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_and_no_machine_endpoint_friendly_domain_and_endpoint_url(self):
        self.test_case = 'TC-EDGE-11'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url="friendly.com/friendly",
                                     check_access=False,
                                     no_machine=True,
                                     url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                     endpoints_structure={
                                         "RStudio": "https://friendly.com.*/friendly-RStudio",
                                         "NoMachine": "https://friendly.com.*/friendly-NoMachine"
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_spark_endpoints(self):
        self.test_case = 'TC-EDGE-17'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     spark=True,
                                     endpoints_structure={
                                         "RStudio": "pipeline-{run_id}-8788-0",
                                         "SparkUI": "pipeline-{run_id}-8088-1000",

                                     })
        self.nodes.add(node_name)

    def test_rstudio_spark_nomachine_endpoints(self):
        self.test_case = 'TC-EDGE-18'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     spark=True,
                                     no_machine=True,
                                     endpoints_structure={
                                         "RStudio": "pipeline-{run_id}-8788-0",
                                         "SparkUI": "pipeline-{run_id}-8088-1000",
                                         "NoMachine": "pipeline-{run_id}-8089-0"
                                     })
        self.nodes.add(node_name)

    def test_rstudio_spark_endpoints_friendly_url(self):
        self.test_case = 'TC-EDGE-19'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     spark=True,
                                     friendly_url="friendly",
                                     endpoints_structure={
                                         "RStudio": "friendly-RStudio",
                                         "SparkUI": "friendly-SparkUI",
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_spark_nomachine_endpoints_friendly_url(self):
        self.test_case = 'TC-EDGE-20'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     spark=True,
                                     no_machine=True,
                                     friendly_url="friendly",
                                     endpoints_structure={
                                         "RStudio": "friendly-RStudio",
                                         "SparkUI": "friendly-SparkUI",
                                         "NoMachine": "friendly-NoMachine"
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_spark_endpoints_friendly_domain_url(self):
        self.test_case = 'TC-EDGE-21'
        pytest.skip("Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     spark=True,
                                     friendly_url="friendly.com",
                                     url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                     endpoints_structure={
                                         "RStudio": "https://friendly.com.*/RStudio",
                                         "SparkUI": "https://friendly.com.*/SparkUI",
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_spark_no_machine_endpoint_friendly_domain_url(self):
        self.test_case = 'TC-EDGE-22'
        pytest.skip("Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     spark=True,
                                     friendly_url="friendly.com",
                                     url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                     endpoints_structure={
                                         "RStudio": "https://friendly.com.*/RStudio",
                                         "NoMachine": "https://friendly.com.*/NoMachine",
                                         "SparkUI": "https://friendly.com.*/SparkUI"
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_spark_endpoints_friendly_domain_and_url(self):
        self.test_case = 'TC-EDGE-23'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url="friendly.com/friendly",
                                     check_access=False,
                                     spark=True,
                                     url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                     endpoints_structure={
                                         "RStudio": "https://friendly.com.*/friendly-RStudio",
                                         "SparkUI": "https://friendly.com.*/friendly-SparkUI",
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)

    def test_rstudio_spark_no_machine_endpoint_friendly_domain_and_url(self):
        self.test_case = 'TC-EDGE-24'
        run_id, node_name = run_test("library/rstudio",
                                     "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                     friendly_url="friendly.com/friendly",
                                     check_access=False,
                                     spark=True,
                                     no_machine=True,
                                     url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                     endpoints_structure={
                                         "RStudio": "https://friendly.com.*/friendly-RStudio",
                                         "NoMachine": "https://friendly.com.*/friendly-NoMachine",
                                         "SparkUI": "https://friendly.com.*/friendly-SparkUI",
                                     })
        self.nodes.add(node_name)
        # Sleep 1 min to be sure that edge is reloaded
        time.sleep(60)
