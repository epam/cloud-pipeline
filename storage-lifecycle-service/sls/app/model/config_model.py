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
import re


class SynchronizerConfig:

    def __init__(self, command, mode="single", start_at=None, start_each=None, execution_max_running_days=2):
        self.command = command
        self.mode = mode
        self.start_at = None
        self.start_each = None
        if mode == "daemon":
            self.start_at = start_at
            self.start_each = int(start_each) if start_each else None
        self.execution_max_running_days = execution_max_running_days
        self._validate()

    def to_json(self):
        return {
            "command": self.command,
            "execution_max_running_days": self.execution_max_running_days,
            "mode": self.mode,
            "start_at": self.start_at,
            "start_each": self.start_each
        }

    def _validate(self):
        if self.mode == "daemon":
            if self.start_at and self.start_each:
                raise RuntimeError("Only one of 'start-at' or 'start-each' should be provided")

            if self.start_at and not re.match("\\d\\d:\\d\\d", self.start_at):
                raise RuntimeError(
                    "Wrong format of 'start-at' argument: {}, please specify it in format: 00:00".format(self.start_at))

            if self.start_each and (not isinstance(self.start_each, int) or self.start_each <= 0):
                raise RuntimeError(
                    "Wrong format of 'start-each' argument: {}, please specify integer > 0".format(self.start_each))
