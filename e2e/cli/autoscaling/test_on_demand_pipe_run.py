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

from ..common_utils.entity_managers import PipelineManager
from ..utils.pipeline_utils import *
from ..common_utils.test_utils import format_name

MAX_REPETITIONS = 100


@pytest.mark.skipif(os.environ['CP_PROVIDER'] == AzureClient.name,
                    reason="Spot instances (low priority virtual machines) are not supported for launching AZURE")
class TestOnDemandPipelineRun(object):
    pipeline_id = None
    pipeline_name = format_name('on_demand_integration_pipeline_test')
    run_id = None
    node_name = None
    state = FailureIndicator()
    test_case = 'TC-SCALING-12'

    @classmethod
    def setup_class(cls):
        logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                            format='%(levelname)s %(asctime)s %(module)s:%(message)s')
        cls.pipeline_id = PipelineManager.create(cls.pipeline_name)
        logging.info("Pipeline %s with ID %s created." % (cls.pipeline_name, cls.pipeline_id))

    @classmethod
    def teardown_class(cls):
        terminate_node(cls.node_name)
        logging.info("Node %s was terminated" % cls.node_name)

        if not cls.state.failure:
            PipelineManager.delete(cls.pipeline_id)
            logging.info("Pipeline %s deleted" % cls.pipeline_id)
            wait_for_instance_termination(cls.run_id, 150)

    @pipe_test
    def test_on_demand_pipeline_runs_and_finishes_successfully(self):
        pipeline_preference_should_be('cluster.spot', True)
        (run_id, _) = run_pipe(self.pipeline_name,
                               "-id", "24",
                               "-pt", "on-demand")
        logging.info("Pipeline run with ID %s." % run_id)
        TestOnDemandPipelineRun.run_id = run_id
        wait_for_required_status("SCHEDULED", run_id, MAX_REPETITIONS)
        wait_for_instance_creation(run_id, MAX_REPETITIONS)
        logging.info("Instance %s created." % run_id)
        node_price_type_should_be(run_id, spot=False)
        logging.info("Price type (instance life cycle) of %s has been checked." % run_id)
        wait_for_required_status("SUCCESS", run_id, MAX_REPETITIONS)
        logging.info("Pipeline %s has finished successfully." % run_id)

        node_state = wait_for_node_up(run_id, MAX_REPETITIONS)
        node_name = get_node_name_from_cluster_state(node_state)
        logging.info("Used node %s." % node_name)
        TestOnDemandPipelineRun.node_name = node_name

    @pipe_test
    def test_cluster_should_not_have_node_without_label(self):
        assert len(get_nodes_without_labels(self.node_name)) == 0, \
            "Cluster should not have nodes without labels."
