# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import subprocess
import os


def read_output(output):
    result = []
    line = output.readline()
    while line:
        line = line.strip()
        if line:
            result.append(line)
        line = output.readline()
    return result


def execute_command(cmd, env=None):
    process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=env)
    stdout = read_output(process.stdout)
    stderr = read_output(process.stderr)
    exit_code = process.wait()
    return exit_code, stdout, stderr


def get_command_output(cmd, args=None, expected_status=None, token=None):
    if args:
        for arg in args:
            cmd.append(arg)
    current_env = None
    if token is not None:
        current_env = os.environ.copy()
        current_env['API_TOKEN'] = token
    exit_code, stdout, stderr = execute_command(cmd, env=current_env)
    if expected_status is not None:
        assert exit_code == expected_status, \
            "Cmd '{}' doesn't return expected status {}.\nActual status '{}'.\nError message: {}".format(
                " ".join(cmd), str(expected_status), str(exit_code), stderr)
    return stdout, stderr


def get_test_prefix():
    try:
        return os.environ['TEST_PREFIX']
    except KeyError:
        return ''
