# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

import functools

from azure.common.client_factory import get_client_from_auth_file
from azure.mgmt.network import NetworkManagementClient
from azure.mgmt.resource import ResourceManagementClient

from autoscaling.utils.cloud_client import CloudClient


class AzureClient(CloudClient):

    def __init__(self):
        self.res_client = get_client_from_auth_file(ResourceManagementClient)
        self.network_client = get_client_from_auth_file(NetworkManagementClient)

    name = "AZ"

    def describe_instance(self, run_id):
        for resource in self.res_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + run_id + "'"):
            if str(resource.type).split('/')[-1] == "virtualMachines":
                return resource

        return None

    def get_private_ip(self, instance):
        resource_group_name = instance.id.split("/")[4]
        instance_name = instance.name

        return self.network_client.network_interfaces.get(
            resource_group_name, instance_name + '-nic').ip_configurations[0].private_ip_address

    def terminate_instance(self, run_id):
        resources = []
        for resource in self.res_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + run_id + "'"):
            resources.append(resource)
            # we need to sort resources to be sure that vm and nic will be deleted first,
            # because it has attached resorces(disks and ip)
            resources.sort(key=functools.cmp_to_key(self.azure_resource_type_cmp))
        for resource in resources:
            self.res_client.resources.delete(
                resource_group_name=resource.id.split('/')[4],
                resource_provider_namespace=resource.id.split('/')[6],
                parent_resource_path='',
                resource_type=str(resource.type).split('/')[-1],
                resource_name=resource.name,
                api_version=self.resolve_azure_api(resource),
                parameters=resource
            ).wait()

    def node_price_type_should_be(self, run_id, spot):
        raise RuntimeError("Spot instances (low priority virtual machines) is not supported for AZURE provider")

    @staticmethod
    def azure_resource_type_cmp(r1, r2):
        if str(r1.type).split('/')[-1] == "virtualMachines":
            return -1
        elif str(r1.type).split('/')[-1] == "networkInterfaces" and str(r2.type).split('/')[-1] != "virtualMachines":
            return -1
        return 0

    def resolve_azure_api(self, resource):
        """ This method retrieves the latest non-preview api version for
        the given resource (unless the preview version is the only available
        api version) """
        provider = self.res_client.providers.get(resource.id.split('/')[6])
        rt = next((t for t in provider.resource_types
                   if t.resource_type == '/'.join(resource.type.split('/')[1:])), None)
        if rt and 'api_versions' in rt.__dict__:
            api_version = [v for v in rt.__dict__['api_versions'] if 'preview' not in v.lower()]
            return api_version[0] if api_version else rt.__dict__['api_versions'][0]
