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


from pipeline.hpc.resource import CustomComputeResource


def test_add():
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
           + CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40}) \
           == CustomComputeResource(values={'a': 22, 'b': 33, 'c': 44})
    assert CustomComputeResource(values={'a': 2}) \
           + CustomComputeResource(values={'b': 30}) \
           == CustomComputeResource(values={'a': 2, 'b': 30})
    assert CustomComputeResource(values={'a': 2, 'b': 3}) \
           + CustomComputeResource(values={'b': 30, 'c': 40}) \
           == CustomComputeResource(values={'a': 2, 'b': 33, 'c': 40})


def test_sub():
    assert CustomComputeResource(values={'a': 22, 'b': 33, 'c': 44}) \
           - CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
           == CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40})
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
           - CustomComputeResource(values={'a': 22, 'b': 33, 'c': 44}) \
           == CustomComputeResource(values={'a': 0, 'b': 0, 'c': 0})


def test_subtract():
    assert CustomComputeResource(values={'a': 22, 'b': 33, 'c': 44}) \
               .subtract(CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4})) \
           == (CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40}),
               CustomComputeResource(values={'a': 0, 'b': 0, 'c': 0}))
    assert CustomComputeResource(values={'a': 22}) \
               .subtract(CustomComputeResource(values={'b': 3})) \
           == (CustomComputeResource(values={'a': 22}),
               CustomComputeResource(values={'a': 0, 'b': 3}))
    assert CustomComputeResource(values={'a': 22, 'b': 33}) \
               .subtract(CustomComputeResource(values={'b': 3, 'c': 4})) \
           == (CustomComputeResource(values={'a': 22, 'b': 30}),
               CustomComputeResource(values={'a': 0, 'b': 0, 'c': 4}))
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
               .subtract(CustomComputeResource(values={'a': 22, 'b': 33, 'c': 44})) \
           == (CustomComputeResource(values={'a': 0, 'b': 0, 'c': 0}),
               CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40}))


def test_mul():
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
           * 10 \
           == CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40})


def test_cmp():
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
           < CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40})
    assert not CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
               < CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4})
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
           <= CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4})
    assert CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40}) \
           > CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4})
    assert not CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40}) \
               > CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40})
    assert CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40}) \
           >= CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40})


def test_eq():
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
           == CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4})
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4}) \
           != CustomComputeResource(values={'a': 20, 'b': 30, 'c': 40})


def test_bool():
    assert CustomComputeResource(values={'a': 2, 'b': 3, 'c': 4})
    assert not CustomComputeResource(values={'a': 0, 'b': 0, 'c': 0})
