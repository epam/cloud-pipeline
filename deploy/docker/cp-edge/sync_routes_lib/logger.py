# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

import json
import os
import time
from datetime import datetime, timezone
from .config import DATE_FORMAT

def do_log(msg):
    print('[{}] {}'.format(datetime.now().strftime("%Y-%m-%d %H:%M:%S"), msg))

class RunLogger:
    def __init__(self, run_id, task_name, api_client):
        self.run_id = run_id
        self.task_name = task_name
        self.api_client = api_client

    def info(self, message):
        self._log(message=message, status='RUNNING')

    def warning(self, message):
        self._log(message='\033[93m' + message + '\033[0m', status='RUNNING')

    def success(self, message):
        self._log(message='\033[92m' + message + '\033[0m', status='SUCCESS')

    def _log(self, message, status):
        do_log("Log run log: " + message)
        now = datetime.fromtimestamp(time.time(), timezone.utc).strftime(DATE_FORMAT)
        date = now[0:len(now) - 3]
        log_entry = json.dumps({
            "runId": self.run_id,
            "date": date,
            "status": status,
            "logText": message,
            "taskName": self.task_name
        })
        self.api_client.call_api("run/{}/log".format(self.run_id), data=log_entry)
