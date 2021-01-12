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


class TestRStudioEndpoints(object):
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
    def test_custom_domain_rstudio_endpoint(self):
        self.test_case = 'TC-EDGE-25'
        run_id, node_name, result, message = run_test("pavel-silin/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://pipeline-{run_id}-8788-0.jobs.cloud-pipeline.com:\\d*"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
        if not result:
            raise RuntimeError(message)

    @pipe_test
    def test_custom_domain_rstudio_with_friendly_path(self):
        self.test_case = 'TC-EDGE-26'
        run_id, node_name, result, message = run_test("pavel-silin/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      friendly_url="rstudio",
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://rstudio.jobs.cloud-pipeline.com:\\d*/"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
        if not result:
            raise RuntimeError(message)


    # @pipe_test
    # def test_custom_domain_rstudio_endpoint_friendly_domain(self):
    #     self.test_case = 'TC-EDGE-27'
    #     run_id, node_name, result, message = run_test("pavel-silin/rstudio",
    #                                               "echo {test_case} && /start.sh".format(test_case=self.test_case),
    #                                               friendly_url="friendly1.com",
    #                                               url_checker=lambda u, p: u.startswith(p),
    #                                               endpoints_structure={
    #                                                   "RStudio": "https://friendly1.com"
    #                                               })
    #     self.run_ids.append(run_id)
    #     self.nodes.add(node_name)
    #     if not result:
    #         raise RuntimeError(message)
    #
    @pipe_test
    def test_custom_domain_rstudio_friendly_domain_with_path(self):
        self.test_case = 'TC-EDGE-28'
        run_id, node_name, result, message = run_test("pavel-silin/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      friendly_url="friendly2.com/asdf",
                                                      check_access=False,
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://friendly2.com.*/asdf"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
        if not result:
            raise RuntimeError(message)

    @pipe_test
    def test_custom_domain_rstudio_and_no_machine_endpoint(self):
        self.test_case = 'TC-EDGE-29'
        run_id, node_name, result, message = run_test("pavel-silin/rstudio",
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
        if not result:
            raise RuntimeError(message)

    @pipe_test
    def test_custom_domain_rstudio_and_no_machine_endpoint_friendly_path(self):
        self.test_case = 'TC-EDGE-30'
        run_id, node_name, result, message = run_test("pavel-silin/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      check_access=True,
                                                      no_machine=True,
                                                      friendly_url="asdf",
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://asdf-RStudio.jobs.cloud-pipeline.com:.*/",
                                                          "NoMachine": ".*asdf-NoMachine",
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
        if not result:
            raise RuntimeError(message)

    @pipe_test
    def test_custom_domain_rstudio_spark_no_machine_endpoint_friendly_path(self):
        self.test_case = 'TC-EDGE-30'
        run_id, node_name, result, message = run_test("pavel-silin/rstudio",
                                                      "echo {test_case} && /start.sh".format(test_case=self.test_case),
                                                      check_access=True,
                                                      no_machine=True,
                                                      spark=True,
                                                      friendly_url="asdf",
                                                      url_checker=lambda u, p: bool(re.compile(p).match(u)),
                                                      endpoints_structure={
                                                          "RStudio": "https://asdf-RStudio.jobs.cloud-pipeline.com:.*/",
                                                          "NoMachine": ".*asdf-NoMachine",
                                                          "SparkUI": ".*asdf-SparkUI"
                                                      })
        self.run_ids.append(run_id)
        self.nodes.add(node_name)
        if not result:
            raise RuntimeError(message)
