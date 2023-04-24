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

class TaskStatus(object):
    PENDING = 'pending'
    SUCCESS = 'success'
    RUNNING = 'running'
    FAILURE = 'failure'
    CANCELED = 'canceled'

    @staticmethod
    def is_terminal(status):
        return status == TaskStatus.SUCCESS or status == TaskStatus.FAILURE or status == TaskStatus.CANCELED


class Task:

    def __init__(self, task_id, logger):
        self.task_id = task_id
        self.status = TaskStatus.PENDING
        self.result = None
        self.message = None
        self.logger = logger

    def success(self, result=None):
        self.status = TaskStatus.SUCCESS
        if result:
            self.result = result

    def failure(self, e=None):
        self.status = TaskStatus.FAILURE
        if e:
            self.message = e.__str__()

    def running(self):
        self.status = TaskStatus.RUNNING

    def cancel(self, working_directory):
        return RuntimeError('Unsupported operation')

    def to_json(self):
        result = {'status': self.status}
        if self.message:
            result.update({'message': self.message})
        if self.result:
            result.update({'result': self.result})
        return result
