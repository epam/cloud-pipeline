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

from time import sleep

import requests

WAITING_SERVER_TIME = 5
MAX_RETRY_COUNT = 10


def wait_for_server_availability(func, max_retry_count=MAX_RETRY_COUNT, *args):
    max_retry_count -= 1
    try:
        return func(*args)
    except requests.exceptions.RequestException as http_error:
        if max_retry_count < 1:
            raise requests.exceptions.RequestException(str(http_error))
        sleep(WAITING_SERVER_TIME)
        return wait_for_server_availability(func, max_retry_count, *args)


def wait_for_server_enabling_if_needed():
    """do not throw if server temporary doesn't available."""
    def decorate(func):
        def applicator(*args):
            return wait_for_server_availability(func, MAX_RETRY_COUNT, *args)
        return applicator
    return decorate
