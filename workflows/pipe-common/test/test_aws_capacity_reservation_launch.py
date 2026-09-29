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
How a launch targets a capacity reservation.

Both halves matter and neither fails loudly when missing: a reservation created with TARGETED match criteria is
consumed only by an instance that names it, and it exists in exactly one availability zone. Get either wrong and
the instance still launches - it just quietly runs as on-demand while the reserved capacity sits idle and paid
for. That silence is why these are worth pinning.
"""

from mock import MagicMock, patch

from pipeline.autoscaling import awsprovider
from pipeline.autoscaling.awsprovider import AWSInstanceProvider, RUNNING

RESERVATION_ID = 'cr-0123456789abcdef0'
ZONE = 'us-east-1c'
OTHER_ZONE = 'us-east-1a'
SUBNET_IN_ZONE = 'subnet-in-zone'
SUBNET_ELSEWHERE = 'subnet-elsewhere'


def _apply(capacity_reservation_id=RESERVATION_ID, availability_zone=ZONE,
           allowed_networks=None, additional_args=None):
    args = {} if additional_args is None else additional_args
    # The method is private; name mangling is the price of testing it without widening the provider's surface.
    AWSInstanceProvider._AWSInstanceProvider__apply_capacity_reservation(
        AWSInstanceProvider.__new__(AWSInstanceProvider),
        args, capacity_reservation_id, availability_zone, allowed_networks)
    return args


def _launch_on_demand(capacity_reservation_id=RESERVATION_ID, availability_zone=ZONE, allowed_networks=None):
    """
    Drives the public launch path down to the EC2 call, which the helper-level tests above cannot reach: a reservation
    that never makes it from run_instance into the launch arguments is exactly as silent as a wrong zone.
    """
    provider = AWSInstanceProvider.__new__(AWSInstanceProvider)
    provider.cloud_region = 'region'
    provider.ec2 = MagicMock()
    provider.ec2.run_instances.return_value = {'Instances': [{'InstanceId': 'i-1', 'PrivateIpAddress': '10.0.0.1'}]}
    provider.ec2.describe_instance_status.return_value = {
        'InstanceStatuses': [{'InstanceState': {'Code': RUNNING}}]
    }
    provider._AWSInstanceProvider__check_spot_request_exists = MagicMock(return_value=(None, None))
    provider._AWSInstanceProvider__get_block_devices = MagicMock(return_value=[])
    with patch.object(awsprovider, 'utils') as utils:
        utils.get_networks_config.return_value = allowed_networks or {}
        utils.load_cloud_config.return_value = (None, None)
        provider.run_instance(is_spot=False, bid_price=None, ins_type='m5.large', ins_hdd=50, ins_img='ami-1',
                              ins_platform='linux', ins_key='key', run_id=1, pool_id=2, kms_encyr_key_id=None,
                              num_rep=1, time_rep=0, kube_ip=None, kubeadm_token=None, kubeadm_cert_hash=None,
                              kube_node_token=None, global_distribution_url=None,
                              capacity_reservation_id=capacity_reservation_id, availability_zone=availability_zone)
    return provider.ec2.run_instances.call_args[1]


def test_on_demand_launch_targets_the_reservation():
    launch_args = _launch_on_demand()

    assert launch_args['CapacityReservationSpecification'] == {
        'CapacityReservationTarget': {'CapacityReservationId': RESERVATION_ID}
    }
    assert launch_args['Placement']['AvailabilityZone'] == ZONE


def test_on_demand_launch_without_a_reservation_is_untouched():
    launch_args = _launch_on_demand(capacity_reservation_id=None, availability_zone=None)

    assert 'CapacityReservationSpecification' not in launch_args
    assert 'Placement' not in launch_args


def test_on_demand_launch_uses_the_subnet_of_the_reservation_zone():
    """
    With networks configured the launch used to pick a random zone's subnet before the reservation was looked at,
    so a reservation-backed node landed in the reservation's zone only by chance.
    """
    networks = {OTHER_ZONE: SUBNET_ELSEWHERE, ZONE: SUBNET_IN_ZONE}
    with patch.object(awsprovider, 'randint', return_value=list(networks.keys()).index(OTHER_ZONE)):
        launch_args = _launch_on_demand(allowed_networks=networks)

    assert launch_args['SubnetId'] == SUBNET_IN_ZONE
    assert 'Placement' not in launch_args


def test_on_demand_launch_refuses_when_the_reservation_zone_has_no_subnet():
    try:
        _launch_on_demand(allowed_networks={OTHER_ZONE: SUBNET_ELSEWHERE})
        raise AssertionError('expected a refusal when no subnet is configured for the reservation zone')
    except RuntimeError as e:
        assert ZONE in str(e)


def test_on_demand_launch_without_a_reservation_still_uses_a_configured_subnet():
    launch_args = _launch_on_demand(capacity_reservation_id=None, availability_zone=None,
                                    allowed_networks={OTHER_ZONE: SUBNET_ELSEWHERE})

    assert launch_args['SubnetId'] == SUBNET_ELSEWHERE


def test_targets_the_reservation():
    args = _apply()

    assert args['CapacityReservationSpecification'] == {
        'CapacityReservationTarget': {'CapacityReservationId': RESERVATION_ID}
    }


def test_pins_the_zone_when_nothing_else_constrains_it():
    args = _apply()

    assert args['Placement']['AvailabilityZone'] == ZONE


def test_prefers_a_subnet_in_the_reservation_zone_over_a_bare_placement():
    args = _apply(allowed_networks={ZONE: SUBNET_IN_ZONE, OTHER_ZONE: SUBNET_ELSEWHERE})

    assert args['SubnetId'] == SUBNET_IN_ZONE
    # Naming a subnet already fixes the zone; also setting Placement would be redundant and could conflict.
    assert 'Placement' not in args


def test_refuses_when_no_configured_subnet_is_in_the_reservation_zone():
    try:
        _apply(allowed_networks={OTHER_ZONE: SUBNET_ELSEWHERE})
        raise AssertionError('expected a refusal when no subnet is configured for the reservation zone')
    except RuntimeError as e:
        assert ZONE in str(e)


def test_keeps_dedicated_tenancy_while_adding_the_zone():
    args = _apply(additional_args={'Placement': {'Tenancy': 'dedicated'}})

    assert args['Placement']['Tenancy'] == 'dedicated'
    assert args['Placement']['AvailabilityZone'] == ZONE


def test_refuses_to_launch_without_a_zone():
    """
    The failure mode this prevents is silent: an instance in the wrong zone launches perfectly well, runs as
    ordinary on-demand, and leaves the reserved capacity idle and paid for. A refused launch is visible and
    retryable, so it is the better outcome.
    """
    try:
        _apply(availability_zone=None)
        raise AssertionError('expected a refusal when no availability zone is given')
    except RuntimeError as e:
        assert 'availability zone' in str(e)


def test_does_nothing_without_a_reservation():
    args = _apply(capacity_reservation_id=None)

    assert args == {}
