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

from abc import ABCMeta, abstractmethod

import paramiko


class SSHError(RuntimeError):
    pass


class CloudPipelineSSH:
    __metaclass__ = ABCMeta

    @abstractmethod
    def execute(self, command, user=None, logger=None):
        pass


class LogSSH(CloudPipelineSSH):

    def __init__(self, logger, inner):
        self._logger = logger
        self._inner = inner

    def execute(self, command, user=None, logger=None):
        self._inner.execute(command, user=user, logger=logger or self._logger)


class UserSSH(CloudPipelineSSH):

    def __init__(self, user, inner):
        self._user = user
        self._inner = inner

    def execute(self, command, user=None, logger=None):
        self._inner.execute(command, user=user or self._user, logger=logger)


class HostSSH(CloudPipelineSSH):

    def __init__(self, host, private_key_path):
        self._host = host
        self._private_key_path = private_key_path

    def execute(self, command, user=None, logger=None):
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.MissingHostKeyPolicy())
        client.connect(self._host, username=user, key_filename=self._private_key_path)
        _, stdout, stderr = client.exec_command(command)
        exit_code = stdout.channel.recv_exit_status()
        if logger:
            for line in stdout:
                stripped_line = line.strip('\n')
                logger.info(stripped_line)
            for line in stderr:
                stripped_line = line.strip('\n')
                logger.warning(stripped_line)
        if exit_code != 0:
            raise SSHError('Command has finished with exit code ' + str(exit_code))
        client.close()
