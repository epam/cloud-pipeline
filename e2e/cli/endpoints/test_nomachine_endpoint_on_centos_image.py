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


class TestNoMachineEndpoints(object):
    pipeline_id = None
    run_id = None
    node = None
    pipeline_name = 'test-nomachine-endpoints-centos'
    state = FailureIndicator()
    test_case = 'TC-EDGE-1'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')

    @classmethod
    def teardown_class(cls):
        terminate_node(cls.node)
        logging.info("Node %s was terminated" % cls.node)
        stop_pipe(cls.run_id)

    @pipe_test
    def test_nomachine_endpoint_on_centos_image(self):
        run_id, node_name = run("library/centos:7", no_machine=True)
        endpoints_structure = {
            "NoMachine": "pipeline-" + run_id + "-8089-0"
        }
        self.run_id = run_id
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

