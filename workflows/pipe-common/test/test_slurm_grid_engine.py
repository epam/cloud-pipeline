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

from pipeline.hpc.engine.gridengine import GridEngineJobState, GridEngineJob
from pipeline.hpc.engine.slurm import SlurmGridEngine
from utils import assert_first_argument_contained, assert_first_argument_not_contained

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

QUEUE = 'main.q'
HOSTLIST = '@allhosts'
QUEUE_DEFAULT = True

executor = Mock()
grid_engine = SlurmGridEngine(cmd_executor=executor)


def setup_function():
    executor.execute = MagicMock()


def test_qstat_parsing():
    stdout = """JobId=8 JobName=wrap1 UserId=root(0) GroupId=root(0) MCS_label=N/A Priority=4294901752 Nice=0 Account=(null) QOS=(null) JobState=RUNNING Reason=None Dependency=(null) Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0 RunTime=00:00:15 TimeLimit=365-00:00:00 TimeMin=N/A SubmitTime=2023-07-31T13:55:30 EligibleTime=2023-07-31T13:55:30 AccrueTime=2023-07-31T13:55:30 StartTime=2023-07-31T13:55:30 EndTime=2024-07-30T13:55:30 Deadline=N/A SuspendTime=None SecsPreSuspend=0 LastSchedEval=2023-07-31T13:55:30 Scheduler=Backfill Partition=main.q AllocNode:Sid=pipeline-47608:6013 ReqNodeList=(null) ExcNodeList=(null) NodeList=pipeline-47608 BatchHost=pipeline-47608 NumNodes=1 NumCPUs=1 NumTasks=1 CPUs/Task=1 ReqB:S:C:T=0:0:*:* TRES=cpu=1,node=1,billing=1 Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=* MinCPUsNode=1 MinMemoryNode=0 MinTmpDiskNode=0 Features=(null) DelayBoot=00:00:00 OverSubscribe=OK Contiguous=0 Licenses=(null) Network=(null) Command=(null) WorkDir=/root StdErr=/root/slurm-8.out StdIn=/dev/null StdOut=/root/slurm-8.out Power=
JobId=9 JobName=wrap2 UserId=root(0) GroupId=root(0) MCS_label=N/A Priority=4294901752 Nice=0 Account=(null) QOS=(null) JobState=PENDING Reason=None Dependency=(null) Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0 RunTime=00:00:15 TimeLimit=365-00:00:00 TimeMin=N/A SubmitTime=2023-07-31T13:55:35 EligibleTime=2023-07-31T13:55:35 AccrueTime=Unknown StartTime=Unknown EndTime=2024-07-30T13:55:30 Deadline=N/A SuspendTime=None SecsPreSuspend=0 LastSchedEval=2023-07-31T13:55:30 Scheduler=Backfill Partition=main.q AllocNode:Sid=pipeline-47608:6013 ReqNodeList=(null) ExcNodeList=(null) NodeList= BatchHost=pipeline-47608 NumNodes=1 NumCPUs=2 NumTasks=1 CPUs/Task=2 ReqB:S:C:T=0:0:*:* TRES=cpu=2,node=1,billing=1 Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=* MinCPUsNode=2 MinMemoryNode=500M MinTmpDiskNode=0 Features=(null) DelayBoot=00:00:00 OverSubscribe=OK Contiguous=0 Licenses=(null) Network=(null) Command=(null) WorkDir=/root StdErr=/root/slurm-8.out StdIn=/dev/null StdOut=/root/slurm-8.out Power=
JobId=10 JobName=wrap3 UserId=user-name(123) GroupId=root(0) MCS_label=N/A Priority=4294901752 Nice=0 Account=(null) QOS=(null) JobState=PENDING Reason=None Dependency=(null) Requeue=1 Restarts=0 BatchFlag=1 Reboot=0 ExitCode=0:0 RunTime=00:00:15 TimeLimit=365-00:00:00 TimeMin=N/A SubmitTime=2023-07-31T13:55:35 EligibleTime=2023-07-31T13:55:35 AccrueTime=Unknown StartTime=Unknown EndTime=2024-07-30T13:55:30 Deadline=N/A SuspendTime=None SecsPreSuspend=0 LastSchedEval=2023-07-31T13:55:30 Scheduler=Backfill Partition=main.q AllocNode:Sid=pipeline-47608:6013 ReqNodeList=(null) ExcNodeList=(null) NodeList= BatchHost=pipeline-47608 NumNodes=1 NumCPUs=2 NumTasks=1 CPUs/Task=2 ReqB:S:C:T=0:0:*:* TRES=cpu=2,node=1,billing=1 Socks/Node=* NtasksPerN:B:S:C=0:0:*:* CoreSpec=* MinCPUsNode=2 MinMemoryNode=500M MinTmpDiskNode=0 Features=(null) DelayBoot=00:00:00 OverSubscribe=OK Contiguous=0 Licenses=(null) Network=(null) Command=(null) WorkDir=/root StdErr=/root/slurm-8.out StdIn=/dev/null StdOut=/root/slurm-8.out Power=
    """

    executor.execute = MagicMock(return_value=stdout)
    jobs = grid_engine.get_jobs()

    assert len(jobs) == 3

    job_1, job_2, job_3 = jobs

    assert job_1.root_id == '8'
    assert job_1.id == '8_0'
    assert job_1.name == 'wrap1'
    assert job_1.user == 'root'
    assert job_1.state == GridEngineJobState.RUNNING
    assert job_1.cpu == 1
    assert job_1.gpu == 0
    assert job_1.mem == 0
    assert job_1.datetime == datetime(2023, 7, 31,
                                      13, 55, 30)

    assert job_2.root_id == '9'
    assert job_2.id == '9_0'
    assert job_2.name == 'wrap2'
    assert job_2.user == 'root'
    assert job_2.state == GridEngineJobState.PENDING
    assert job_2.cpu == 2
    assert job_2.gpu == 0
    assert job_2.mem == 1
    assert job_2.datetime == datetime(2023, 7, 31,
                                      13, 55, 35)

    assert job_3.root_id == '10'
    assert job_3.id == '10_0'
    assert job_3.name == 'wrap3'
    assert job_3.user == 'user-name'
    assert job_3.state == GridEngineJobState.PENDING
    assert job_3.cpu == 2
    assert job_3.gpu == 0
    assert job_3.mem == 1
    assert job_3.datetime == datetime(2023, 7, 31,
                                      13, 55, 35)


def test_qstat_empty_parsing():
    stdout = """No jobs in the system
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
    assert_first_argument_contained(executor.execute, 'scancel ')
    assert_first_argument_contained(executor.execute, ' 1 2')
    assert_first_argument_not_contained(executor.execute, '-f')


def test_force_kill_jobs():
    jobs = [
        GridEngineJob(id='1', root_id=1, name='', user='', state='', datetime=''),
        GridEngineJob(id='2', root_id=2, name='', user='', state='', datetime='')
    ]

    grid_engine.kill_jobs(jobs, force=True)
    assert_first_argument_contained(executor.execute, 'scancel ')
    assert_first_argument_contained(executor.execute, ' 1 2')
    assert_first_argument_contained(executor.execute, '-f')
