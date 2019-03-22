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

import os
import tempfile

import pytest

from pytest import fail

from scripts.autoscale_sge import FileSystemHostStorage, MemoryHostStorage, CmdExecutor, ScalingError

HOST1 = "host1"
HOST2 = "host2"

cmd_executor = CmdExecutor()
_, storage_file = tempfile.mkstemp()
host_storage = FileSystemHostStorage(cmd_executor=cmd_executor, storage_file=storage_file)


@pytest.fixture(params=[
    MemoryHostStorage(),
    FileSystemHostStorage(cmd_executor=cmd_executor, storage_file=storage_file)
])
def host_storage(request):
    host_storage = request.param
    yield host_storage
    host_storage.clear()


def teardown_module():
    os.remove(storage_file)


def test_host_addition(host_storage):
    host_storage.add_host(HOST1)

    hosts = host_storage.load_hosts()

    assert HOST1 in hosts


def test_several_hosts_addition(host_storage):
    host_storage.add_host(HOST1)
    host_storage.add_host(HOST2)

    hosts = host_storage.load_hosts()

    assert HOST1 in hosts
    assert HOST2 in hosts


def test_same_host_addition_fails(host_storage):
    host_storage.add_host(HOST1)

    try:
        host_storage.add_host(HOST1)
        fail('Addition of the same host should fail')
    except ScalingError:
        pass

    hosts = host_storage.load_hosts()

    assert len(hosts) == 1
    assert HOST1 in hosts


def test_host_removal(host_storage):
    host_storage.add_host(HOST1)
    host_storage.add_host(HOST2)

    host_storage.remove_host(HOST1)

    hosts = host_storage.load_hosts()

    assert HOST1 not in hosts
    assert HOST2 in hosts


def test_non_existing_host_removal(host_storage):
    try:
        host_storage.remove_host(HOST1)
        fail('Removal of non existing host should fail')
    except ScalingError:
        pass
