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

from e2e.cli.common_utils.entity_managers import PipelineManager
from e2e.cli.utils.pipeline_utils import *
import utils


class TestNoMachineEndpoints(object):
    pipeline_id = None
    run_id = None
    node = None
    pipeline_name = 'test_nomachine_endpoints_centos'
    state = FailureIndicator()
    test_case = 'EPMCMBIBPC-0000'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        cls.pipeline_id = PipelineManager.create(cls.pipeline_name)
        logging.info("Pipeline %s with ID %s created." % (cls.pipeline_name, cls.pipeline_id))

    @classmethod
    def teardown_class(cls):
        terminate_node(cls.node)
        logging.info("Node %s was terminated" % cls.node)

        # if not cls.state.failure:
        PipelineManager.delete(cls.pipeline_id)
        logging.info("Pipeline %s deleted" % cls.pipeline_id)
        wait_for_instance_termination(cls.run_id, 150)

    @pipe_test
    def test_nomachine_endpoint_on_centos_image(self):
        run_id, node_name = utils.run_pipeline(self.pipeline_name,
                                               "cp-docker-registry.default.svc.cluster.local:31443/library/centos:latest",
                                               "CP_CAP_LIMIT_MOUNTS 'None' CP_CAP_DESKTOP_NM 'boolean?true'")
        self.run_id = run_id
        self.node = node_name
        urls = get_endpoint_urls(run_id)
        check_for_number_of_enpoints(urls, 1)
        for name, url in urls:
            is_fine = follow_service_url(url)
            if not is_fine:
                raise RuntimeError("service url: {}, is not accessible.".format(url))
        stop_pipe(run_id)

