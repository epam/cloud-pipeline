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
import os

from pipeline import TaskStatus, Logger

FS_BROWSER_TASK = "FsBrowserTask"


class BrowserLogger(object):

    def __init__(self, run_id=None, log_dir=None):
        self.run_id = run_id
        self.log_dir = log_dir
        if run_id:
            self.api_url = os.environ.get('API')
            self.api_token = os.environ.get('API_TOKEN')
            self.status = TaskStatus.RUNNING

    def log(self, message):
        if self.run_id:
            Logger.info('[{}] {}'.format(self.run_id, message),
                        task_name=FS_BROWSER_TASK,
                        run_id=self.run_id,
                        api_url=self.api_url,
                        log_dir=None,
                        omit_console=True)
        else:
            print message
