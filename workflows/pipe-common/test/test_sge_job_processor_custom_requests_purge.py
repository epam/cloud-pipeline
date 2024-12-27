#  Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

from mock import MagicMock, Mock

from pipeline.hpc.engine.gridengine import GridEngineJob
from pipeline.hpc.engine.sge import SunGridEngineCustomRequestsPurgeJobProcessor
from pipeline.hpc.resource import ResourceSupply

LOCAL_PE = 'local'
MPI_PE = 'mpi'

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

gpu_resource_name = 'gpus'
mem_resource_name = 'ram'
exc_resource_name = 'exclusive'

instance_max_supply = ResourceSupply(cpu=2, gpu=3, mem=4)
cluster_max_supply = ResourceSupply(cpu=20, gpu=30, mem=40)

cmd_executor = Mock()
job_processor = SunGridEngineCustomRequestsPurgeJobProcessor(cmd_executor=cmd_executor,
                                                             gpu_resource_name=gpu_resource_name,
                                                             mem_resource_name=mem_resource_name,
                                                             exc_resource_name=exc_resource_name,
                                                             dry_run=False)


def setup_function():
    cmd_executor.execute = MagicMock()


def test_process_empty_jobs():
    jobs = [
    ]

    relevant_jobs, irrelevant_jobs = job_processor.process(jobs)

    assert not relevant_jobs
    assert not irrelevant_jobs


def test_process_jobs_without_custom_requests():
    jobs = [
        GridEngineJob(id='1', root_id='1', name='', user='', state='', datetime='',
                      cpu=2, gpu=3, mem=4, pe=LOCAL_PE),
        GridEngineJob(id='2', root_id='2', name='', user='', state='', datetime='',
                      cpu=20, gpu=30, mem=40, pe=MPI_PE),
    ]

    relevant_jobs, irrelevant_jobs = job_processor.process(jobs)

    assert relevant_jobs == jobs
    assert not irrelevant_jobs


def test_process_jobs_with_custom_requests():
    jobs = [
        GridEngineJob(id='1', root_id='1', name='', user='', state='', datetime='',
                      cpu=2, gpu=3, mem=4, requests={'A': '1.000000'}, pe=LOCAL_PE),
        GridEngineJob(id='2', root_id='2', name='', user='', state='', datetime='',
                      cpu=20, gpu=30, mem=40, requests={'A': '1.000000'}, pe=MPI_PE),
    ]

    relevant_jobs, irrelevant_jobs = job_processor.process(jobs)

    assert not relevant_jobs
    assert irrelevant_jobs == jobs

    calls = list(cmd_executor.execute.call_args_list)

    assert len(calls) == 2

    assert 'qalter ' in calls[0][0][0]
    assert ' 1' in calls[0][0][0]

    assert 'qalter ' in calls[1][0][0]
    assert ' 2' in calls[1][0][0]


def test_process_jobs_both_with_without_custom_requests():
    jobs = [
        GridEngineJob(id='1', root_id='1', name='', user='', state='', datetime='',
                      cpu=2, gpu=3, mem=4, pe=LOCAL_PE),
        GridEngineJob(id='2', root_id='2', name='', user='', state='', datetime='',
                      cpu=3, gpu=4, mem=5, requests={'A': '1.000000'}, pe=LOCAL_PE),
        GridEngineJob(id='3', root_id='3', name='', user='', state='', datetime='',
                      cpu=20, gpu=30, mem=40, pe=MPI_PE),
        GridEngineJob(id='4', root_id='4', name='', user='', state='', datetime='',
                      cpu=30, gpu=40, mem=50, requests={'A': '1.000000'}, pe=MPI_PE),
    ]

    relevant_jobs, irrelevant_jobs = job_processor.process(jobs)

    assert relevant_jobs == [jobs[0], jobs[2]]
    assert irrelevant_jobs == [jobs[1], jobs[3]]

    calls = list(cmd_executor.execute.call_args_list)

    assert len(calls) == 2

    assert 'qalter ' in calls[0][0][0]
    assert ' 2' in calls[0][0][0]

    assert 'qalter ' in calls[1][0][0]
    assert ' 4' in calls[1][0][0]
