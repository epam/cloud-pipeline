# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

from mock import MagicMock, patch

from pipeline.autoscaling import kubeprovider
from pipeline.autoscaling.kubeprovider import KubeProvider

INSTANCE_ID = 'i-12345'
NODE_NAME = 'ip-10-0-0-1'
NODE_NAME_FULL = 'ip-10-0-0-1.internal'
NUM_REP = 3
TIME_REP = 0


def condition(type, status):
    return {'type': type, 'status': status}


# a network plugin adds its own condition first, so Ready is the fifth one rather than the fourth
READY_NODE_CONDITIONS = [
    condition('NetworkUnavailable', 'False'),
    condition('MemoryPressure', 'False'),
    condition('DiskPressure', 'False'),
    condition('PIDPressure', 'False'),
    condition('Ready', 'True'),
]


def test_node_is_ready_whatever_the_position_of_ready_condition():
    assert KubeProvider.is_node_ready(READY_NODE_CONDITIONS)
    assert KubeProvider.is_node_ready(list(reversed(READY_NODE_CONDITIONS)))


def test_node_is_not_ready_unless_ready_condition_is_true():
    assert not KubeProvider.is_node_ready(READY_NODE_CONDITIONS[:-1] + [condition('Ready', 'False')])
    assert not KubeProvider.is_node_ready(READY_NODE_CONDITIONS[:-1] + [condition('Ready', 'Unknown')])


def test_node_is_not_ready_without_ready_condition():
    assert not KubeProvider.is_node_ready(READY_NODE_CONDITIONS[:-1])
    assert not KubeProvider.is_node_ready([])


# patched on the module object, since another test replaces the pipeline package in sys.modules
@patch.object(kubeprovider, 'sleep')
@patch.object(kubeprovider, 'utils')
@patch.object(kubeprovider, 'pykube')
def test_node_registration_waits_for_ready_condition_by_its_type(pykube, utils, _):
    nodes = MagicMock()
    nodes.response = {'items': [{'status': {'conditions': READY_NODE_CONDITIONS}}]}
    pykube.Node.objects.return_value.filter.return_value = nodes
    pods = MagicMock()
    pods.response = {'items': []}
    pods.__iter__.return_value = iter([])
    pykube.objects.Pod.objects.return_value.filter.return_value = pods
    utils.increment_or_fail.side_effect = lambda num_rep, rep, message: rep + 1

    provider = KubeProvider.__new__(KubeProvider)
    provider.api = MagicMock()
    provider.find_node = MagicMock(return_value=NODE_NAME)

    assert provider.verify_regnode(INSTANCE_ID, NODE_NAME, NODE_NAME_FULL, NUM_REP, TIME_REP) == NODE_NAME
    utils.increment_or_fail.assert_not_called()
