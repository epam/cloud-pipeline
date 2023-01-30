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

import logging

import pytest

from ..utils.pipeline_utils import get_log_filename, stop_pipe_with_retry

runs_container = set()


@pytest.fixture(scope='function')
def runs():
    return runs_container


@pytest.fixture(scope='function', autouse=True)
def teardown_function(runs):
    yield
    logging.info("Stopping runs...")
    for run_id in runs:
        try:
            logging.info("Stopping run #%s...", run_id)
            stop_pipe_with_retry(run_id)
            logging.info("Run #%s has been stopped", run_id)
        except Exception:
            logging.exception("Run #%s has not been stopped due to error", run_id)
    runs.clear()


def pytest_sessionstart():
    logging.basicConfig(filename=get_log_filename(), level=logging.INFO,
                        format='%(levelname)s %(asctime)s %(module)s: %(message)s')
