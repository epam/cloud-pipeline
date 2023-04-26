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

import logging

from mock import MagicMock, Mock

from scripts.autoscale_sge import GridEngineJob, GridEngineJobValidator, ResourceSupply, AllocationRule


LOCAL_PE = 'local'
MPI_PE = 'mpi'

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

MAX_CLUSTER_CORES = 6
MAX_INSTANCE_CORES = 2
QUEUE = 'main.q'
HOSTLIST = '@allhosts'
QUEUE_DEFAULT = True

instance_max_supply = ResourceSupply(cpu=2, gpu=3, memory=4)
cluster_max_supply = ResourceSupply(cpu=20, gpu=30, memory=40)


grid_engine = Mock()
job_validator = GridEngineJobValidator(grid_engine=grid_engine,
                                       instance_max_supply=instance_max_supply,
                                       cluster_max_supply=cluster_max_supply)


def setup_function():
    grid_engine.get_pe_allocation_rule = MagicMock(
        side_effect=lambda pe: AllocationRule.round_robin() if pe == MPI_PE else AllocationRule.pe_slots())


def test_validate_empty_jobs():
    jobs = [
    ]

    valid_jobs, invalid_jobs = job_validator.validate(jobs)

    assert not valid_jobs
    assert not invalid_jobs


def test_validate_valid_jobs():
    jobs = [
        GridEngineJob(id='1', root_id=1, name='', user='', state='', datetime='',
                      slots=2, gpus=3, mem=4, pe=LOCAL_PE),
        GridEngineJob(id='2', root_id=2, name='', user='', state='', datetime='',
                      slots=20, gpus=30, mem=40, pe=MPI_PE),
    ]

    valid_jobs, invalid_jobs = job_validator.validate(jobs)

    assert valid_jobs == jobs
    assert not invalid_jobs


def test_validate_invalid_jobs():
    jobs = [
        GridEngineJob(id='1', root_id=1, name='', user='', state='', datetime='',
                      slots=3, gpus=4, mem=5, pe=LOCAL_PE),
        GridEngineJob(id='2', root_id=2, name='', user='', state='', datetime='',
                      slots=30, gpus=40, mem=50, pe=MPI_PE),
    ]

    valid_jobs, invalid_jobs = job_validator.validate(jobs)

    assert not valid_jobs
    assert invalid_jobs == jobs


def test_validate_both_valid_invalid_jobs():
    jobs = [
        GridEngineJob(id='1', root_id=1, name='', user='', state='', datetime='',
                      slots=2, gpus=3, mem=4, pe=LOCAL_PE),
        GridEngineJob(id='2', root_id=2, name='', user='', state='', datetime='',
                      slots=3, gpus=4, mem=5, pe=LOCAL_PE),
        GridEngineJob(id='3', root_id=3, name='', user='', state='', datetime='',
                      slots=20, gpus=30, mem=40, pe=MPI_PE),
        GridEngineJob(id='4', root_id=4, name='', user='', state='', datetime='',
                      slots=30, gpus=40, mem=50, pe=MPI_PE),
    ]

    valid_jobs, invalid_jobs = job_validator.validate(jobs)

    assert valid_jobs == [jobs[0], jobs[2]]
    assert invalid_jobs == [jobs[1], jobs[3]]
