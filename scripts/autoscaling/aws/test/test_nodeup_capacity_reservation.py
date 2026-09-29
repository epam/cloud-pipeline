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

"""
Where a node consuming a capacity reservation is launched.

A reservation exists in exactly one availability zone, and an instance anywhere else does not consume it. Every way
the launch picks a network - an explicit subnet, the configured networks, a performance network, none at all - has
to end up in that zone, or refuse. A launch into another zone is the failure worth pinning, because it is silent.
"""

from collections import OrderedDict

import pytest

try:
    from unittest.mock import MagicMock, patch
except ImportError:
    from mock import MagicMock, patch

import aws_nodeup

RESERVATION_ID = 'cr-0123456789abcdef0'
ZONE = 'us-east-1c'
OTHER_ZONE = 'us-east-1a'
SUBNET_IN_ZONE = 'subnet-in-zone'
SUBNET_ELSEWHERE = 'subnet-elsewhere'
NETWORKS = {OTHER_ZONE: SUBNET_ELSEWHERE, ZONE: SUBNET_IN_ZONE}


def _first(sequence):
    return sequence[0]


def _ec2(subnet_zones=None, enis=None):
    ec2 = MagicMock()
    ec2.run_instances.return_value = {'Instances': [{'InstanceId': 'i-1', 'PrivateIpAddress': '10.0.0.1'}]}

    def describe_subnets(SubnetIds=None, Filters=None):
        zones = subnet_zones or {}
        if SubnetIds:
            return {'Subnets': [{'SubnetId': s, 'AvailabilityZone': zones[s]} for s in SubnetIds]}
        wanted = Filters[0]['Values'] if Filters else None
        return {'Subnets': [{'SubnetId': s, 'AvailabilityZone': z} for s, z in zones.items()
                            if wanted is None or z in wanted]}

    ec2.describe_subnets.side_effect = describe_subnets
    ec2.describe_network_interfaces.side_effect = lambda NetworkInterfaceIds: {
        'NetworkInterfaces': [enis[eni] for eni in NetworkInterfaceIds]
    }
    return ec2


def _launch(ec2, networks=None, capacity_reservation_id=RESERVATION_ID, availability_zone=ZONE,
            subnet=None, is_dedicated=False, performance_network=False, network_interface=None):
    with patch.object(aws_nodeup, 'get_networks_config', return_value=networks or {}), \
            patch.object(aws_nodeup, 'get_security_groups', return_value=['sg-1']), \
            patch.object(aws_nodeup, 'get_block_devices', return_value=[]), \
            patch.object(aws_nodeup, 'get_tags', return_value=[]), \
            patch.object(aws_nodeup, 'resource_tags', return_value=[]), \
            patch.object(aws_nodeup, 'get_current_status', return_value=aws_nodeup.RUNNING), \
            patch.object(aws_nodeup.random, 'choice', side_effect=_first), \
            patch.object(aws_nodeup, 'pipe_log'):
        aws_nodeup.run_on_demand_instance(ec2, 'region', 'ami-1', 'key', 'm5.large', 50, None, 1, 2, 'script',
                                          1, 0, None, None, None, availability_zone, None, subnet, network_interface,
                                          is_dedicated, performance_network, None,
                                          capacity_reservation_id=capacity_reservation_id)
    return ec2.run_instances.call_args[1]


def test_targets_the_reservation():
    launch_args = _launch(_ec2(), networks=NETWORKS)

    assert launch_args['CapacityReservationSpecification'] == {
        'CapacityReservationTarget': {'CapacityReservationId': RESERVATION_ID}
    }


def test_uses_the_configured_subnet_of_the_reservation_zone():
    launch_args = _launch(_ec2(), networks=NETWORKS)

    assert launch_args['SubnetId'] == SUBNET_IN_ZONE
    assert 'Placement' not in launch_args


def test_refuses_when_no_usable_subnet_is_configured_for_the_reservation_zone():
    ec2 = _ec2()

    with pytest.raises(RuntimeError, match=ZONE):
        _launch(ec2, networks={OTHER_ZONE: SUBNET_ELSEWHERE})

    ec2.run_instances.assert_not_called()


