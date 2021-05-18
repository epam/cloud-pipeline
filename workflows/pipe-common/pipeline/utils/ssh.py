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

import logging
import paramiko


class SSHError(RuntimeError):
    pass


class HostSSH:

    def __init__(self, host, private_key_path):
        self._host = host
        self._private_key_path = private_key_path

    def execute(self, command, user):
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.MissingHostKeyPolicy())
        client.connect(self._host, username=user, key_filename=self._private_key_path)
        _, stdout, stderr = client.exec_command(command)
        exit_code = stdout.channel.recv_exit_status()
        for line in stdout:
            logging.info(line.strip('\n'))
        for line in stderr:
            logging.warning(line.strip('\n'))
        if exit_code != 0:
            raise SSHError('Command has exited with ' + exit_code)
        client.close()
