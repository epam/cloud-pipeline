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

# Covers the output of the commands that pipe_exec (pipeline/common/logged_command.py), the grid engine autoscaler
# (pipeline/hpc/cmd.py) and the local executor (pipeline/utils/ssh.py) run: an output that is not UTF-8 does not fail
# the command. pipeline/common/common.py is covered by test_common_cmd.py.

import locale
import sys
import unittest

from mock import MagicMock, patch

from pipeline.api import TaskStatus
from pipeline.common import logged_command
from pipeline.hpc.cmd import CmdExecutor
from pipeline.utils.ssh import ExecutorError, LocalExecutor

# Python 3 decodes the output with the encoding of the locale and replaces a byte that is not valid there: with UTF-8,
# the encoding of the test containers, it is U+FFFD. Python 2 keeps the bytes.
NOT_UTF_8_OUTPUT = b'caf\xe9\n'.decode(locale.getpreferredencoding(False), 'replace') \
    if sys.version_info[0] >= 3 else 'caf\xe9\n'
NOT_UTF_8_COMMAND = r"printf 'caf\351\n'"


class LoggedCommandTest(unittest.TestCase):

    def test_output_that_is_not_utf_8_does_not_fail_the_command(self):
        with patch.object(logged_command, 'Logger') as logger:
            logged_command.LoggedCommand(NOT_UTF_8_COMMAND, None, 'Task').execute()

        logger.log_task_event.assert_any_call('Task', NOT_UTF_8_OUTPUT.rstrip('\n'), status=TaskStatus.RUNNING)


class CmdExecutorTest(unittest.TestCase):

    def test_output_that_is_not_utf_8_does_not_fail_the_command(self):
        self.assertEqual(NOT_UTF_8_OUTPUT, CmdExecutor().execute(NOT_UTF_8_COMMAND))


class LocalExecutorTest(unittest.TestCase):

    def test_output_that_is_not_utf_8_does_not_fail_the_command(self):
        logger = MagicMock()

        LocalExecutor().execute(NOT_UTF_8_COMMAND + '; ' + NOT_UTF_8_COMMAND + ' 1>&2', logger=logger)

        self.assertEqual([((NOT_UTF_8_OUTPUT,),), ((NOT_UTF_8_OUTPUT,),)], logger.debug.call_args_list)

    def test_a_failed_command_still_fails(self):
        with self.assertRaises(ExecutorError):
            LocalExecutor().execute(NOT_UTF_8_COMMAND + '; exit 3', logger=MagicMock())


if __name__ == '__main__':
    unittest.main()
