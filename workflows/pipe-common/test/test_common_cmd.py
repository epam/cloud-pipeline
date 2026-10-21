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

import threading

from mock import MagicMock, patch

from pipeline.common import common


def popen_returning(out, err, returncode, on_start=None):
    def popen(*args, **kwargs):
        if on_start:
            on_start()
        process = MagicMock(returncode=returncode)
        process.communicate.return_value = (out, err)
        return process
    return popen


def test_command_is_started_without_extra_arguments_by_default():
    with patch.object(common.subprocess, 'Popen', side_effect=popen_returning('out', '', 0)) as popen:
        assert common.execute_cmd_command_and_get_stdout_stderr('ls', silent=True, executable='/bin/bash') \
            == (0, 'out', '')
    stdout, stderr = common._get_stdout_and_stderr()
    popen.assert_called_once_with('ls', shell=True, stdout=stdout, stderr=stderr, executable='/bin/bash',
                                  universal_newlines=True)


def test_command_is_started_under_the_start_lock():
    start_lock = threading.Lock()
    held = []
    with patch.object(common.subprocess, 'Popen',
                      side_effect=popen_returning('', 'err', 3, on_start=lambda: held.append(start_lock.locked()))):
        assert common.execute_cmd_command_and_get_stdout_stderr('ls', silent=True, start_lock=start_lock) \
            == (3, '', 'err')
    assert held == [True]
    assert not start_lock.locked()


def test_command_output_is_read_after_the_start_lock_is_released():
    start_lock = threading.Lock()
    held = []

    def popen(*args, **kwargs):
        process = MagicMock(returncode=0)
        process.communicate.side_effect = lambda: held.append(start_lock.locked()) or ('', '')
        return process

    with patch.object(common.subprocess, 'Popen', side_effect=popen):
        common.execute_cmd_command('ls', silent=True, start_lock=start_lock)
    assert held == [False]


def test_start_lock_is_passed_through_execute_cmd_command():
    start_lock = threading.Lock()
    with patch.object(common, 'execute_cmd_command_and_get_stdout_stderr', return_value=(5, '', '')) as execute:
        assert common.execute_cmd_command('ls', True, '/bin/bash', start_lock=start_lock) == 5
    execute.assert_called_once_with('ls', True, '/bin/bash', start_lock=start_lock)
