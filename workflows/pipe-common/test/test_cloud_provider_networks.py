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

try:
    from unittest.mock import MagicMock, patch
except ImportError:
    from mock import MagicMock, patch

from pipeline.autoscaling import awsprovider, azureprovider, gcpprovider
from pipeline.autoscaling.awsprovider import AWSInstanceProvider, RUNNING
from pipeline.autoscaling.azureprovider import AzureInstanceProvider
from pipeline.autoscaling.gcpprovider import GCPInstanceProvider


def test_aws_uses_the_configured_subnet():
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
        utils.get_networks_config.return_value = {'us-east-1a': 'subnet-a'}
        utils.load_cloud_config.return_value = (None, None)
        provider.run_instance(is_spot=False, bid_price=None, ins_type='m5.large', ins_hdd=50, ins_img='ami-1',
                              ins_platform='linux', ins_key='key', run_id=1, pool_id=2, kms_encyr_key_id=None,
                              num_rep=1, time_rep=0, kube_ip=None, kubeadm_token=None, kubeadm_cert_hash=None,
                              kube_node_token=None, global_distribution_url=None)

    assert provider.ec2.run_instances.call_args[1]['SubnetId'] == 'subnet-a'


def test_gcp_uses_the_configured_network():
    provider = GCPInstanceProvider.__new__(GCPInstanceProvider)
    provider.cloud_region = 'us-central1-a'
    provider.project_id = 'project'
    with patch.object(gcpprovider, 'utils') as utils:
        utils.get_networks_config.return_value = {'network-a': 'subnet-a'}
        utils.get_access_config.return_value = None
        networks = provider._GCPInstanceProvider__build_networks('n2-standard-2')

    assert networks[0]['network'] == 'projects/project/global/networks/network-a'
    assert networks[0]['subnetwork'] == 'projects/project/regions/us-central1/subnetworks/subnet-a'


def test_azure_uses_the_configured_subnet():
    provider = AzureInstanceProvider.__new__(AzureInstanceProvider)
    provider.zone = 'eastus'
    provider.network_client = MagicMock()
    with patch.object(azureprovider, 'utils') as utils:
        utils.get_networks_config.return_value = {'resource-group/vnet': 'subnet-1'}
        provider._AzureInstanceProvider__get_subnet_info()

    provider.network_client.subnets.get.assert_called_once_with('resource-group', 'vnet', 'subnet-1')
