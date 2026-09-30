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
Choosing a configured network on GCP and Azure.

pipe-common runs in every job container, Python 3 ones included, and both providers used to index dict.items() -
which works on Python 2 only, so any launch with a networks config failed on Python 3 before it reached the cloud.
"""

try:
    from unittest.mock import MagicMock, patch
except ImportError:
    from mock import MagicMock, patch

from pipeline.autoscaling import azureprovider, gcpprovider
from pipeline.autoscaling.azureprovider import AzureInstanceProvider
from pipeline.autoscaling.gcpprovider import GCPInstanceProvider


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
