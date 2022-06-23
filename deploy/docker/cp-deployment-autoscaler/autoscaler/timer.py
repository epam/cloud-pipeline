# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

import time

import functools


class AutoscalingTimer:

    def __init__(self):
        self.last_scale_operation_time: int = 0
        self.last_trigger_time: int = time.time()
        self.scale_up_triggers_duration: int = 0
        self.scale_down_triggers_duration: int = 0

    @staticmethod
    def wraps(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            self = args[0]
            try:
                return func(*args, **kwargs)
            finally:
                self._timer.last_scale_operation_time = time.time()
                self._timer.last_trigger_time = self._timer.last_scale_operation_time
        return wrapper
