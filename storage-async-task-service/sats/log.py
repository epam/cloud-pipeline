# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

"""
Logging setup.

The thread name is in the format on purpose: this service runs several workers at once and each renames
its thread after the task it took, which is what makes an interleaved log readable.
"""

import logging
import sys

LEVELS = {
    'DEBUG': logging.DEBUG,
    'INFO': logging.INFO,
    'WARNING': logging.WARNING,
    'ERROR': logging.ERROR,
}

FORMAT = '%(asctime)s [%(threadName)s] %(levelname)s %(name)s - %(message)s'
DATE_FORMAT = '%Y-%m-%d %H:%M:%S'


def configure_logging(level: str) -> None:
    """
    Sends every log record of the process to stdout, once.

    stdout and not a file, because kubernetes and `docker logs` collect a container's standard output.
    `force=True` discards handlers configured earlier, so a second call - in a test, say - does not
    double every line.
    """
    logging.basicConfig(
        level=LEVELS.get(level.upper(), logging.INFO),
        format=FORMAT,
        datefmt=DATE_FORMAT,
        stream=sys.stdout,
        force=True,
    )
    # The AWS libraries log every HTTP request at DEBUG, which buries our own output at that level.
    logging.getLogger('botocore').setLevel(logging.INFO)
    logging.getLogger('boto3').setLevel(logging.INFO)
    logging.getLogger('urllib3').setLevel(logging.INFO)


def get_logger(name: str) -> logging.Logger:
    """Returns the logger for a module. Call it as `get_logger(__name__)`."""
    return logging.getLogger(name)
