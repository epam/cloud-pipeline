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
import os
import re

import pykube
import argparse
from azure.common.client_factory import get_client_from_auth_file
from azure.mgmt.resource import ResourceManagementClient
from azure.mgmt.compute import ComputeManagementClient

RUN_ID_LABEL = 'runid'
CLOUD_REGION_LABEL = 'cloud_region'
KUBE_CONFIG_PATH = '~/.kube/config'
LOW_PRIORITY_INSTANCE_ID_TEMPLATE = '(az-[a-z0-9]{16})[0-9A-Z]{6}'

res_client = get_client_from_auth_file(ResourceManagementClient)
compute_client = get_client_from_auth_file(ComputeManagementClient)
resource_group_name = os.environ["AZURE_RESOURCE_GROUP"]


def resolve_azure_api(resource):
    """ This method retrieves the latest non-preview api version for
    the given resource (unless the preview version is the only available
    api version) """
    provider = res_client.providers.get(resource.id.split('/')[6])
    rt = next((t for t in provider.resource_types
               if t.resource_type == '/'.join(resource.type.split('/')[1:])), None)
    if rt and 'api_versions' in rt.__dict__:
        api_version = [v for v in rt.__dict__['api_versions'] if 'preview' not in v.lower()]
        return api_version[0] if api_version else rt.__dict__['api_versions'][0]


def azure_resource_type_cmp(r1, r2):
    if str(r1.type).split('/')[-1].startswith("virtualMachine"):
        return -1
    elif str(r1.type).split('/')[-1] == "networkInterfaces" and not str(r2.type).split('/')[-1].startswith("virtualMachine"):
        return -1
    return 0


def delete_cloud_node(node_name):
    low_priority_search = re.search(LOW_PRIORITY_INSTANCE_ID_TEMPLATE, node_name)
    if low_priority_search:
        # just because we set computer_name_prefix in nodeup script,
        # we know that it is the same with scale set name, so let's extract it
        scale_set_name = low_priority_search.group(1)
        info = compute_client.virtual_machine_scale_sets.get(resource_group_name, scale_set_name)
    else:
        info = compute_client.virtual_machines.get(resource_group_name, node_name)
    if info is not None and "Name" in info.tags:
        resources = []
        for resource in res_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + info.tags["Name"] + "'"):
            resources.append(resource)
            # we need to sort resources to be sure that vm and nic will be deleted first, because it has attached resorces(disks and ip)
            resources.sort(key=functools.cmp_to_key(azure_resource_type_cmp))
        for resource in resources:
            res_client.resources.delete(
                resource_group_name=resource.id.split('/')[4],
                resource_provider_namespace=resource.id.split('/')[6],
                parent_resource_path='',
                resource_type=str(resource.type).split('/')[-1],
                resource_name=resource.name,
                api_version=resolve_azure_api(resource),
                parameters=resource
            ).wait()


def delete_kubernetes_node(kube_api, node_name):
    if node_name is not None and get_node(kube_api, node_name) is not None:
        obj = {
            "apiVersion": "v1",
            "kind": "Node",
            "metadata": {
                "name": node_name
            }
        }
        pykube.Node(kube_api, obj).delete()


def get_node(kube_api, nodename):
    nodes = pykube.Node.objects(kube_api).filter(field_selector={'metadata.name': nodename})
    if len(nodes.response['items']) == 0:
        return None
    return nodes.response['items'][0]


def get_kube_api():
    try:
        api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
    except Exception as e:
        api = pykube.HTTPClient(pykube.KubeConfig.from_file(KUBE_CONFIG_PATH))
    api.session.verify = False
    return api


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--internal_ip", "-ip", type=str, required=True)
    parser.add_argument("--node_name", "-n", type=str, required=True)
    args, unknown = parser.parse_known_args()
    kube_api = get_kube_api()
    delete_kubernetes_node(kube_api, args.node_name)

    delete_cloud_node(args.node_name)


if __name__ == '__main__':
    main()
