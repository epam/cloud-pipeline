# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

"""
Creates an additional worker for the current cluster.
"""

import os, argparse
from autoscale_sge import CmdExecutor, GridEngineScaleUpHandler, Logger, GridEngine, MemoryHostStorage
from pipeline.api import PipelineAPI

parser = argparse.ArgumentParser(description='Creates an additional worker for the current cluster.',
                                 formatter_class=argparse.ArgumentDefaultsHelpFormatter)
parser.add_argument('--debug',
                    action='store_true',
                    help='If specified print all logs to command line.')
args = parser.parse_args()

pipeline_api = os.environ['API']
master_run_id = os.environ['RUN_ID']
default_hostfile = os.environ['DEFAULT_HOSTFILE']
instance_disk = os.environ['instance_disk']
instance_type = os.environ['instance_size']
instance_image = os.environ['docker_image']

Logger.init(cmd=args.debug, log_file='/common/workdir/.autoscaler.log', task='GridEngineAutoscaling')

cmd_executor = CmdExecutor()
grid_engine = GridEngine(cmd_executor=cmd_executor)
host_storage = MemoryHostStorage()
pipe = PipelineAPI(api_url=pipeline_api, log_dir='/common/workdir/.pipe.log')

scale_up_handler = GridEngineScaleUpHandler(cmd_executor=cmd_executor, pipe=pipe, grid_engine=grid_engine,
                                            host_storage=host_storage, parent_run_id=master_run_id,
                                            default_hostfile=default_hostfile, instance_disk=instance_disk,
                                            instance_type=instance_type, instance_image=instance_image)
scale_up_handler.scale_up()