def test_refuses_an_explicit_subnet_in_another_zone():
    ec2 = _ec2(subnet_zones={SUBNET_ELSEWHERE: OTHER_ZONE})

    with pytest.raises(RuntimeError, match=SUBNET_ELSEWHERE):
        _launch(ec2, networks=NETWORKS, subnet=SUBNET_ELSEWHERE)

    ec2.run_instances.assert_not_called()


def test_keeps_an_explicit_subnet_in_the_reservation_zone():
    launch_args = _launch(_ec2(subnet_zones={SUBNET_IN_ZONE: ZONE}), networks=NETWORKS, subnet=SUBNET_IN_ZONE)

    assert launch_args['SubnetId'] == SUBNET_IN_ZONE


def test_pins_the_zone_when_no_networks_are_configured():
    launch_args = _launch(_ec2())

    assert launch_args['Placement']['AvailabilityZone'] == ZONE
    assert 'SubnetId' not in launch_args


def test_keeps_dedicated_tenancy_while_pinning_the_zone():
    launch_args = _launch(_ec2(), is_dedicated=True)

    assert launch_args['Placement'] == {'Tenancy': 'dedicated', 'AvailabilityZone': ZONE}


# Listed with the out-of-zone subnet first: the random pick is fixed to the first subnet it is offered, so a pick
# that is not restricted to the reservation zone lands elsewhere every time.
ACCOUNT_SUBNETS = OrderedDict([(SUBNET_ELSEWHERE, OTHER_ZONE), (SUBNET_IN_ZONE, ZONE)])


def test_performance_network_keeps_the_subnet_chosen_for_the_reservation_zone():
    ec2 = _ec2(subnet_zones=ACCOUNT_SUBNETS)

    launch_args = _launch(ec2, networks=NETWORKS, performance_network=True)

    assert launch_args['NetworkInterfaces'][0]['SubnetId'] == SUBNET_IN_ZONE


def test_performance_network_picks_a_random_subnet_only_from_the_reservation_zone():
    ec2 = _ec2(subnet_zones=ACCOUNT_SUBNETS)

    launch_args = _launch(ec2, performance_network=True)

    assert launch_args['NetworkInterfaces'][0]['SubnetId'] == SUBNET_IN_ZONE


def test_performance_network_refuses_when_the_reservation_zone_has_no_subnet():
    ec2 = _ec2(subnet_zones={SUBNET_ELSEWHERE: OTHER_ZONE})

    with pytest.raises(RuntimeError, match=ZONE):
        _launch(ec2, performance_network=True)

    ec2.run_instances.assert_not_called()


def test_uses_a_network_interface_in_the_reservation_zone_without_pinning_a_placement():
    ec2 = _ec2(enis={'eni-1': {'AvailabilityZone': ZONE, 'SubnetId': SUBNET_IN_ZONE, 'Status': 'available'}})

    launch_args = _launch(ec2, network_interface='eni-1')

    assert launch_args['NetworkInterfaces'][0]['NetworkInterfaceId'] == 'eni-1'
    # The interface fixes the zone by itself.
    assert 'Placement' not in launch_args


def test_refuses_a_network_interface_in_another_zone():
    ec2 = _ec2(enis={'eni-1': {'AvailabilityZone': OTHER_ZONE, 'SubnetId': SUBNET_ELSEWHERE,
                               'Status': 'available'}})

    with pytest.raises(RuntimeError, match='eni-1'):
        _launch(ec2, network_interface='eni-1')

    ec2.run_instances.assert_not_called()


def test_refuses_a_reservation_without_a_zone():
    with pytest.raises(RuntimeError, match='availability zone'):
        _launch(_ec2(), networks=NETWORKS, availability_zone=None)


def test_leaves_a_launch_without_a_reservation_as_it_was():
    launch_args = _launch(_ec2(subnet_zones={SUBNET_ELSEWHERE: OTHER_ZONE}), networks=NETWORKS,
                          capacity_reservation_id=None, availability_zone=ZONE, subnet=SUBNET_ELSEWHERE)

    # An explicit subnet still wins over the desired zone, exactly as before.
    assert launch_args['SubnetId'] == SUBNET_ELSEWHERE
    assert 'CapacityReservationSpecification' not in launch_args
    assert 'Placement' not in launch_args
