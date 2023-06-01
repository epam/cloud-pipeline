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

from datetime import datetime
from mock import MagicMock, Mock

from pipeline.hpc.autoscaler import GridEngine, GridEngineJobState, GridEngineJob
from utils import assert_first_argument_contained, assert_first_argument_not_contained

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

QUEUE = 'main.q'
HOSTLIST = '@allhosts'
QUEUE_DEFAULT = True

executor = Mock()
grid_engine = GridEngine(cmd_executor=executor,
                         queue=QUEUE, hostlist=HOSTLIST, queue_default=QUEUE_DEFAULT)


def setup_function():
    executor.execute = MagicMock()


def test_qstat_parsing():
    stdout = """<?xml version='1.0'?>
<job_info  xmlns:xsd="http://arc.liv.ac.uk/repos/darcs/sge/source/dist/util/resources/schemas/qstat/qstat.xsd">
  <queue_info>
    <Queue-List>
      <name>main.q@pipeline-38415</name>
      <qtype>BIP</qtype>
      <slots_used>2</slots_used>
      <slots_resv>0</slots_resv>
      <slots_total>4</slots_total>
      <load_avg>1.00000</load_avg>
      <arch>lx-amd64</arch>
      <job_list state="running">
        <JB_job_number>1</JB_job_number>
        <JAT_prio>0.75000</JAT_prio>
        <JB_name>name1</JB_name>
        <JB_owner>root</JB_owner>
        <state>r</state>
        <JAT_start_time>2018-12-21T11:48:00</JAT_start_time>
        <slots>2</slots>
        <full_job_name>sleep</full_job_name>
        <requested_pe name="local">2</requested_pe>
        <granted_pe name="local">2</granted_pe>
        <hard_req_queue>main.q</hard_req_queue>
        <binding>NONE</binding>
      </job_list>
    </Queue-List>
  </queue_info>
  <job_info>
    <job_list state="pending">
      <JB_job_number>2</JB_job_number>
      <JAT_prio>0.25000</JAT_prio>
      <JB_name>name2</JB_name>
      <JB_owner>someUser</JB_owner>
      <state>qw</state>
      <JB_submission_time>2018-12-21T12:39:38</JB_submission_time>
      <slots>3</slots>
      <full_job_name>sleep</full_job_name>
      <requested_pe name="local">3</requested_pe>
      <hard_request name="gpus" resource_contribution="0.000000">4</hard_request>
      <hard_request name="ram" resource_contribution="0.000000">5G</hard_request>
      <hard_req_queue>main.q</hard_req_queue>
      <binding>NONE</binding>
    </job_list>
  </job_info>
</job_info>
    """

    executor.execute = MagicMock(return_value=stdout)
    jobs = grid_engine.get_jobs()

    assert len(jobs) == 2

    job_1, job_2 = jobs

    assert job_1.name == 'name1'
    assert job_1.user == 'root'
    assert job_1.state == GridEngineJobState.RUNNING
    assert job_1.cpu == 2
    assert job_1.gpu == 0
    assert job_1.mem == 0
    assert job_1.datetime == datetime(2018, 12, 21,
                                      11, 48, 00)
    assert 'pipeline-38415' in job_1.hosts

    assert job_2.name == 'name2'
    assert job_2.user == 'someUser'
    assert job_2.state == GridEngineJobState.PENDING
    assert job_2.cpu == 3
    assert job_2.gpu == 4
    assert job_2.mem == 5
    assert job_2.datetime == datetime(2018, 12, 21,
                                      12, 39, 38)
    assert len(job_2.hosts) == 0


