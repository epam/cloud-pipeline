# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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


class ExtensionApplicationRule:
    REGEXP = 1
    EQUALS = 2

    def __init__(self, key, value, rule_type):
        self.key = key
        self.rule_type = rule_type
        self.value = value

    def test(self, param, param_value):
        if self.rule_type == ExtensionApplicationRule.EQUALS:
            return self.key == param and param_value and param_value == self.value
        elif self.rule_type == ExtensionApplicationRule.REGEXP:
            return self.key == param and param_value and re.match(self.value, param_value)


class ExtensionHandler:

    def __init__(self, command_group, command, rules):
        self.command_group = command_group
        self.command = command
        self.rules = rules

    def accept(self, command_group, command, arguments):
        if self._test(command_group, command, arguments):
            self._apply(arguments)
            return True
        return False

    def _apply(self, arguments):
        pass

    def _test(self, command_group, command, arguments):
        return command_group == self.command_group and command == self.command and self._test_arguments(arguments)

    def _test_arguments(self, arguments):
        for arg, value in arguments.items():
            for rule in self.rules:
                if rule.test(arg, value):
                    return True
        return False
