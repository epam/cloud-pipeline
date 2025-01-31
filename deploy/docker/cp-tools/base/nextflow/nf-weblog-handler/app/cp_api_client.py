# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

from pipeline import PipelineAPI

class CloudPipelineApi(object):

    def __init__(self, api, run_id, logger):
        self.logger = logger
        self.api_client = PipelineAPI(api, 'logs')
        self.run_id = run_id

    def log_pipeline_run_engine_task_events(self, events):
        try:
            event_dict_list = [e.to_dict() for e in events]
            if self.api_client.log_pipeline_run_engine_task_events(self.run_id, event_dict_list):
                return True
        except Exception as e:
            self.logger.error("Failed to log engine task events.")
        return False
