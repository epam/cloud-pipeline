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

from utils import *
import re


class TestRStudioEndpoints(object):
    pipeline_id = None
    run_ids = []
    nodes = set()
    state = FailureIndicator()
    custom_dns_swap_flags = {}
    test_case = ''

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        logging.info("Change endpoint settings for RStudio tool, make a SubDomain param is True")
        tool_info = get_tool_info("library/rstudio")
        for i in range(0, len(tool_info["endpoints"])):
            if '"customDNS":false' in tool_info["endpoints"][i]:
                cls.custom_dns_swap_flags[i] = True
                tool_info["endpoints"][i] = tool_info["endpoints"][i].replace('"customDNS":false', '"customDNS":true')
        update_tool_info(tool_info)


    @classmethod
    def teardown_class(cls):
        for node in cls.nodes:
            terminate_node_with_retry(node)
            logging.info("Node %s was terminated" % node)
        tool_info = get_tool_info("library/rstudio")
        logging.info("Change endpoint settings for RStudio tool, make a SubDomain param is False")
        for i in range(0, len(tool_info["endpoints"])):
            if i in cls.custom_dns_swap_flags:
                tool_info["endpoints"][i] = tool_info["endpoints"][i].replace('"customDNS":true', '"customDNS":false')
        update_tool_info(tool_info)

    @pipe_test
    def test_custom_domain_rstudio_endpoint(self):
        self.test_case = 'TC-EDGE-25'
        run_id, node_name = run_test("library/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://pipeline-{run_id}-8788-0.jobs.cloud-pipeline.com:\\d*"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)

    @pipe_test
    def test_custom_domain_rstudio_with_friendly_path(self):
        self.test_case = 'TC-EDGE-26'
        run_id, node_name = run_test("library/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      friendly_url="rstudio",
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://rstudio.jobs.cloud-pipeline.com:\\d*/"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)


    @pipe_test
    def test_custom_domain_rstudio_endpoint_friendly_domain(self):
        self.test_case = 'TC-EDGE-27'
        pytest.skip("Can't be run now, because pipe-cli can't configure friendly_url=friendly.com as a domain")
        run_id, node_name = run_test("library/rstudio",
                                                  "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                  friendly_url="friendly.com",
                                                  url_checker=lambda u, p: u == p,
                                                  endpoints_structure={
                                                      "RStudio": "https://friendly.com"
                                                  })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)


    @pipe_test
    def test_custom_domain_rstudio_friendly_domain_with_path(self):
        self.test_case = 'TC-EDGE-28'
        run_id, node_name = run_test("library/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      friendly_url="friendly.com/friendly",
                                                      check_access=False,
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://friendly.com.*/friendly"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)

    @pipe_test
    def test_custom_domain_rstudio_and_no_machine_endpoint(self):
        self.test_case = 'TC-EDGE-29'
        run_id, node_name = run_test("library/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      check_access=True,
                                                      no_machine=True,
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                           "RStudio": "https://pipeline-{run_id}-8788-0.jobs.cloud-pipeline.com:.*/",
                                                           "NoMachine": ".*pipeline-{run_id}-8089-0",
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)

    @pipe_test
    def test_custom_domain_rstudio_and_no_machine_endpoint_friendly_path(self):
        self.test_case = 'TC-EDGE-30'
        run_id, node_name = run_test("library/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      check_access=True,
                                                      no_machine=True,
                                                      friendly_url="friendly",
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://friendly-RStudio.jobs.cloud-pipeline.com:.*/",
                                                          "NoMachine": ".*friendly-NoMachine",
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)

    @pipe_test
    def test_custom_domain_rstudio_spark_no_machine_endpoint_friendly_path(self):
        self.test_case = 'TC-EDGE-31'
        run_id, node_name = run_test("library/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      check_access=True,
                                                      no_machine=True,
                                                      spark=True,
                                                      friendly_url="friendly",
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://friendly-RStudio.jobs.cloud-pipeline.com:.*/",
                                                          "NoMachine": ".*friendly-NoMachine",
                                                          "SparkUI": ".*friendly-SparkUI"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
