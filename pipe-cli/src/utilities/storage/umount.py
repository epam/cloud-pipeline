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


class Umount(object):

    UMOUNT_COMMANDS = ['fusermount -u', 'umount', 'diskutil unmount']

    def umount_storages(self, mountpoint, quiet=False):
        cmd_logs = {}
        for cmd in self.UMOUNT_COMMANDS:
            status, log = self.try_exec_cmd(cmd, mountpoint)
            if status == 0:
                return
            cmd_logs[cmd] = log
        error_message = 'All attempts to umount %s failed.' % mountpoint
        for cmd, log in cmd_logs.iteritems():
            if log:
                error_message += '\n%s: %s' % (cmd, log)
        raise RuntimeError(error_message)

    def try_exec_cmd(self, cmd, mountpoint):
        try:
            command = cmd.split(' ')
            command.append(mountpoint)
            umount_proc = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            stdout, stderr = umount_proc.communicate()
            exit_code = umount_proc.wait()
            return exit_code, stderr
        except BaseException as e:
            return -1, str(e)
