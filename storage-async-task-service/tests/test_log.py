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
Tests of the logging setup. The property worth a test is that stdout never goes away.

These tests reconfigure the root logger, and the fixture puts it back.
"""

import logging
import sys
from logging.handlers import TimedRotatingFileHandler

import pytest

from sats.util.log import LOG_FILE_NAME, configure_logging, get_logger


@pytest.fixture(autouse=True)
def restored_root_logger():
    root = logging.getLogger()
    saved_handlers = root.handlers[:]
    saved_level = root.level
    yield
    root.handlers = saved_handlers
    root.setLevel(saved_level)


def _handlers_of_type(handler_type) -> list[logging.Handler]:
    return [handler for handler in logging.getLogger().handlers
            if isinstance(handler, handler_type)]


def test_logs_to_stdout_when_no_directory_is_given():
    configure_logging('INFO')

    assert _handlers_of_type(logging.StreamHandler)
    assert not _handlers_of_type(TimedRotatingFileHandler)


def test_adds_a_rotating_file_beside_stdout(tmp_path):
    configure_logging('INFO', log_dir=tmp_path, backup_days=7)

    # A `TimedRotatingFileHandler` is itself a `StreamHandler`, so the stdout one is found by where
    # it writes. Identity against `sys.stdout`, because pytest replaces it with a capture object.
    assert any(handler.stream is sys.stdout
               for handler in _handlers_of_type(logging.StreamHandler)
               if not isinstance(handler, TimedRotatingFileHandler))
    files = _handlers_of_type(TimedRotatingFileHandler)
    assert len(files) == 1
    assert files[0].backupCount == 7


def test_writes_what_is_logged_into_the_file(tmp_path):
    configure_logging('INFO', log_dir=tmp_path)

    get_logger(__name__).info('an archive was packed')
    for handler in logging.getLogger().handlers:
        handler.flush()

    assert 'an archive was packed' in (tmp_path / LOG_FILE_NAME).read_text(encoding='utf-8')


def test_creates_the_log_directory(tmp_path):
    # A developer passing `--log-dir` at a path that does not exist yet should not have the service
    # refuse to start over it.
    directory = tmp_path / 'logs' / 'sats'

    configure_logging('INFO', log_dir=directory)

    assert directory.is_dir()


def test_does_not_double_the_handlers_when_configured_twice(tmp_path):
    # The second call is what a test does, and what a second entry into `main` would do. Without
    # `force=True` every line would then appear twice in both places.
    configure_logging('INFO', log_dir=tmp_path)
    configure_logging('DEBUG', log_dir=tmp_path)

    assert len(_handlers_of_type(TimedRotatingFileHandler)) == 1
    assert logging.getLogger().level == logging.DEBUG
