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

import datetime
import logging
import os
import time
import sys
from logging.handlers import TimedRotatingFileHandler

DATE_FORMAT = "%Y-%m-%d %H:%M:%S.%f"


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


class AppLogger(object):

    def __init__(self, log_topic, log_dir="logs", stdout=True, backup_count=31):
        self.logger = logging.getLogger("Storage Lifecycle Service Log")
        self.logger.setLevel(logging.INFO)
        if not stdout:
            os.makedirs(log_dir, exist_ok=True)
            handler = TimedRotatingFileHandler(os.path.join(log_dir, "storage-lifecycle-service-" + log_topic + ".log"),
                                               when="d",
                                               interval=1,
                                               backupCount=backup_count)
            self.logger.addHandler(handler)

    def log(self, message):
        self.logger.info("{} {}".format(AppLogger._build_current_date(), message))

    def exception(self, message):
        self.logger.exception(message)

    @staticmethod
    def _build_current_date():
        current_date = datetime.datetime.utcfromtimestamp(time.time()).strftime(DATE_FORMAT)
        return current_date[0:len(current_date) - 3]

