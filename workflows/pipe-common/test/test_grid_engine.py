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

from datetime import datetime

from mock import MagicMock, Mock

from scripts.autoscale_sge import GridEngine, GridEngineJobState, GridEngineJob
from utils import assert_first_argument_contained, assert_first_argument_not_contained

executor = Mock()
grid_engine = GridEngine(executor)


def setup_function():
    executor.execute = MagicMock()


def test_qstat_parsing():
    stdout = """
    job-ID  prior   name       user         state submit/start at     queue                          slots ja-task-ID
    -----------------------------------------------------------------------------------------------------------------
          1 0.75000 name1      root         r     12/21/2018 11:48:00 main.q@pipeline-38415              1
          2 0.25000 name2      someUser     qw    12/21/2018 12:39:38                                    1
    """

    executor.execute_to_lines = MagicMock(return_value=__to_lines(stdout))
    jobs = grid_engine.get_jobs()

    assert len(jobs) == 2

    job_1, job_2 = jobs

    assert job_1.name == 'name1'
    assert job_1.user == 'root'
    assert job_1.state == GridEngineJobState.RUNNING
    assert job_1.datetime == datetime(2018, 12, 21,
                                      11, 48, 00)
    assert job_1.host == 'pipeline-38415'
    assert job_1.array is None

    assert job_2.name == 'name2'
    assert job_2.user == 'someUser'
    assert job_2.state == GridEngineJobState.PENDING
    assert job_2.datetime == datetime(2018, 12, 21,
                                      12, 39, 38)
    assert job_2.host is None
    assert job_2.array is None


def test_qstat_array_job_parsing():
    stdout = """
    job-ID  prior   name       user         state submit/start at     queue                          slots ja-task-ID
    -----------------------------------------------------------------------------------------------------------------
          1 0.75000 name1      root         r     12/21/2018 12:48:00 main.q@pipeline-38415              1
          2 0.25000 name2      root         qw    12/21/2018 14:52:43                                    1 1
          2 0.25000 name2      root         qw    12/21/2018 14:51:43                                    1 2-10:1
          3 0.25000 name3      root         qw    12/21/2018 14:51:43                                    1 1-5:1
          4 0.25000 name4      root         qw    12/21/2018 14:51:43                                    1 8,9
    """

    executor.execute_to_lines = MagicMock(return_value=__to_lines(stdout))
    jobs = grid_engine.get_jobs()

    _, job_2_1, job_2_array, job_3_array, job_4_array = jobs

    assert [1] == [1]
    assert job_2_array.array == list(range(2, 10 + 1))
    assert job_3_array.array == list(range(1, 5 + 1))
    assert job_4_array.array == list(range(8, 9 + 1))


def test_qstat_empty_parsing():
    stdout = """
    """

    executor.execute_to_lines = MagicMock(return_value=__to_lines(stdout))
    jobs = grid_engine.get_jobs()

    assert len(jobs) == 0


def test_kill_jobs():
    jobs = [
        GridEngineJob(id=1, name='', user='', state='', datetime=''),
        GridEngineJob(id=2, name='', user='', state='', datetime='')
    ]

    grid_engine.kill_jobs(jobs)
    assert_first_argument_contained(executor.execute, 'qdel ')
    assert_first_argument_contained(executor.execute, ' 1 2')
    assert_first_argument_not_contained(executor.execute, '-f')


def test_force_kill_jobs():
    jobs = [
        GridEngineJob(id=1, name='', user='', state='', datetime=''),
        GridEngineJob(id=2, name='', user='', state='', datetime='')
    ]

    grid_engine.kill_jobs(jobs, force=True)
    assert_first_argument_contained(executor.execute, 'qdel ')
    assert_first_argument_contained(executor.execute, ' 1 2')
    assert_first_argument_contained(executor.execute, '-f')


def test_kill_array_jobs():
    jobs = [
        GridEngineJob(id=1, name='', user='', state='', datetime=''),
        GridEngineJob(id=2, name='', user='', state='', datetime='', array=[3]),
        GridEngineJob(id=3, name='', user='', state='', datetime='', array=[5, 6])
    ]

    grid_engine.kill_jobs(jobs)
    assert_first_argument_contained(executor.execute, 'qdel ')
    assert_first_argument_contained(executor.execute, ' 1 2.3 3')
    assert_first_argument_not_contained(executor.execute, '-f')


def __to_lines(stdout):
    return [line for line in stdout.splitlines() if line.strip()]
