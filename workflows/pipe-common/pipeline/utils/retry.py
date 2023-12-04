#  Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import time


def retry(func, attempts, delay_seconds, logger):
    delay_seconds = max(delay_seconds, 0)
    attempts = max(attempts, 1)
    attempt = 0
    exceptions = []
    while attempt < attempts:
        attempt += 1
        try:
            return func(attempt, attempts)
        except Exception as e:
            exceptions.append(e)
        if attempt >= attempts:
            raise exceptions[-1]
        logger.debug('Attempt {attempt}/{attempts} has failed. It will be retried in {delay_seconds} s.'
                     .format(attempt=attempt, attempts=attempts, delay_seconds=delay_seconds),
                     trace=True)
        time.sleep(delay_seconds)
