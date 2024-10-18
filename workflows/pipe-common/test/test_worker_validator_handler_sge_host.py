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

import logging

from mock import Mock, MagicMock

from pipeline.hpc.engine.sge import SunGridEngineHostWorkerValidatorHandler

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

HOST = 'pipeline-12345'

executor = Mock()
handler = SunGridEngineHostWorkerValidatorHandler(cmd_executor=executor)


def setup_function():
    executor.execute = MagicMock()


def test_existing_host():
    stdout = """
hostname              pipeline-12345
load_scaling          NONE
complex_values        gpus=0,ram=7G,n_cpu=2,exclusive=true
load_values           load_avg=0.240000,load_short=0.180000, \
                      load_medium=0.240000,load_long=0.260000,arch=lx-amd64, \
                      num_proc=2,mem_free=6846.859375M,swap_free=0.000000M, \
                      virtual_free=6846.859375M,mem_total=7764.621094M, \
                      swap_total=0.000000M,virtual_total=7764.621094M, \
                      mem_used=917.761719M,swap_used=0.000000M, \
                      virtual_used=917.761719M,cpu=10.500000,m_topology=SCTT, \
                      m_topology_inuse=SCTT,m_socket=1,m_core=1,m_thread=2, \
                      np_load_avg=0.120000,np_load_short=0.090000, \
                      np_load_medium=0.120000,np_load_long=0.130000
processors            2
user_lists            NONE
xuser_lists           NONE
projects              NONE
xprojects             NONE
usage_scaling         NONE
report_variables      NONE
    """

    executor.execute = MagicMock(return_value=stdout)

    assert handler.is_valid(HOST)


def test_missing_host():
    def _fail(*args, **kwargs):
        raise RuntimeError("""
HOST is not an execution host
        """)

    executor.execute = MagicMock(side_effect=_fail)

    assert not handler.is_valid(HOST)
