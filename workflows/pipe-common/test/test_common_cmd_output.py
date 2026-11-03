# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

import unittest

from pipeline.common.common import execute_cmd_command_and_get_stdout_stderr


class CommandOutputTest(unittest.TestCase):

    def test_output_is_text(self):
        exit_code, stdout, stderr = execute_cmd_command_and_get_stdout_stderr('echo out; echo err 1>&2', silent=True)
        self.assertEqual(0, exit_code)
        self.assertEqual('out\n', stdout)
        self.assertEqual('err\n', stderr)

    def test_output_that_is_not_utf_8_does_not_fail_the_command(self):
        exit_code, stdout, _ = execute_cmd_command_and_get_stdout_stderr(r"printf 'caf\351\n'", silent=True)
        self.assertEqual(0, exit_code)
        self.assertEqual('caf�\n', stdout)


if __name__ == '__main__':
    unittest.main()
