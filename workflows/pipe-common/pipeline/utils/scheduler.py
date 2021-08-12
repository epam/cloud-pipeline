# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

from pipeline.utils.platform import assert_windows

assert_windows('Task scheduling')

import win32com.client

_TASK_ACTION_EXEC = 0
_TASK_LOGON_NONE = 0
_TASK_CREATE_OR_UPDATE = 6
_TASK_TRIGGER_LOGON = 9
_TASK_RUNLEVEL_HIGHEST = 1
_POWERSHELL_EXECUTABLE = 'powershell.exe'
_POWERSHELL_EXECUTE_SCRIPT_ARGS_TEMPLATE = '{} -File "{}" '
_POWERSHELL_WINDOW_STYLE_HIDDEN_FLAG = '-windowstyle hidden'
_PYTHON_EXECUTABLE = 'python.exe'
_PYTHON_EXECUTE_COMMAND_TEMPLATE = '-c "{}"'
_WIN_SCHEDULE_SERVICE = 'Schedule.Service'


def _schedule_command_on_user_logon(logon_user, task_name, executable, arguments):
    scheduler = win32com.client.Dispatch(_WIN_SCHEDULE_SERVICE)
    scheduler.Connect()
    root_folder = scheduler.GetFolder('\\')
    task_def = scheduler.NewTask(0)

    logon_trigger = task_def.Triggers.Create(_TASK_TRIGGER_LOGON)
    logon_trigger.UserId = logon_user

    action = task_def.Actions.Create(_TASK_ACTION_EXEC)
    action.Path = executable
    action.Arguments = arguments

    task_def.RegistrationInfo.Description = task_name
    task_def.Settings.Enabled = True
    task_def.Principal.UserId = logon_user
    task_def.Principal.RunLevel = _TASK_RUNLEVEL_HIGHEST

    root_folder.RegisterTaskDefinition(task_name, task_def, _TASK_CREATE_OR_UPDATE, '', '', _TASK_LOGON_NONE)


def schedule_python_command_on_logon(user, task_name, python_command):
    command = _PYTHON_EXECUTE_COMMAND_TEMPLATE.format(python_command)
    _schedule_command_on_user_logon(user, task_name, _PYTHON_EXECUTABLE, command)


def schedule_powershell_script_on_logon(user, task_name, script_path, is_hidden=False, arguments=None):
    command = _POWERSHELL_EXECUTE_SCRIPT_ARGS_TEMPLATE.format(
        _POWERSHELL_WINDOW_STYLE_HIDDEN_FLAG if is_hidden else '', script_path)
    if arguments:
        command += arguments
    _schedule_command_on_user_logon(user, task_name, _POWERSHELL_EXECUTABLE, command)
