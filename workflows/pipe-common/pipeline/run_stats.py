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

import sys
import requests
import datetime


class Task:

    def __init__(self, start, end, name, parameters):
        self.start = start
        self.end = end
        self.name = name
        self.parameters = parameters
        self.started = False
        self.ended = False


class Api:

    __API_URL = "http://10.66.128.50:9999/pipeline/restapi/"
    __LOG_URL = 'run/{}/logs'
    __RESPONSE_STATUS_OK = 'OK'
    __DEFAULT_HEADER = {'content-type': 'application/json'}
    __DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"

    def __init__(self):
        pass

    def get_logs(self, run_id):
        result = requests.get(self.__API_URL + self.__LOG_URL.format(run_id), headers=self.__DEFAULT_HEADER)
        if hasattr(result.json(), 'error') or result.json()['status'] != self.__RESPONSE_STATUS_OK:
            raise RuntimeError('Failed to load run {} logs. API response: {}'.format(run_id, result.json()['message']))
        logs = result.json()['payload']
        tasks = {}
        for log in logs:
            id = log['task']['name']
            name = id
            parameters = ''
            if 'parameters' in log['task']:
                id += ' ' + log['task']['parameters']
                parameters = log['task']['parameters']
            else:
                continue
            date = datetime.datetime.strptime(log['date'], self.__DATE_FORMAT)
            if id not in tasks:
                task = Task(date, date, name, parameters)
                tasks[id] = task
            else:
                task = tasks[id]
                if 'logText' in log and 'Kubernetes pod state: Running' in log['logText'] and not task.started:
                    task.start = date
                    task.started = True
                elif log['status'] == "FAILURE" or log['status'] == "STOPPED" or log['status'] == "SUCCESS" and not task.ended:
                    task.end = date
                    task.ended = True
        total_time = 0
        for id in tasks:
            task = tasks[id]
            task_time = (task.end - task.start).seconds
            minutes = task_time/60
            seconds = task_time%60
            print('{}\t{}\t{} min {} s'.format(task.name, task.parameters, minutes, seconds))
            total_time += task_time
        print
        print('Whole pipeline ran for {} s.'.format(total_time))


if __name__ == '__main__':
    if len(sys.argv) < 2:
        raise RuntimeError('Run ID is required for script')
    run_id = sys.argv[1]
    api = Api()
    api.get_logs(run_id)

