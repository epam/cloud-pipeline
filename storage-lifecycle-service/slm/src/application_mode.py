# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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
from time import sleep

import schedule


class ApplicationModeRunner:

    def run(self):
        pass

    @staticmethod
    def get_application_runner(slm, config):
        if config.mode == "daemon":
            return DaemonApplicationModeRunner(slm, config.at_time)
        else:
            return SingleApplicationModeRunner(slm)


class SingleApplicationModeRunner(ApplicationModeRunner):

    def __init__(self, storage_lifecycle_manager):
        self.storage_lifecycle_manager = storage_lifecycle_manager

    def run(self):
        self.storage_lifecycle_manager.sync()


class DaemonApplicationModeRunner(ApplicationModeRunner):

    def __init__(self, storage_lifecycle_manager, at_time):
        self.storage_lifecycle_manager = storage_lifecycle_manager

        self.at_time = at_time

    def run(self):
        schedule.every().day.at(self.at_time).do(self.storage_lifecycle_manager.sync)
        while True:
            schedule.run_pending()
            sleep(1)

