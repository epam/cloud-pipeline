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


from pipeline.hpc.autoscaler import ComputeResource


def test_add():
    assert ComputeResource(cpu=2, gpu=3, mem=4) \
           + ComputeResource(cpu=20, gpu=30, mem=40) \
           == ComputeResource(cpu=22, gpu=33, mem=44)


def test_sub():
    assert ComputeResource(cpu=22, gpu=33, mem=44) \
           - ComputeResource(cpu=2, gpu=3, mem=4) \
           == ComputeResource(cpu=20, gpu=30, mem=40)
    assert ComputeResource(cpu=2, gpu=3, mem=4) \
           - ComputeResource(cpu=22, gpu=33, mem=44) \
           == ComputeResource(cpu=0, gpu=0, mem=0)


def test_subtract():
    assert ComputeResource(cpu=22, gpu=33, mem=44) \
               .subtract(ComputeResource(cpu=2, gpu=3, mem=4)) \
           == (ComputeResource(cpu=20, gpu=30, mem=40),
               ComputeResource(cpu=0, gpu=0, mem=0))
    assert ComputeResource(cpu=2, gpu=3, mem=4) \
               .subtract(ComputeResource(cpu=22, gpu=33, mem=44)) \
           == (ComputeResource(cpu=0, gpu=0, mem=0),
               ComputeResource(cpu=20, gpu=30, mem=40))


def test_mul():
    assert ComputeResource(cpu=2, gpu=3, mem=4) \
           * 10 \
           == ComputeResource(cpu=20, gpu=30, mem=40)


def test_cmp():
    assert ComputeResource(cpu=2, gpu=3, mem=4) \
           < ComputeResource(cpu=20, gpu=30, mem=40)
    assert not ComputeResource(cpu=2, gpu=3, mem=4) \
           < ComputeResource(cpu=2, gpu=3, mem=4)
    assert ComputeResource(cpu=2, gpu=3, mem=4) \
           <= ComputeResource(cpu=2, gpu=3, mem=4)
    assert ComputeResource(cpu=20, gpu=30, mem=40) \
           > ComputeResource(cpu=2, gpu=3, mem=4)
    assert not ComputeResource(cpu=20, gpu=30, mem=40) \
           > ComputeResource(cpu=20, gpu=30, mem=40)
    assert ComputeResource(cpu=20, gpu=30, mem=40) \
           >= ComputeResource(cpu=20, gpu=30, mem=40)


def test_eq():
    assert ComputeResource(cpu=2, gpu=3, mem=4) \
           == ComputeResource(cpu=2, gpu=3, mem=4)
    assert ComputeResource(cpu=2, gpu=3, mem=4) \
           != ComputeResource(cpu=20, gpu=30, mem=40)


def test_bool():
    assert ComputeResource(cpu=2, gpu=3, mem=4)
    assert not ComputeResource(cpu=0, gpu=0, mem=0)
