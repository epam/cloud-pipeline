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

from e2e.cli.utils.pipeline_utils import *

MAX_REPETITIONS = 100

def run_pipeline(pipeline_name, image, params="CP_CAP_LIMIT_MOUNTS 'None'"):
    (run_id, _) = run_pipe(pipeline_name,
                           "-id", "34",
                           "-pt", "on-demand",
                           "-cmd", "sleep infinity",
                           "-di", image,
                           "-- " + params)
    logging.info("Pipeline run with ID %s." % run_id)
    wait_for_instance_creation(run_id, MAX_REPETITIONS)
    logging.info("Instance %s created." % run_id)
    wait_for_required_status("RUNNING", run_id, MAX_REPETITIONS)
    logging.info("Pipeline %s has finished successfully." % run_id)

    node_state = wait_for_node_up(run_id, MAX_REPETITIONS)
    node_name = get_node_name_from_cluster_state(node_state)
    logging.info("Used node %s." % node_name)
    return run_id, node_name