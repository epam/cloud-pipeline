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
A pool's own launch configuration - what was set for the pool on purpose - laid over the region's amis rule its
nodes match.

A capacity reservation reaches a node only this way - its target in additional_spec, its zone and subnet - so each
part has to arrive in the launch request intact: a missing target or a wrong zone still launches an instance, which
just runs as ordinary on-demand while the reserved capacity sits idle and paid for.
"""

try:
    from unittest.mock import MagicMock, patch
except ImportError:
    from mock import MagicMock, patch

import aws_nodeup

ZONE = 'us-east-1c'
SUBNET = 'subnet-in-zone'
POOL_ID = '42'
RESERVATION_TARGET = {'CapacityReservationTarget': {'CapacityReservationId': 'cr-0123456789abcdef0'}}
INSTANCE_PROFILE = {'Arn': 'arn:aws:iam::123456789012:instance-profile/node'}
RULE = {'ami': 'ami-rule', 'instance_mask': '*', 'init_script': '/opt/api/scripts/init.sh',
        'embedded_scripts': None, 'fs_type': 'btrfs', 'additional_spec': {'IamInstanceProfile': INSTANCE_PROFILE},
        'availability_zone': None, 'subnet': None}


def _resolve(pool_configuration=None, ins_img='null', availability_zone=None, subnet=None, rule=RULE):
    """
    resolve_launch_configuration for a pool node - or a node outside any pool, when there is no pool configuration -
    with the region's matching rule and the pool's configuration stubbed.
    """
    with patch.object(aws_nodeup, 'PipelineAPI') as api, patch.object(aws_nodeup, 'pipe_log'), \
            patch.object(aws_nodeup, 'get_matching_instance_image', return_value=dict(rule)):
        api.return_value.load_node_pool.return_value = {'amiConfiguration': pool_configuration}
        configuration = aws_nodeup.resolve_launch_configuration(
            'region', 'm5.large', 'linux', 'token', 'p-1', POOL_ID if pool_configuration is not None else None,
            ins_img, availability_zone, subnet)
    aws_nodeup.load_pool_launch_configuration(None)
    return configuration


def test_overlays_every_field_the_pool_sets_and_keeps_the_rest_of_the_rule():
    overlaid = aws_nodeup.overlay_dict(RULE, {
        'ami': 'ami-pool', 'init_script': '/opt/api/scripts/init_pool.sh', 'availability_zone': ZONE, 'subnet': SUBNET})

    assert overlaid['ami'] == 'ami-pool'
    assert overlaid['init_script'] == '/opt/api/scripts/init_pool.sh'
    assert overlaid['availability_zone'] == ZONE
    assert overlaid['subnet'] == SUBNET
    assert overlaid['fs_type'] == 'btrfs'
    assert overlaid['instance_mask'] == '*'


def test_merges_additional_spec_so_a_reservation_target_keeps_the_rules_instance_profile():
    overlaid = aws_nodeup.overlay_dict(RULE, {
        'additional_spec': {'CapacityReservationSpecification': RESERVATION_TARGET}})

    assert overlaid['additional_spec'] == {'IamInstanceProfile': INSTANCE_PROFILE,
                                           'CapacityReservationSpecification': RESERVATION_TARGET}


def test_replaces_a_spec_key_the_rule_and_the_pool_both_set_rather_than_mixing_them():
    """
    A rule may set its own CapacityReservationSpecification - a preference - and a reservation pool sets a target in
    the same key. EC2 takes one or the other, so the pool's replaces the rule's whole.
    """
    rule = dict(RULE, additional_spec={'IamInstanceProfile': INSTANCE_PROFILE,
                                       'CapacityReservationSpecification': {'CapacityReservationPreference': 'none'}})

    overlaid = aws_nodeup.overlay_dict(rule, {'additional_spec': {'CapacityReservationSpecification': RESERVATION_TARGET}})

    assert overlaid['additional_spec'] == {'IamInstanceProfile': INSTANCE_PROFILE,
                                           'CapacityReservationSpecification': RESERVATION_TARGET}


def test_does_not_share_the_pools_spec_with_the_result():
    """
    The launch adds to the spec it is given; the pool's own configuration must not change with it.
    """
    pool_spec = {'CapacityReservationSpecification': RESERVATION_TARGET}

    overlaid = aws_nodeup.overlay_dict(dict(RULE, additional_spec=None), {'additional_spec': pool_spec})
    overlaid['additional_spec']['SubnetId'] = SUBNET

    assert pool_spec == {'CapacityReservationSpecification': RESERVATION_TARGET}


def test_leaves_the_rule_as_it_is_without_a_pool_configuration():
    assert aws_nodeup.overlay_dict(RULE, {}) == RULE


def test_loads_the_pools_configuration_by_its_id():
    with patch.object(aws_nodeup, 'PipelineAPI') as api, patch.object(aws_nodeup, 'pipe_log'):
        api.return_value.load_node_pool.return_value = {'id': 42, 'amiConfiguration': {'subnet': SUBNET}}
        configuration = aws_nodeup.load_pool_launch_configuration(POOL_ID)

    api.return_value.load_node_pool.assert_called_once_with(POOL_ID)
    assert configuration == {'subnet': SUBNET}


def test_asks_nothing_for_a_node_outside_any_pool():
    with patch.object(aws_nodeup, 'PipelineAPI') as api:
        assert aws_nodeup.load_pool_launch_configuration(None) == {}

    api.assert_not_called()


def test_lays_a_pool_nodes_configuration_over_the_rule_it_matches():
    """
    The pool carries only what was set for it; everything else - here the init script, filesystem and instance
    profile - is the matched rule's.
    """
    configuration = _resolve({'ami': 'ami-pool', 'availability_zone': ZONE,
                              'additional_spec': {'CapacityReservationSpecification': RESERVATION_TARGET}})

    assert configuration['ami'] == 'ami-pool'
    assert configuration['availability_zone'] == ZONE
    assert configuration['additional_spec'] == {'IamInstanceProfile': INSTANCE_PROFILE,
                                                'CapacityReservationSpecification': RESERVATION_TARGET}
    assert configuration['init_script'] == RULE['init_script']
    assert configuration['fs_type'] == RULE['fs_type']


def test_keeps_the_rules_image_for_a_reservation_pool_that_sets_none():
    """
    A reservation writes only its target, zone and subnet. The node's image is the rule's, as it is for any run that
    asks for no image - which is what lets such runs reuse the pool's nodes.
    """
    configuration = _resolve({'availability_zone': ZONE, 'subnet': SUBNET,
                              'additional_spec': {'CapacityReservationSpecification': RESERVATION_TARGET}})

    assert configuration['ami'] == RULE['ami']
    assert configuration['init_script'] == RULE['init_script']
    assert configuration['subnet'] == SUBNET


def test_keeps_the_pools_zone_for_placement_once_resolved():
    """
    Whether a zone pins the placement depends on its being the pool's - which the launch asks after resolving.
    """
    with patch.object(aws_nodeup, 'PipelineAPI') as api, patch.object(aws_nodeup, 'pipe_log'), \
            patch.object(aws_nodeup, 'get_matching_instance_image', return_value=dict(RULE)):
        api.return_value.load_node_pool.return_value = {'amiConfiguration': {'availability_zone': ZONE}}
        aws_nodeup.resolve_launch_configuration('region', 'm5.large', 'linux', 'token', 'p-1', POOL_ID,
                                                'null', None, None)
        pinned = aws_nodeup.zone_pinned_by_pool(ZONE)
    aws_nodeup.load_pool_launch_configuration(None)

    assert pinned


def test_launches_a_node_outside_any_pool_as_the_rule_says():
    assert _resolve() == RULE


def test_lets_explicit_arguments_win_over_the_pool_and_the_rule():
    configuration = _resolve({'ami': 'ami-pool', 'availability_zone': ZONE, 'subnet': SUBNET},
                             ins_img='ami-explicit', availability_zone='us-east-1a', subnet='subnet-explicit')

    assert configuration['ami'] == 'ami-explicit'
    assert configuration['availability_zone'] == 'us-east-1a'
    assert configuration['subnet'] == 'subnet-explicit'


def test_treats_a_null_image_argument_as_none_given():
    """
    The API passes "null" for a node no image was asked for - which must not hide the pool's or the rule's image.
    """
    assert _resolve({'ami': 'ami-pool'}, ins_img='null')['ami'] == 'ami-pool'
    assert _resolve(ins_img='null')['ami'] == RULE['ami']


def test_takes_the_pools_zone_and_subnet_when_no_argument_names_them():
    configuration = _resolve({'availability_zone': ZONE, 'subnet': SUBNET})

    assert (configuration['availability_zone'], configuration['subnet']) == (ZONE, SUBNET)


def test_takes_the_pools_additional_spec_even_when_no_rule_matched():
    """
    A pool's reservation target lives in its additional spec, and must reach the launch whether or not a rule matched.
    """
    unmatched = dict(RULE, instance_mask=None, additional_spec=None)

    configuration = _resolve({'additional_spec': {'CapacityReservationSpecification': RESERVATION_TARGET}},
                             rule=unmatched)

    assert configuration['additional_spec'] == {'CapacityReservationSpecification': RESERVATION_TARGET}


def _ec2(subnet_zones=None):
    ec2 = MagicMock()
    ec2.run_instances.return_value = {'Instances': [{'InstanceId': 'i-1', 'PrivateIpAddress': '10.0.0.1'}]}

    def describe_subnets(SubnetIds=None, Filters=None):
        zones = subnet_zones or {}
        wanted = Filters[0]['Values'] if Filters else None
        return {'Subnets': [{'SubnetId': s, 'AvailabilityZone': z} for s, z in sorted(zones.items())
                            if wanted is None or z in wanted]}

    ec2.describe_subnets.side_effect = describe_subnets
    return ec2


def _launch(ec2, additional_spec=None, availability_zone=None, subnet=None, is_dedicated=False,
            performance_network=False, networks=None, pool_zone=None):
    pool_configuration = {'availability_zone': pool_zone} if pool_zone else {}
    with patch.object(aws_nodeup, '__POOL_LAUNCH_CONFIGURATION__', pool_configuration), \
            patch.object(aws_nodeup, 'get_networks_config', return_value=networks or {}), \
            patch.object(aws_nodeup, 'get_security_groups', return_value=['sg-1']), \
            patch.object(aws_nodeup, 'get_block_devices', return_value=[]), \
            patch.object(aws_nodeup, 'get_tags', return_value=[]), \
            patch.object(aws_nodeup, 'resource_tags', return_value=[]), \
            patch.object(aws_nodeup, 'get_current_status', return_value=aws_nodeup.RUNNING), \
            patch.object(aws_nodeup.random, 'choice', side_effect=lambda seq: seq[0]), \
            patch.object(aws_nodeup, 'pipe_log'):
        aws_nodeup.run_on_demand_instance(ec2, 'region', 'ami-1', 'key', 'm5.large', 50, None, 1, POOL_ID, 'script',
                                          1, 0, None, None, additional_spec, availability_zone, None, subnet, None,
                                          is_dedicated, performance_network, None)
    return ec2.run_instances.call_args[1]


def test_sends_the_reservation_target_in_the_launch_request():
    launch_args = _launch(_ec2(), additional_spec={'CapacityReservationSpecification': RESERVATION_TARGET},
                          availability_zone=ZONE, subnet=SUBNET)

    assert launch_args['CapacityReservationSpecification'] == RESERVATION_TARGET
    assert launch_args['SubnetId'] == SUBNET


def test_pins_the_zone_the_pool_pins_when_no_subnet_fixes_it():
    launch_args = _launch(_ec2(), availability_zone=ZONE, pool_zone=ZONE)

    assert launch_args['Placement']['AvailabilityZone'] == ZONE
    assert 'SubnetId' not in launch_args


def test_leaves_a_zone_an_ordinary_run_asks_for_as_it_was():
    """
    Only a pool's own zone is enforced by placement. A zone an ordinary run asks for keeps its old meaning, so launches
    that worked before - into whichever zone has a default subnet - still do.
    """
    launch_args = _launch(_ec2(), availability_zone=ZONE)

    assert 'Placement' not in launch_args


def test_keeps_dedicated_tenancy_while_pinning_the_zone():
    launch_args = _launch(_ec2(), availability_zone=ZONE, is_dedicated=True, pool_zone=ZONE)

    assert launch_args['Placement'] == {'Tenancy': 'dedicated', 'AvailabilityZone': ZONE}


def test_leaves_the_placement_alone_when_a_subnet_fixes_the_zone():
    launch_args = _launch(_ec2(), availability_zone=ZONE, subnet=SUBNET, pool_zone=ZONE)

    assert 'Placement' not in launch_args


def test_keeps_a_performance_networks_random_subnet_in_the_zone_asked_for():
    ec2 = _ec2(subnet_zones={'subnet-a-elsewhere': 'us-east-1a', SUBNET: ZONE})

    launch_args = _launch(ec2, availability_zone=ZONE, performance_network=True, pool_zone=ZONE)

    assert launch_args['NetworkInterfaces'][0]['SubnetId'] == SUBNET


def test_leaves_a_launch_without_any_configuration_as_it_was():
    launch_args = _launch(_ec2())

    assert 'Placement' not in launch_args
    assert 'CapacityReservationSpecification' not in launch_args


def test_drops_a_reservation_target_from_a_spot_request_with_a_warning():
    """
    A spot request cannot consume reserved capacity and one that targets a reservation is refused, so the node runs
    as spot without it - but not silently, or the reserved capacity idles with nothing saying why.
    """
    spec = {'CapacityReservationSpecification': RESERVATION_TARGET, 'IamInstanceProfile': INSTANCE_PROFILE}
    with patch.object(aws_nodeup, 'pipe_log_warn') as warn:
        remaining = aws_nodeup.without_capacity_reservation_target(spec)

    assert remaining == {'IamInstanceProfile': INSTANCE_PROFILE}
    warn.assert_called_once()
    assert 'CapacityReservationSpecification' in spec


def test_leaves_a_spot_request_without_a_reservation_target_as_it_is():
    spec = {'IamInstanceProfile': INSTANCE_PROFILE}
    with patch.object(aws_nodeup, 'pipe_log_warn') as warn:
        assert aws_nodeup.without_capacity_reservation_target(spec) is spec

    warn.assert_not_called()
