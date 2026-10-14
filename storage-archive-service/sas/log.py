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

`logging` is part of the standard library and works like log4j: named loggers in a tree, a level per
logger, handlers that write somewhere. `logging.getLogger('sas.worker')` is a child of `sas`, so
configuring `sas` configures everything under it.

Two things are worth knowing about it:

  * calls are not interpolated with f-strings but with `%s` placeholders and extra arguments -
    `log.info('took %s s', seconds)`. The formatting happens only if the record is actually emitted,
    and the arguments stay separate, which structured log collectors can use;
  * every record carries the name of the thread that produced it. This service runs several workers in
    parallel, so the thread name is in the format and every worker renames its thread after the task
    it took. That is what makes an interleaved log readable.
"""

import logging
import sys

# Level names as the user types them on the command line, mapped to the module's constants. `dict`
# literals use braces; this one is a plain lookup table.
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

    stdout and not a file: the service runs as a single container, and both kubernetes and
    `docker logs` collect a container's standard output. The lifecycle service writes files because it
    is a cron-style job whose output nobody is watching live; a long-running loop is the other case.

    `force=True` discards handlers configured earlier. Without it a second call - in a test, say -
    would add another handler and every line would appear twice.
    """
    logging.basicConfig(
        level=LEVELS.get(level.upper(), logging.INFO),
        format=FORMAT,
        datefmt=DATE_FORMAT,
        stream=sys.stdout,
        force=True,
    )
    # botocore logs every HTTP request it makes at DEBUG, which buries our own output when we are
    # debugging. Keep the AWS libraries at INFO regardless of our level.
    logging.getLogger('botocore').setLevel(logging.INFO)
    logging.getLogger('boto3').setLevel(logging.INFO)
    logging.getLogger('urllib3').setLevel(logging.INFO)


def get_logger(name: str) -> logging.Logger:
    """
    Returns the logger for a module. Call it as `get_logger(__name__)`.

    `__name__` is a name every module gets for free, holding its own dotted path - `sas.worker` inside
    `sas/worker.py`. Using it means the logger name always matches the file, with no string to keep in
    step.
    """
    return logging.getLogger(name)
