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

from pipeline.hpc.engine.sge import SunGridEngineStateWorkerValidatorHandler

logging.basicConfig(level=logging.DEBUG, format='%(asctime)s [%(threadName)s] [%(levelname)s] %(message)s')

HOST = 'pipeline-12345'

executor = Mock()
queue = 'queue.q'
handler = SunGridEngineStateWorkerValidatorHandler(cmd_executor=executor, queue=queue)


def setup_function():
    executor.execute = MagicMock()


def test_host_state_empty():
    stdout = """<?xml version='1.0'?>
<qhost xmlns:xsd="http://arc.liv.ac.uk/repos/darcs/sge/source/dist/util/resources/schemas/qhost/qhost.xsd">
 <host name='global'>
   <hostvalue name='arch_string'>-</hostvalue>
   <hostvalue name='num_proc'>-</hostvalue>
   <hostvalue name='m_socket'>-</hostvalue>
   <hostvalue name='m_core'>-</hostvalue>
   <hostvalue name='m_thread'>-</hostvalue>
   <hostvalue name='load_avg'>-</hostvalue>
   <hostvalue name='mem_total'>-</hostvalue>
   <hostvalue name='mem_used'>-</hostvalue>
   <hostvalue name='swap_total'>-</hostvalue>
   <hostvalue name='swap_used'>-</hostvalue>
 </host>
 <host name='pipeline-12345'>
   <hostvalue name='arch_string'>lx-amd64</hostvalue>
   <hostvalue name='num_proc'>2</hostvalue>
   <hostvalue name='m_socket'>1</hostvalue>
   <hostvalue name='m_core'>1</hostvalue>
   <hostvalue name='m_thread'>2</hostvalue>
   <hostvalue name='load_avg'>0.20</hostvalue>
   <hostvalue name='mem_total'>7.6G</hostvalue>
   <hostvalue name='mem_used'>918.6M</hostvalue>
   <hostvalue name='swap_total'>0.0</hostvalue>
   <hostvalue name='swap_used'>0.0</hostvalue>
 <queue name='queue.q'>
   <queuevalue qname='main.q' name='qtype_string'>BIP</queuevalue>
   <queuevalue qname='main.q' name='slots_used'>0</queuevalue>
   <queuevalue qname='main.q' name='slots'>2</queuevalue>
   <queuevalue qname='main.q' name='slots_resv'>0</queuevalue>
   <queuevalue qname='main.q' name='state_string'></queuevalue>
 </queue>
 </host>
</qhost>
    """

    executor.execute = MagicMock(return_value=stdout)

    assert handler.is_valid(HOST)


def test_host_state_unheard():
    stdout = """<?xml version='1.0'?>
<qhost xmlns:xsd="http://arc.liv.ac.uk/repos/darcs/sge/source/dist/util/resources/schemas/qhost/qhost.xsd">
 <host name='global'>
   <hostvalue name='arch_string'>-</hostvalue>
   <hostvalue name='num_proc'>-</hostvalue>
   <hostvalue name='m_socket'>-</hostvalue>
   <hostvalue name='m_core'>-</hostvalue>
   <hostvalue name='m_thread'>-</hostvalue>
   <hostvalue name='load_avg'>-</hostvalue>
   <hostvalue name='mem_total'>-</hostvalue>
   <hostvalue name='mem_used'>-</hostvalue>
   <hostvalue name='swap_total'>-</hostvalue>
   <hostvalue name='swap_used'>-</hostvalue>
 </host>
 <host name='pipeline-12345'>
   <hostvalue name='arch_string'>lx-amd64</hostvalue>
   <hostvalue name='num_proc'>2</hostvalue>
   <hostvalue name='m_socket'>1</hostvalue>
   <hostvalue name='m_core'>1</hostvalue>
   <hostvalue name='m_thread'>2</hostvalue>
   <hostvalue name='load_avg'>0.20</hostvalue>
   <hostvalue name='mem_total'>7.6G</hostvalue>
   <hostvalue name='mem_used'>918.6M</hostvalue>
   <hostvalue name='swap_total'>0.0</hostvalue>
   <hostvalue name='swap_used'>0.0</hostvalue>
 <queue name='queue.q'>
   <queuevalue qname='main.q' name='qtype_string'>BIP</queuevalue>
   <queuevalue qname='main.q' name='slots_used'>0</queuevalue>
   <queuevalue qname='main.q' name='slots'>2</queuevalue>
   <queuevalue qname='main.q' name='slots_resv'>0</queuevalue>
   <queuevalue qname='main.q' name='state_string'>u</queuevalue>
 </queue>
 </host>
</qhost>
    """

    executor.execute = MagicMock(return_value=stdout)

    assert not handler.is_valid(HOST)


