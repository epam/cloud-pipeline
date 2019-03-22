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
Stops a worker of the current cluster.
"""

import os, argparse
from autoscale_sge import CmdExecutor, GridEngineScaleDownHandler, Logger, GridEngine
from pipeline.api import PipelineAPI

parser = argparse.ArgumentParser(description='Stops a worker of the current cluster.',
                                 formatter_class=argparse.ArgumentDefaultsHelpFormatter)
parser.add_argument('host',
                    help='Hostname of the worker to be stopped.')
parser.add_argument('--debug',
                    action='store_true',
                    help='If specified print all logs to command line.')
args = parser.parse_args()

pipeline_api = os.environ['API']
default_hostfile = os.environ['DEFAULT_HOSTFILE']

Logger.init(cmd=args.debug, log_file='/common/workdir/.autoscaler.log', task='GridEngineAutoscaling')

cmd_executor = CmdExecutor()
grid_engine = GridEngine(cmd_executor=cmd_executor)
pipe = PipelineAPI(api_url=pipeline_api, log_dir='/common/workdir/.pipe.log')

scale_down_handler = GridEngineScaleDownHandler(cmd_executor=cmd_executor, grid_engine=grid_engine,
                                                default_hostfile=default_hostfile)
scale_down_handler.scale_down(args.host)