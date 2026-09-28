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

from pipeline.autoscaling.awsprovider import AWSInstanceProvider

RESERVATION_ID = 'cr-0123456789abcdef0'
ZONE = 'us-east-1c'
OTHER_ZONE = 'us-east-1a'
SUBNET_IN_ZONE = 'subnet-in-zone'
SUBNET_ELSEWHERE = 'subnet-elsewhere'


def _apply(capacity_reservation_id=RESERVATION_ID, availability_zone=ZONE,
           subnet_id=None, allowed_networks=None, additional_args=None):
    args = {} if additional_args is None else additional_args
    # The method is private; name mangling is the price of testing it without widening the provider's surface.
    AWSInstanceProvider._AWSInstanceProvider__apply_capacity_reservation(
        AWSInstanceProvider.__new__(AWSInstanceProvider),
        args, capacity_reservation_id, availability_zone, subnet_id, allowed_networks)
    return args


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


def test_leaves_an_explicit_subnet_alone():
    args = _apply(subnet_id=SUBNET_ELSEWHERE, additional_args={'SubnetId': SUBNET_ELSEWHERE})

    assert args['SubnetId'] == SUBNET_ELSEWHERE
    assert 'Placement' not in args


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