def test_host_state_errored():
    stdout = """<?xml version='1.0'?>
<qhost xmlns:xsd="http://arc.liv.ac.uk/repos/darcs/sge/source/dist/util/resources/schemas/qhost/qhost.xsd">
 <host name='global'>
   <hostvalue name='arch_string'>-</hostvalue>
   <hostvalue name='num_proc'>-</hostvalue>
   <hostvalue name='m_socket'>-</hostvalue>
   <hostvalue name='m_core'>-</hostvalue>
   <hostvalue name='m_thread'>-</hostvalue>
   <hostvalue name='load_avg'>-</hostvalue>
   <hostvalue name='mem_total'>-</hostvalue>
   <hostvalue name='mem_used'>-</hostvalue>
   <hostvalue name='swap_total'>-</hostvalue>
   <hostvalue name='swap_used'>-</hostvalue>
 </host>
 <host name='pipeline-12345'>
   <hostvalue name='arch_string'>lx-amd64</hostvalue>
   <hostvalue name='num_proc'>2</hostvalue>
   <hostvalue name='m_socket'>1</hostvalue>
   <hostvalue name='m_core'>1</hostvalue>
   <hostvalue name='m_thread'>2</hostvalue>
   <hostvalue name='load_avg'>0.20</hostvalue>
   <hostvalue name='mem_total'>7.6G</hostvalue>
   <hostvalue name='mem_used'>918.6M</hostvalue>
   <hostvalue name='swap_total'>0.0</hostvalue>
   <hostvalue name='swap_used'>0.0</hostvalue>
 <queue name='queue.q'>
   <queuevalue qname='main.q' name='qtype_string'>BIP</queuevalue>
   <queuevalue qname='main.q' name='slots_used'>0</queuevalue>
   <queuevalue qname='main.q' name='slots'>2</queuevalue>
   <queuevalue qname='main.q' name='slots_resv'>0</queuevalue>
   <queuevalue qname='main.q' name='state_string'>E</queuevalue>
 </queue>
 </host>
</qhost>
    """

    executor.execute = MagicMock(return_value=stdout)

    assert not handler.is_valid(HOST)


def test_host_state_disabled():
    stdout = """<?xml version='1.0'?>
<qhost xmlns:xsd="http://arc.liv.ac.uk/repos/darcs/sge/source/dist/util/resources/schemas/qhost/qhost.xsd">
 <host name='global'>
   <hostvalue name='arch_string'>-</hostvalue>
   <hostvalue name='num_proc'>-</hostvalue>
   <hostvalue name='m_socket'>-</hostvalue>
   <hostvalue name='m_core'>-</hostvalue>
   <hostvalue name='m_thread'>-</hostvalue>
   <hostvalue name='load_avg'>-</hostvalue>
   <hostvalue name='mem_total'>-</hostvalue>
   <hostvalue name='mem_used'>-</hostvalue>
   <hostvalue name='swap_total'>-</hostvalue>
   <hostvalue name='swap_used'>-</hostvalue>
 </host>
 <host name='pipeline-12345'>
   <hostvalue name='arch_string'>lx-amd64</hostvalue>
   <hostvalue name='num_proc'>2</hostvalue>
   <hostvalue name='m_socket'>1</hostvalue>
   <hostvalue name='m_core'>1</hostvalue>
   <hostvalue name='m_thread'>2</hostvalue>
   <hostvalue name='load_avg'>0.20</hostvalue>
   <hostvalue name='mem_total'>7.6G</hostvalue>
   <hostvalue name='mem_used'>918.6M</hostvalue>
   <hostvalue name='swap_total'>0.0</hostvalue>
   <hostvalue name='swap_used'>0.0</hostvalue>
 <queue name='queue.q'>
   <queuevalue qname='main.q' name='qtype_string'>BIP</queuevalue>
   <queuevalue qname='main.q' name='slots_used'>0</queuevalue>
   <queuevalue qname='main.q' name='slots'>2</queuevalue>
   <queuevalue qname='main.q' name='slots_resv'>0</queuevalue>
   <queuevalue qname='main.q' name='state_string'>d</queuevalue>
 </queue>
 </host>
</qhost>
    """

    executor.execute = MagicMock(return_value=stdout)

    assert not handler.is_valid(HOST)


def test_host_state_unexpected():
    stdout = """<?xml version='1.0'?>
<qhost xmlns:xsd="http://arc.liv.ac.uk/repos/darcs/sge/source/dist/util/resources/schemas/qhost/qhost.xsd">
 <host name='global'>
   <hostvalue name='arch_string'>-</hostvalue>
   <hostvalue name='num_proc'>-</hostvalue>
   <hostvalue name='m_socket'>-</hostvalue>
   <hostvalue name='m_core'>-</hostvalue>
   <hostvalue name='m_thread'>-</hostvalue>
   <hostvalue name='load_avg'>-</hostvalue>
   <hostvalue name='mem_total'>-</hostvalue>
   <hostvalue name='mem_used'>-</hostvalue>
   <hostvalue name='swap_total'>-</hostvalue>
   <hostvalue name='swap_used'>-</hostvalue>
 </host>
 <host name='pipeline-12345'>
   <hostvalue name='arch_string'>lx-amd64</hostvalue>
   <hostvalue name='num_proc'>2</hostvalue>
   <hostvalue name='m_socket'>1</hostvalue>
   <hostvalue name='m_core'>1</hostvalue>
   <hostvalue name='m_thread'>2</hostvalue>
   <hostvalue name='load_avg'>0.20</hostvalue>
   <hostvalue name='mem_total'>7.6G</hostvalue>
   <hostvalue name='mem_used'>918.6M</hostvalue>
   <hostvalue name='swap_total'>0.0</hostvalue>
   <hostvalue name='swap_used'>0.0</hostvalue>
 <queue name='queue.q'>
   <queuevalue qname='main.q' name='qtype_string'>BIP</queuevalue>
   <queuevalue qname='main.q' name='slots_used'>0</queuevalue>
   <queuevalue qname='main.q' name='slots'>2</queuevalue>
   <queuevalue qname='main.q' name='slots_resv'>0</queuevalue>
   <queuevalue qname='main.q' name='state_string'>?</queuevalue>
 </queue>
 </host>
</qhost>
    """

    executor.execute = MagicMock(return_value=stdout)

    assert handler.is_valid(HOST)


def test_host_state_exception():
    def _fail(*args, **kwargs):
        raise RuntimeError("""
Any exception
        """)

    executor.execute = MagicMock(side_effect=_fail)

    assert not handler.is_valid(HOST)
