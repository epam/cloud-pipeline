# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

class SynchronizerConfig:

    def __init__(self, command, mode="single", at_time="00:01", execution_max_running_days=2):
        self.command = command
        self.mode = mode
        self.at_time = None
        if mode == "daemon":
            self.at_time = at_time
        self.execution_max_running_days = execution_max_running_days

    def to_json(self):
        return {
            "command": self.command,
            "execution_max_running_days": self.execution_max_running_days,
            "mode": self.mode,
            "at_time": self.at_time
        }
