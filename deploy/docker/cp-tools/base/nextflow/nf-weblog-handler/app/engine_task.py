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

class CloudPipelineRunEngineTask(object):

    def __init__(self, run_id, task_group, task_id, task_key, task_name, task_tag, engine_type,
                 status, attributes, start_timestamp=None, end_timestamp=None):
        self.runId = run_id
        self.taskGroup = task_group
        self.taskId = task_id
        self.taskKey = task_key
        self.taskName = task_name
        self.taskTag = task_tag
        self.engineType = engine_type
        self.status = status
        self.attributes = attributes
        self.startDateTime = start_timestamp
        self.endDateTime = end_timestamp

    def to_dict(self):
        return vars(self)
