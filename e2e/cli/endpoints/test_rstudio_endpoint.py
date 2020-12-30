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
    node = None
    state = FailureIndicator()
    test_case = ''

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')

    @classmethod
    def teardown_class(cls):
        terminate_node(cls.node)
        logging.info("Node %s was terminated" % cls.node)
        for run_id in cls.run_ids:
            stop_pipe(run_id)

    @pipe_test
    def test_rstudio_endpoint(self):
        self.test_case = 'TC-EDGE-4'
        run_id, node_name = run("library/rstudio", "/start.sh", False)
        endpoints_structure = {
            "RStudio": "pipeline-" + run_id + "-8788-0"
        }
        self.run_ids.append(run_id)
        self.node = node_name
        urls = get_endpoint_urls(run_id)
        check_for_number_of_endpoints(urls, 1)
        for name in urls:
            url = urls[name]
            structure_is_fine = check_service_url_structure(url, endpoints_structure[name])
            if not structure_is_fine:
                raise RuntimeError("service url: {}, has wrong format.".format(url))
            is_accessible = follow_service_url(url, 100)
            if not is_accessible:
                raise RuntimeError("service url: {}, is not accessible.".format(url))
        stop_pipe(run_id)

    @pipe_test
    def test_rstudio_and_no_machine_endpoint(self):
        self.test_case = 'TC-EDGE-5'
        run_id, node_name = run("library/rstudio", "/start.sh", True)

        endpoints_structure = {
            "RStudio": "pipeline-" + run_id + "-8788-0",
            "NoMachine": "pipeline-" + run_id + "-8089-0"
        }
        self.run_ids.append(run_id)
        self.node = node_name
        urls = get_endpoint_urls(run_id)
        check_for_number_of_endpoints(urls, 2)
        for name in urls:
            url = urls[name]
            structure_is_fine = check_service_url_structure(url, endpoints_structure[name])
            if not structure_is_fine:
                raise RuntimeError("service url: {}, has wrong format.".format(url))
            is_accessible = follow_service_url(url, 100)
            if not is_accessible:
                raise RuntimeError("service url: {}, is not accessible.".format(url))

    @pipe_test
    def test_rstudio_endpoint_friendly_url(self):
        self.test_case = 'TC-EDGE-6'
        run_id, node_name = run("library/rstudio", "/start.sh", no_machine=False, friendly_url='friendly1')

        endpoints_structure = {
            "RStudio": "friendly1-RStudio",
        }
        self.run_ids.append(run_id)
        self.node = node_name
        urls = get_endpoint_urls(run_id)
        check_for_number_of_endpoints(urls, 1)
        for name in urls:
            url = urls[name]
            structure_is_fine = check_service_url_structure(url, endpoints_structure[name])
            if not structure_is_fine:
                raise RuntimeError("service url: {}, has wrong format.".format(url))
            is_accessible = follow_service_url(url, 100)
            if not is_accessible:
                raise RuntimeError("service url: {}, is not accessible.".format(url))

    @pipe_test
    def test_rstudio_and_no_machine_endpoint_friendly_url(self):
        self.test_case = 'TC-EDGE-7'
        run_id, node_name = run("library/rstudio", "/start.sh", no_machine=True, friendly_url='friendly2')

        endpoints_structure = {
            "RStudio": "friendly2-RStudio",
            "NoMachine": "friendly2-NoMachine"
        }
        self.run_ids.append(run_id)
        self.node = node_name
        urls = get_endpoint_urls(run_id)
        check_for_number_of_endpoints(urls, 2)
        for name in urls:
            url = urls[name]
            structure_is_fine = check_service_url_structure(url, endpoints_structure[name])
            if not structure_is_fine:
                raise RuntimeError("service url: {}, has wrong format.".format(url))
            is_accessible = follow_service_url(url, 100)
            if not is_accessible:
                raise RuntimeError("service url: {}, is not accessible.".format(url))

    # @pipe_test
    # def test_rstudio_endpoint_friendly_domain_url(self):
    #     self.test_case = 'TC-EDGE-8'w
    #     run_id, node_name = run("library/rstudio", "/start.sh", no_machine=False, friendly_url='friendly1.com')
    #     check_for_access = False
    #     endpoints_structure = {
    #         "RStudio": "friendly1.com",
    #     }
    #     self.run_ids.append(run_id)
    #     self.node = node_name
    #     urls = get_endpoint_urls(run_id)
    #     check_for_number_of_endpoints(urls, 1)
    #     for name in urls:
    #         url = urls[name]
    #         structure_is_fine = check_service_url_structure(url, endpoints_structure[name], checker=lambda u, p: url.startswith('https://{}'.format(p)))
    #         if not structure_is_fine:
    #             raise RuntimeError("service url: {}, has wrong format.".format(url))
    #         is_accessible = not check_for_access or follow_service_url(url, 100)
    #         if not is_accessible:
    #             raise RuntimeError("service url: {}, is not accessible.".format(url))
    #
    # @pipe_test
    # def test_rstudio_and_no_machine_endpoint_friendly_domain_url(self):
    #     self.test_case = 'TC-EDGE-9'
    #     run_id, node_name = run("library/rstudio", "/start.sh", no_machine=True, friendly_url='friendly2.com')
    #     check_for_access = False
    #     endpoints_structure = {
    #         "RStudio": "https://friendly2.com/.*RStudio",
    #         "NoMachine": "https://friendly2.com/.*NoMachine"
    #     }
    #     self.run_ids.append(run_id)
    #     self.node = node_name
    #     urls = get_endpoint_urls(run_id)
    #     check_for_number_of_endpoints(urls, 2)
    #     for name in urls:
    #         url = urls[name]
    #         structure_is_fine = check_service_url_structure(url, endpoints_structure[name], checker=lambda u, p: re.compile(p).match(u))
    #         if not structure_is_fine:
    #             raise RuntimeError("service url: {}, has wrong format.".format(url))
    #         is_accessible = not check_for_access or follow_service_url(url, 100)
    #         if not is_accessible:
    #             raise RuntimeError("service url: {}, is not accessible.".format(url))

    @pipe_test
    def test_rstudio_and_no_machine_endpoint_friendly_domain_and_endpoint_url(self):
        self.test_case = 'TC-EDGE-10'
        check_for_access = False
        run_id, node_name = run("library/rstudio", "/start.sh", no_machine=True, friendly_url='friendly3.com/friendly')

        endpoints_structure = {
            "RStudio": "https://friendly3.com.*/friendly-RStudio",
            "NoMachine": "https://friendly3.com.*/friendly-NoMachine"
        }
        self.run_ids.append(run_id)
        self.node = node_name
        urls = get_endpoint_urls(run_id)
        check_for_number_of_endpoints(urls, 2)
        for name in urls:
            url = urls[name]
            structure_is_fine = check_service_url_structure(url, endpoints_structure[name], checker=lambda u, p: bool(re.compile(p).match(u)))
            if not structure_is_fine:
                raise RuntimeError("service url: {}, has wrong format.".format(url))
            is_accessible = not check_for_access or follow_service_url(url, 100)
            if not is_accessible:
                raise RuntimeError("service url: {}, is not accessible.".format(url))

    @pipe_test
    def test_rstudio_endpoint_friendly_domain_and_endpoint_url(self):
        self.test_case = 'TC-EDGE-11'
        run_id, node_name = run("library/rstudio", "/start.sh", no_machine=False, friendly_url='friendly4.com/friendly')
        check_for_access = False
        endpoints_structure = {
            "RStudio": "friendly4.com.*/friendly",
        }
        self.run_ids.append(run_id)
        self.node = node_name
        urls = get_endpoint_urls(run_id)
        check_for_number_of_endpoints(urls, 1)
        for name in urls:
            url = urls[name]
            structure_is_fine = check_service_url_structure(url, endpoints_structure[name], checker=lambda u, p: url.startswith('https://{}'.format(p)))
            if not structure_is_fine:
                raise RuntimeError("service url: {}, has wrong format.".format(url))
            is_accessible = not check_for_access or follow_service_url(url, 100)
            if not is_accessible:
                raise RuntimeError("service url: {}, is not accessible.".format(url))
