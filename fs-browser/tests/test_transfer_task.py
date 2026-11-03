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

# Covers the command execution of TransferTask: "pipe storage cp" output reaches the task status and its error
# message, so it has to be text, not bytes. Run with `python3 -m unittest discover -s tests` from inside fs-browser.
# The "pipeline" package (pipe-common) is replaced with a stub when it is not installed.

import os
import sys
import types
import unittest

FS_BROWSER_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, FS_BROWSER_DIR)

try:
    import pipeline  # noqa: F401
except ImportError:
    _stub = types.ModuleType('pipeline')
    for _name in ('TaskStatus', 'Logger', 'PipelineAPI'):
        setattr(_stub, _name, type(_name, (object,), {}))
    sys.modules['pipeline'] = _stub

from fsbrowser.src.transfer_task import TransferTask  # noqa: E402

SHELL = '/bin/sh'


class TransferTaskCommandTest(unittest.TestCase):

    def test_command_output_is_text(self):
        task = TransferTask('task-1', 'storage')
        exit_code, stdout, stderr = task.execute_command([SHELL, '-c', 'echo first; echo last; echo oops 1>&2'],
                                                         False)
        self.assertEqual(0, exit_code)
        self.assertEqual('last', stdout)
        self.assertEqual(['oops'], stderr)

    def test_failed_command_message_has_no_bytes_literals(self):
        task = TransferTask('task-1', 'storage')
        with self.assertRaises(RuntimeError) as error:
            task.get_command_output([SHELL, '-c', 'echo out; echo err 1>&2; exit 3'], False)
        self.assertEqual("stdout: out\nstderr: ['err']", str(error.exception))


if __name__ == '__main__':
    unittest.main()
