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


from scripts.autoscale_sge import ComputeResource


def test_add():
    assert ComputeResource(cpu=2, gpu=3, memory=4, disk=5) \
           + ComputeResource(cpu=20, gpu=30, memory=40, disk=50) \
           == ComputeResource(cpu=22, gpu=33, memory=44, disk=55)


def test_sub():
    assert ComputeResource(cpu=22, gpu=33, memory=44, disk=55) \
           - ComputeResource(cpu=2, gpu=3, memory=4, disk=5) \
           == ComputeResource(cpu=20, gpu=30, memory=40, disk=50)
    assert ComputeResource(cpu=2, gpu=3, memory=4, disk=5) \
           - ComputeResource(cpu=22, gpu=33, memory=44, disk=55) \
           == ComputeResource(cpu=0, gpu=0, memory=0, disk=0)


def test_subtract():
    assert ComputeResource(cpu=22, gpu=33, memory=44, disk=55) \
               .subtract(ComputeResource(cpu=2, gpu=3, memory=4, disk=5)) \
           == (ComputeResource(cpu=20, gpu=30, memory=40, disk=50),
               ComputeResource(cpu=0, gpu=0, memory=0, disk=0))
    assert ComputeResource(cpu=2, gpu=3, memory=4, disk=5) \
               .subtract(ComputeResource(cpu=22, gpu=33, memory=44, disk=55)) \
           == (ComputeResource(cpu=0, gpu=0, memory=0, disk=0),
               ComputeResource(cpu=20, gpu=30, memory=40, disk=50))


def test_mul():
    assert ComputeResource(cpu=2, gpu=3, memory=4, disk=5) \
           * 10 \
           == ComputeResource(cpu=20, gpu=30, memory=40, disk=50)


def test_compare():
    assert ComputeResource(cpu=2, gpu=3, memory=4, disk=5) \
           < ComputeResource(cpu=20, gpu=30, memory=40, disk=50)
    assert ComputeResource(cpu=20, gpu=30, memory=40, disk=50) \
           > ComputeResource(cpu=2, gpu=3, memory=4, disk=5)


def test_equals():
    assert ComputeResource(cpu=2, gpu=3, memory=4, disk=5) \
           == ComputeResource(cpu=2, gpu=3, memory=4, disk=5)
    assert ComputeResource(cpu=2, gpu=3, memory=4, disk=5) \
           != ComputeResource(cpu=20, gpu=30, memory=40, disk=50)


def test_bool():
    assert ComputeResource(cpu=2, gpu=3, memory=4, disk=5)
    assert not ComputeResource(cpu=0, gpu=0, memory=0, disk=0)
