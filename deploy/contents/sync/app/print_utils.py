# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

DATETIME_FORMAT = "%Y-%m-%d %H:%M:%S"
LOG_MESSAGE_TEMPLATE = '{date}|{status}{message}'


def print_info_message(message):
    print(LOG_MESSAGE_TEMPLATE.format(date=get_formatted_now(), status=' ', message=message))


def print_warn_message(message):
    print(LOG_MESSAGE_TEMPLATE.format(date=get_formatted_now(), status='[WARN]|', message=message))


def get_formatted_now():
    return datetime.datetime.now().strftime(DATETIME_FORMAT)
