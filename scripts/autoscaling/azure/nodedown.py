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


import argparse
import functools
import os

import pykube
from azure.common.client_factory import get_client_from_auth_file
from azure.mgmt.resource import ResourceManagementClient
from azure.mgmt.network import NetworkManagementClient


RUN_ID_LABEL = 'runid'
CLOUD_REGION_LABEL = 'cloud_region'

res_client = get_client_from_auth_file(ResourceManagementClient)
network_client = get_client_from_auth_file(NetworkManagementClient)


def find_instance(run_id):
    for resource in res_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + run_id + "'"):
        if str(resource.type).split('/')[-1] == "virtualMachines":
            return resource.name

    return None


def run_id_filter(run_id):
    return {
                'Name': 'tag:Name',
                'Values': [run_id]
           }


def verify_regnode(resource_group_name, ins_id, api):
    public_ip = network_client.public_ip_addresses.get(
        resource_group_name,
        ins_id + '-ip'
    )
    nodename_full = public_ip.dns_settings.fqdn
    nodename = nodename_full.split('.', 1)[0]

    if find_node(api, nodename):
        return nodename

    if find_node(api, nodename_full):
        return nodename_full

    raise RuntimeError("Failed to find Node {}".format(ins_id))


def find_node(api, node_name):
    node = pykube.Node.objects(api).filter(field_selector={'metadata.name': node_name})
    if len(node.response['items']) > 0:
        return node_name
    else:
        return ''


def delete_kube_node(nodename, run_id, api):
    if nodename is None:
        nodes = pykube.Node.objects(api).filter(selector={RUN_ID_LABEL: run_id})
        if len(nodes.response['items']) > 0:
            node = nodes.response['items'][0]
            nodename = node['metadata']['name']
    if nodename is not None:
        obj = {
            "apiVersion": "v1",
            "kind": "Node",
            "metadata": {
                "name": nodename,
                "labels": {
                    "runid": run_id
                }
            }
        }
        pykube.Node(api, obj).delete()


def get_cloud_region(api, run_id):
    nodes = pykube.Node.objects(api).filter(selector={RUN_ID_LABEL: run_id})
    if len(nodes.response['items']) == 0:
        raise RuntimeError('Cannot find node matching RUN ID %s' % run_id)
    node = nodes.response['items'][0]
    labels = node['metadata']['labels']
    if CLOUD_REGION_LABEL not in labels:
        raise RuntimeError('Node %s is not labeled with Azure Region' % node['metadata']['name'])
    return labels[CLOUD_REGION_LABEL]


def get_kube_api():
    try:
        api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
    except Exception as e:
        api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
    api.session.verify = False
    return api


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
    if str(r1.type).split('/')[-1] == "virtualMachines":
        return -1
    elif str(r1.type).split('/')[-1] == "networkInterfaces" and str(r2.type).split('/')[-1] != "virtualMachines":
        return -1
    return 0


def delete_resources_by_tag(run_id):
    resources = []
    for resource in res_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + run_id + "'"):
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


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run_id", "-kid", type=str, required=True)
    parser.add_argument("--ins_id", "-id", type=str, required=False)  # do we need?
    args = parser.parse_args()
    run_id = args.run_id
    api = get_kube_api()

    resource_group_name = os.environ["AZURE_RESOURCE_GROUP"]

    try:
        ins_id = find_instance(run_id)
    except Exception:
        ins_id = None
    if ins_id is None:
        delete_kube_node(None, run_id, api)
        delete_resources_by_tag(run_id)
    else:
        try:
            nodename = verify_regnode(resource_group_name, ins_id, api)
        except Exception:
            nodename = None

        delete_kube_node(nodename, run_id, api)
        delete_resources_by_tag(run_id)


if __name__ == '__main__':
    main()
