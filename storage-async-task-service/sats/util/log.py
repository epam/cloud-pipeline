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

"""Logging setup. The thread name is in the format, because each worker renames its own thread."""

from __future__ import annotations

import logging
import sys
from logging.handlers import TimedRotatingFileHandler
from pathlib import Path

LEVELS = {
    'DEBUG': logging.DEBUG,
    'INFO': logging.INFO,
    'WARNING': logging.WARNING,
    'ERROR': logging.ERROR,
}

FORMAT = '%(asctime)s [%(threadName)s] %(levelname)s %(name)s - %(message)s'
DATE_FORMAT = '%Y-%m-%d %H:%M:%S'

# The newest log always has this name; the handler adds a date suffix to the rotated ones.
LOG_FILE_NAME = 'storage-async-task-service.log'

DEFAULT_BACKUP_DAYS = 31


def configure_logging(level: str, log_dir: str | Path | None = None,
                      backup_days: int = DEFAULT_BACKUP_DAYS) -> None:
    """
    Logs to stdout, and to a file rotated at midnight when a directory is given.

    stdout is never dropped: it is what `kubectl logs` and `docker logs` show. The file is what
    survives a restart, since the pod mounts a host directory onto it.

    `force=True` replaces handlers set earlier, so a second call does not double every line.
    """
    handlers: list[logging.Handler] = [logging.StreamHandler(sys.stdout)]
    if log_dir:
        directory = Path(log_dir)
        directory.mkdir(parents=True, exist_ok=True)
        handlers.append(TimedRotatingFileHandler(
            directory / LOG_FILE_NAME, when='midnight', backupCount=backup_days, encoding='utf-8'))
    logging.basicConfig(
        level=LEVELS.get(level.upper(), logging.INFO),
        format=FORMAT,
        datefmt=DATE_FORMAT,
        handlers=handlers,
        force=True,
    )
    # The AWS libraries log every request at DEBUG, which buries our own output.
    logging.getLogger('botocore').setLevel(logging.INFO)
    logging.getLogger('boto3').setLevel(logging.INFO)
    logging.getLogger('urllib3').setLevel(logging.INFO)


def get_logger(name: str) -> logging.Logger:
    """The logger of one module. Call it as `get_logger(__name__)`."""
    return logging.getLogger(name)
