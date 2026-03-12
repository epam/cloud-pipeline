# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

import logging
import os

logger = logging.getLogger(__name__)

_DATE_FORMAT = '%Y-%m-%d %H:%M:%S.000'


def read_last_run_date(state_file):
    """
    Returns the date string stored from the previous successful run,
    or None if the file doesn't exist or can't be read.
    """
    if not os.path.exists(state_file):
        return None
    try:
        with open(state_file, 'r') as f:
            value = f.read().strip()
        logger.info('Loaded last run date from %s: %s', state_file, value)
        return value if value else None
    except Exception:
        logger.warning('Failed to read state file %s, will process full history', state_file, exc_info=True)
        return None


def write_last_run_date(state_file, date_str):
    """
    Persists date_str to state_file so the next run can resume from there.
    """
    try:
        os.makedirs(os.path.dirname(os.path.abspath(state_file)), exist_ok=True)
        with open(state_file, 'w') as f:
            f.write(date_str)
        logger.info('Saved last run date to %s: %s', state_file, date_str)
    except Exception:
        logger.warning('Failed to write state file %s', state_file, exc_info=True)