def test_qstat_array_job_parsing():
    stdout = """<?xml version='1.0'?>
<job_info  xmlns:xsd="http://arc.liv.ac.uk/repos/darcs/sge/source/dist/util/resources/schemas/qstat/qstat.xsd">
  <queue_info>
    <Queue-List>
      <name>main.q@pipeline-38415</name>
      <qtype>BIP</qtype>
      <slots_used>2</slots_used>
      <slots_resv>0</slots_resv>
      <slots_total>2</slots_total>
      <load_avg>1.00000</load_avg>
      <arch>lx-amd64</arch>
      <job_list state="running">
        <JB_job_number>1</JB_job_number>
        <JAT_prio>0.75000</JAT_prio>
        <JB_name>name1</JB_name>
        <JB_owner>root</JB_owner>
        <state>r</state>
        <JAT_start_time>2018-12-21T11:48:00</JAT_start_time>
        <slots>2</slots>
        <full_job_name>sleep</full_job_name>
        <requested_pe name="local">2</requested_pe>
        <granted_pe name="local">2</granted_pe>
        <hard_req_queue>main.q</hard_req_queue>
        <binding>NONE</binding>
      </job_list>
      <job_list state="running">
        <JB_job_number>2</JB_job_number>
        <JAT_prio>0.25000</JAT_prio>
        <JB_name>name2</JB_name>
        <JB_owner>root</JB_owner>
        <state>r</state>
        <JAT_start_time>2018-12-21T14:51:43</JAT_start_time>
        <slots>1</slots>
        <tasks>1</tasks>
        <full_job_name>sleep</full_job_name>
        <requested_pe name="local">1</requested_pe>
        <granted_pe name="local">1</granted_pe>
        <hard_req_queue>main.q</hard_req_queue>
        <binding>NONE</binding>
      </job_list>
    </Queue-List>
  </queue_info>
  <job_info>
    <job_list state="pending">
      <JB_job_number>2</JB_job_number>
      <JAT_prio>0.25000</JAT_prio>
      <JB_name>name2</JB_name>
      <JB_owner>root</JB_owner>
      <state>qw</state>
      <JB_submission_time>2018-12-21T14:51:43</JB_submission_time>
      <slots>1</slots>
      <tasks>2-10:1</tasks>
      <full_job_name>sleep</full_job_name>
      <requested_pe name="local">1</requested_pe>
      <hard_req_queue>main.q</hard_req_queue>
      <binding>NONE</binding>
    </job_list>
    <job_list state="pending">
      <JB_job_number>3</JB_job_number>
      <JAT_prio>0.25000</JAT_prio>
      <JB_name>name3</JB_name>
      <JB_owner>root</JB_owner>
      <state>qw</state>
      <JB_submission_time>2018-12-21T14:51:43</JB_submission_time>
      <slots>1</slots>
      <tasks>1-5:1</tasks>
      <full_job_name>sleep</full_job_name>
      <requested_pe name="local">1</requested_pe>
      <hard_req_queue>main.q</hard_req_queue>
      <binding>NONE</binding>
    </job_list>
    <job_list state="pending">
      <JB_job_number>4</JB_job_number>
      <JAT_prio>0.25000</JAT_prio>
      <JB_name>name3</JB_name>
      <JB_owner>root</JB_owner>
      <state>qw</state>
      <JB_submission_time>2018-12-21T14:51:43</JB_submission_time>
      <slots>1</slots>
      <tasks>8,9</tasks>
      <full_job_name>sleep</full_job_name>
      <requested_pe name="local">1</requested_pe>
      <hard_req_queue>main.q</hard_req_queue>
      <binding>NONE</binding>
    </job_list>
  </job_info>
</job_info>
    """

    executor.execute = MagicMock(return_value=stdout)
    jobs = grid_engine.get_jobs()

    assert len([job for job in jobs if '2.' in job.id]) == 10
    assert len([job for job in jobs if '3.' in job.id]) == 5
    assert len([job for job in jobs if '4.' in job.id]) == 2


def test_qstat_empty_parsing():
    stdout = """<?xml version='1.0'?>
<job_info  xmlns:xsd="http://arc.liv.ac.uk/repos/darcs/sge/source/dist/util/resources/schemas/qstat/qstat.xsd">
  <queue_info>
    <Queue-List>
      <name>main.q@pipeline-38415</name>
      <qtype>BIP</qtype>
      <slots_used>0</slots_used>
      <slots_resv>0</slots_resv>
      <slots_total>2</slots_total>
      <load_avg>0.34000</load_avg>
      <arch>lx-amd64</arch>
    </Queue-List>
  </queue_info>
  <job_info>
  </job_info>
</job_info>
    """

    executor.execute = MagicMock(return_value=stdout)
    jobs = grid_engine.get_jobs()

    assert len(jobs) == 0


def test_kill_jobs():
    jobs = [
        GridEngineJob(id='1', root_id=1, name='', user='', state='', datetime=''),
        GridEngineJob(id='2', root_id=2, name='', user='', state='', datetime='')
    ]

    grid_engine.kill_jobs(jobs)
    assert_first_argument_contained(executor.execute, 'qdel ')
    assert_first_argument_contained(executor.execute, ' 1 2')
    assert_first_argument_not_contained(executor.execute, '-f')


def test_force_kill_jobs():
    jobs = [
        GridEngineJob(id='1', root_id=1, name='', user='', state='', datetime=''),
        GridEngineJob(id='2', root_id=2, name='', user='', state='', datetime='')
    ]

    grid_engine.kill_jobs(jobs, force=True)
    assert_first_argument_contained(executor.execute, 'qdel ')
    assert_first_argument_contained(executor.execute, ' 1 2')
    assert_first_argument_contained(executor.execute, '-f')
