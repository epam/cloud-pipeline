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
import json
import sys
import re

import pykube
import logging
import fnmatch
import uuid
import base64
from time import sleep
from random import randint
from azure.common.client_factory import get_client_from_auth_file
from azure.mgmt.resource import ResourceManagementClient
from azure.mgmt.compute import ComputeManagementClient
from azure.mgmt.network import NetworkManagementClient
from msrestazure.azure_exceptions import CloudError
from pipeline import Logger, TaskStatus, PipelineAPI

VM_NAME_PREFIX = "az-"
UUID_LENGHT = 16

NETWORKS_PARAM = "cluster.networks.config"
NODEUP_TASK = "InitializeNode"
LIMIT_EXCEEDED_EXIT_CODE = 6
LIMIT_EXCEEDED_ERROR_MASSAGE = 'Instance limit exceeded. A new one will be launched as soon as free space will be available.'
LOW_PRIORITY_INSTANCE_ID_TEMPLATE = '(az-[a-z0-9]{16})[0-9A-Z]{6}'

current_run_id = 0
api_url = None
api_token = None
script_path = None


def pipe_log_init(run_id):
    global api_token
    global api_url
    global current_run_id
    current_run_id = run_id

    api_url = os.environ["API"]
    api_token = os.environ["API_TOKEN"]

    if not api_url or not api_token:
        logging.basicConfig(filename='nodeup.log', level=logging.INFO, format='%(asctime)s %(message)s')


def pipe_log_warn(message):
    global api_token
    global api_url
    global script_path
    global current_run_id

    if api_url and api_token:
        Logger.warn('[{}] {}'.format(current_run_id, message),
                    task_name=NODEUP_TASK,
                    run_id=current_run_id,
                    api_url=api_url,
                    log_dir=script_path,
                    omit_console=True)
    else:
        logging.warn(message)


def pipe_log(message, status=TaskStatus.RUNNING):
    global api_token
    global api_url
    global script_path
    global current_run_id

    if api_url and api_token:
        Logger.log_task_event(NODEUP_TASK,
                              '[{}] {}'.format(current_run_id, message),
                              run_id=current_run_id,
                              instance=str(current_run_id),
                              log_dir=script_path,
                              api_url=api_url,
                              status=status,
                              omit_console=True)
    else:
        # Log as always
        logging.info(message)

#############################

#############################


__CLOUD_METADATA__ = None
__CLOUD_TAGS__ = None


def load_cloud_config():
    global __CLOUD_METADATA__
    global __CLOUD_TAGS__

    if not __CLOUD_METADATA__:
        pipe_api = PipelineAPI(api_url, None)
        preference = pipe_api.get_preference(NETWORKS_PARAM)
        if preference:
            data = json.loads(preference['value'])
            if 'regions' not in data:
                pipe_log('Malformed networks config file: missing "regions" section. Update config file.')
                raise RuntimeError('Malformed networks config file: missing "regions" section. Update config file.')
            __CLOUD_METADATA__ = data['regions']
            if 'tags' in data:
                __CLOUD_TAGS__ = data['tags']
    return __CLOUD_METADATA__, __CLOUD_TAGS__


def get_region_settings(cloud_region):
    full_settings, tags = load_cloud_config()
    for region_settings in full_settings:
        if 'name' in region_settings and region_settings['name'] == cloud_region:
            return region_settings
    pipe_log('Failed to find networks settings for region: %s.' % cloud_region)
    return None


def get_cloud_config_section(cloud_region, section_name):
    cloud_metadata = get_region_settings(cloud_region)
    if cloud_metadata and section_name in cloud_metadata and len(cloud_metadata[section_name]) > 0:
        return cloud_metadata[section_name]
    else:
        return None


def get_networks_config(cloud_region):
    return get_cloud_config_section(cloud_region, "networks")


def get_instance_images_config(cloud_region):
    return get_cloud_config_section(cloud_region, "amis")


def get_allowed_zones(cloud_region):
    return list(get_networks_config(cloud_region).keys())


def get_security_groups(cloud_region):
    config = get_cloud_config_section(cloud_region, "security_group_ids")
    if not config:
        raise RuntimeError('Security group setting is required to run an instance')
    return config

def get_well_known_hosts(cloud_region):
    return get_cloud_config_section(cloud_region, "well_known_hosts")


def get_allowed_instance_image(cloud_region, instance_type, default_image):
    default_init_script = os.path.dirname(os.path.abspath(__file__)) + '/init.sh'
    default_object = {"instance_mask_ami": default_image, "instance_mask": None, "init_script": default_init_script}

    instance_images_config = get_instance_images_config(cloud_region)
    if not instance_images_config:
        return default_object

    for image_config in instance_images_config:
        instance_mask = image_config["instance_mask"]
        instance_mask_ami = image_config["ami"]
        init_script = None
        if "init_script" in image_config:
            init_script = image_config["init_script"]
        else:
            init_script = default_object["init_script"]
        if fnmatch.fnmatch(instance_type, instance_mask):
            return {"instance_mask_ami": instance_mask_ami, "instance_mask": instance_mask, "init_script": init_script}

    return default_object


#############################

SUBNET_ID_PARTS_NUMBER = 11
SUBNET_NAME_INDEX = 10
SUBNET_TYPE_INDEX = 9

RESOURCE_ID_PARTS_NUMBER = 9
RESOURCE_NAME_INDEX = 8
RESOURCE_TYPE_INDEX = 7
RESOURCE_GROUP_NAME_INDEX = 4
RESOURCE_GROUP_KEY_INDEX = 3


zone = None
resource_client = get_client_from_auth_file(ResourceManagementClient)
network_client = get_client_from_auth_file(NetworkManagementClient)
compute_client = get_client_from_auth_file(ComputeManagementClient)
resource_group_name = os.environ["AZURE_RESOURCE_GROUP"]


def run_instance(instance_type, cloud_region, run_id, ins_hdd, ins_img, ssh_pub_key, user,
                 ins_type, is_spot, kube_ip, kubeadm_token):
    try:
        ins_key = read_ssh_key(ssh_pub_key)
        user_data_script = get_user_data_script(cloud_region, ins_type, ins_img, kube_ip, kubeadm_token)
        instance_name = VM_NAME_PREFIX + uuid.uuid4().hex[0:UUID_LENGHT]
        if not is_spot:
            create_public_ip_address(instance_name, run_id)
            create_nic(instance_name, run_id)
            return create_vm(instance_name, run_id, instance_type, ins_img, ins_hdd,
                             user_data_script, ins_key, user)
        else:
            return create_low_priority_vm(instance_name, run_id, instance_type, ins_img, ins_hdd,
                                          user_data_script, ins_key, user)
    except Exception as e:
        delete_all_by_run_id(run_id)
        raise e


def read_ssh_key(ssh_pub_key):
    with open(ssh_pub_key) as f:
        content = f.readlines()
        if len(content) != 1 and not content[0].startswith("ssh-rsa"):
            raise RuntimeError("Wrong format of ssh pub key!")
    ins_key = content[0]
    return ins_key


def create_public_ip_address(instance_name, run_id):
    public_ip_addess_params = {
        'location': zone,
        'public_ip_allocation_method': 'Dynamic',
        'dns_settings': {
            'domain_name_label': instance_name
        },
        'tags': get_tags(run_id)
    }
    creation_result = network_client.public_ip_addresses.create_or_update(
        resource_group_name,
        instance_name + '-ip',
        public_ip_addess_params
    )

    return creation_result.result()


def create_nic(instance_name, run_id):

    public_ip_address = network_client.public_ip_addresses.get(
        resource_group_name,
        instance_name + '-ip'
    )

    subnet_info = get_subnet_info()
    security_group_info = get_security_group_info()

    nic_params = {
        'location': zone,
        'ipConfigurations': [{
            'name': 'IPConfig',
            'publicIpAddress': public_ip_address,
            'subnet': {
                'id': subnet_info.id
            }
        }],
        "networkSecurityGroup": {
            'id': security_group_info.id
        },
        'tags': get_tags(run_id)
    }
    creation_result = network_client.network_interfaces.create_or_update(
        resource_group_name,
        instance_name + '-nic',
        nic_params
    )

    return creation_result.result()


def get_security_group_info():
    security_groups = get_security_groups(zone)
    if len(security_groups) != 1:
        raise AssertionError("Please specify only one security group!")
    resource_group, secur_grp = get_res_grp_and_res_name_from_string(security_groups[0], 'networkSecurityGroups')
    security_group_info = network_client.network_security_groups.get(resource_group, secur_grp)
    return security_group_info


def get_subnet_info():
    allowed_networks = get_networks_config(zone)
    if allowed_networks and len(allowed_networks) > 0:
        az_num = randint(0, len(allowed_networks) - 1)
        az_name = allowed_networks.items()[az_num][0]
        subnet_id = allowed_networks.items()[az_num][1]
        resource_group, network = get_res_grp_and_res_name_from_string(az_name, 'virtualNetworks')
        subnet = get_subnet_name_from_id(subnet_id)
        pipe_log('- Networks list found, subnet {} in VNET {} will be used'.format(subnet_id, az_name))
    else:
        pipe_log('- Networks list NOT found, trying to find network from region in the same resource group...')
        resource_group, network, subnet = get_any_network_from_location(zone)
        pipe_log('- Network found, subnet {} in VNET {} will be used'.format(subnet, network))
    if not resource_group or not network or not subnet:
        raise RuntimeError(
            "No networks with subnet found for location: {} in resourceGroup: {}".format(zone, resource_group_name))
    subnet_info = network_client.subnets.get(resource_group, network, subnet)
    return subnet_info


def get_any_network_from_location(location):
    resource_group, network, subnet = None, None, None

    for vnet in resource_client.resources.list(filter="resourceType eq 'Microsoft.Network/virtualNetworks' "
                                                      "and location eq '{}' "
                                                      "and resourceGroup eq '{}'".format(location, resource_group_name)):
        resource_group, network = get_res_grp_and_res_name_from_string(vnet.id, 'virtualNetworks')
        break

    if not resource_group or not network:
        return resource_group, network, subnet

    for subnet_res in network_client.subnets.list(resource_group, network):
        subnet = get_subnet_name_from_id(subnet_res.id)
        break
    return resource_group, network, subnet


def get_disk_type(instance_type):
    disk_type = None
    for sku in compute_client.resource_skus.list():
        if sku.locations[0].lower() == zone.lower() and sku.resource_type.lower() == "virtualmachines" \
                and sku.name.lower() == instance_type.lower():
            for capability in sku.capabilities:
                if capability.name.lower() == "premiumio":
                    disk_type = "Premium_LRS" if capability.value.lower() == "true" else "StandardSSD_LRS"
                    break
    return disk_type


def get_os_profile(instance_name, ssh_pub_key, user, user_data_script, computer_name_parameter):
    profile = {
        computer_name_parameter: instance_name,
        'admin_username': user,
        "linuxConfiguration": {
            "ssh": {
                "publicKeys": [
                    {
                        "path": "/home/" + user + "/.ssh/authorized_keys",
                        "key_data": "{key}".format(key=ssh_pub_key)
                    }
                ]
            },
            "disablePasswordAuthentication": True,
        },
        "custom_data": base64.b64encode(user_data_script)
    }
    return profile


def get_storage_profile(disk, image, instance_type):
    return {
        'image_reference': {
            'id': image.id
        },
        "osDisk": {
            "caching": "ReadWrite",
            "managedDisk": {
                "storageAccountType": get_disk_type(instance_type)
            },
            "createOption": "FromImage"
        },
        "dataDisks": [
            {
                "diskSizeGB": disk,
                "lun": 63,
                "createOption": "Empty",
                "managedDisk": {
                    "storageAccountType": get_disk_type(instance_type)
                }
            }
        ]
    }


def create_vm(instance_name, run_id, instance_type, instance_image, disk, user_data_script,
              ssh_pub_key, user):
    nic = network_client.network_interfaces.get(
        resource_group_name,
        instance_name + '-nic'
    )
    resource_group, image_name = get_res_grp_and_res_name_from_string(instance_image, 'images')

    image = compute_client.images.get(resource_group, image_name)

    storage_profile = get_storage_profile(disk, image, instance_type)
    storage_profile["dataDisks"][0]["name"] = instance_name + "-data"

    vm_parameters = {
        'location': zone,
        'os_profile': get_os_profile(instance_name, ssh_pub_key, user, user_data_script, 'computer_name'),
        'hardware_profile': {
            'vm_size': instance_type
        },
        'storage_profile': storage_profile,
        'network_profile': {
            'network_interfaces': [{
                'id': nic.id
            }]
        },
        'tags': get_tags(run_id)
    }

    create_node_resource(compute_client.virtual_machines, instance_name, vm_parameters)

    private_ip = network_client.network_interfaces.get(
        resource_group_name, instance_name + '-nic').ip_configurations[0].private_ip_address

    return instance_name, private_ip


def create_low_priority_vm(scale_set_name, run_id, instance_type, instance_image, disk, user_data_script, ssh_pub_key, user):

    pipe_log('Create VMScaleSet with low priority instance for run: {}'.format(run_id))
    resource_group, image_name = get_res_grp_and_res_name_from_string(instance_image, 'images')

    image = compute_client.images.get(resource_group, image_name)
    subnet_info = get_subnet_info()
    security_group_info = get_security_group_info()

    service = compute_client.virtual_machine_scale_sets

    vmss_parameters = {
        "location": zone,
        "sku": {
            "name": instance_type,
            "capacity": "1"
        },
        "upgradePolicy": {
            "mode": "Manual",
            "automaticOSUpgrade": False
        },
        "properties": {
            "virtualMachineProfile": {
                'priority': 'Low',
                'evictionPolicy': 'delete',
                'os_profile': get_os_profile(scale_set_name, ssh_pub_key, user, user_data_script, 'computer_name_prefix'),
                'storage_profile': get_storage_profile(disk, image, instance_type),
                "network_profile": {
                    "networkInterfaceConfigurations": [
                        {
                            "name": scale_set_name + "-nic",
                            "properties": {
                                "primary": True,
                                "networkSecurityGroup": {
                                    "id": security_group_info.id
                                },
                                'dns_settings': {
                                    'domain_name_label': scale_set_name
                                },
                                "ipConfigurations": [
                                    {
                                        "name": scale_set_name + "-ip",
                                        "publicIPAddressConfiguration": {
                                            "name": scale_set_name + "-publicip"
                                        },
                                        "properties": {
                                            "subnet": {
                                                "id": subnet_info.id
                                            }
                                        }
                                    }
                                ]
                            }
                        }
                    ]
                }
            }
        },
        'tags': get_tags(run_id)
    }

    create_node_resource(service, scale_set_name, vmss_parameters)

    return get_instance_name_and_private_ip_from_vmss(scale_set_name)


def create_node_resource(service, instance_name, node_parameters):
    try:
        creation_result = service.create_or_update(
            resource_group_name,
            instance_name,
            node_parameters
        )
        creation_result.result()

        start_result = service.start(resource_group_name, instance_name)
        start_result.wait()
    except CloudError as client_error:
        delete_all_by_run_id(node_parameters['tags']['Name'])
        error_message = client_error.__str__()
        if 'OperationNotAllowed' in error_message or 'ResourceQuotaExceeded' in error_message:
            pipe_log_warn(LIMIT_EXCEEDED_ERROR_MASSAGE)
            sys.exit(LIMIT_EXCEEDED_EXIT_CODE)
        else:
            raise client_error


def get_instance_name_and_private_ip_from_vmss(scale_set_name):
    vm_vmss_id = None
    for vm in compute_client.virtual_machine_scale_set_vms.list(resource_group_name, scale_set_name):
        vm_vmss_id = vm.instance_id
        break
    instance_name = compute_client.virtual_machine_scale_set_vms \
        .get_instance_view(resource_group_name, scale_set_name, vm_vmss_id) \
        .additional_properties["computerName"]
    private_ip = network_client.network_interfaces. \
        get_virtual_machine_scale_set_ip_configuration(resource_group_name, scale_set_name, vm_vmss_id,
                                                       scale_set_name + "-nic", scale_set_name + "-ip") \
        .private_ip_address
    return instance_name, private_ip


def get_cloud_region(region_id):
    if region_id is not None:
        return region_id
    regions, tags = load_cloud_config()
    for region in regions:
        if 'default' in region and region['default']:
            return region['name']
    pipe_log('Failed to determine region for Azure instance')
    raise RuntimeError('Failed to determine region for Azure instance')


def increment_or_fail(num_rep, rep, error_message, kill_instance_id_on_fail=None):
    rep = rep + 1
    if rep > num_rep:
        if kill_instance_id_on_fail:
            pipe_log('[ERROR] Operation timed out and an instance {} will be terminated'.format(kill_instance_id_on_fail))

            terminate_instance(kill_instance_id_on_fail)
        raise RuntimeError(error_message)
    return rep


def resource_tags():
    tags = {}
    config_regions, config_tags = load_cloud_config()
    if config_tags is None:
        return tags
    for key, value in config_tags.iteritems():
        tags.update({key: value})
    return tags


def run_id_tag(run_id):
    return {
        'Name': run_id,
    }


def get_tags(run_id):
    tags = run_id_tag(run_id)
    res_tags = resource_tags()
    if res_tags:
        tags.update(res_tags)
    return tags


def verify_run_id(run_id):
    pipe_log('Checking if instance already exists for RunID {}'.format(run_id))
    vm_name = None
    private_ip = None
    for resource in resource_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + run_id + "'"):
        if str(resource.type).split('/')[-1] == "virtualMachines":
            vm_name = resource.name
            private_ip = network_client.network_interfaces\
                .get(resource_group_name, vm_name + '-nic').ip_configurations[0].private_ip_address
            break
        if str(resource.type).split('/')[-1] == "virtualMachineScaleSet":
            scale_set_name = resource.name
            vm_name, private_ip = get_instance_name_and_private_ip_from_vmss(scale_set_name)
            break

    return vm_name, private_ip


# There is a strange behavior, when you create scale set with one node,
# several node will be created at first and then only one of it will stay as running
# Some times other nodes can rich startup script before it will be terminated, so nodes will join a kube cluster
# In order to get rid of this 'phantom' nodes this method will delete nodes with name like computerNamePrefix + 000000
def delete_phantom_low_priority_kubernetes_node(kube_api, ins_id):
    low_priority_search = re.search(LOW_PRIORITY_INSTANCE_ID_TEMPLATE, ins_id)
    if low_priority_search:
        scale_set_name = low_priority_search.group(1)

        # according to naming of azure scale set nodes: computerNamePrefix + hex postfix (like 000000)
        # delete node that opposite to ins_id
        nodes_to_delete = [scale_set_name + '%0*x' % (6, x) for x in range(0, 15)]
        for node_to_delete in nodes_to_delete:

            if node_to_delete == ins_id:
                continue

            nodes = pykube.Node.objects(kube_api).filter(field_selector={'metadata.name': node_to_delete})
            for node in nodes.response['items']:
                obj = {
                    "apiVersion": "v1",
                    "kind": "Node",
                    "metadata": {
                        "name": node["metadata"]["name"]
                    }
                }
                pykube.Node(kube_api, obj).delete()


def find_node(nodename, nodename_full, api):
    ret_namenode = get_nodename(api, nodename)
    if not ret_namenode:
        return get_nodename(api, nodename_full)
    else:
        return ret_namenode


def get_nodename(api, nodename):
    node = pykube.Node.objects(api).filter(field_selector={'metadata.name': nodename})
    if len(node.response['items']) > 0:
        return nodename
    else:
        return ''


def verify_regnode(ins_id, num_rep, time_rep, api):
    ret_namenode = ''
    rep = 0
    while rep <= num_rep:
        ret_namenode = find_node(ins_id, ins_id, api)
        if ret_namenode:
            break
        rep = increment_or_fail(num_rep, rep,
                                'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) cluster registration'.format(num_rep, ins_id, ins_id), ins_id)
        sleep(time_rep)

    if ret_namenode:  # useless?
        pipe_log('- Node registered in cluster as {}'.format(ret_namenode))
        rep = 0
        while rep <= num_rep:
            node = pykube.Node.objects(api).filter(field_selector={'metadata.name': ret_namenode})
            status = node.response['items'][0]['status']['conditions'][3]['status']
            if status == u'True':
                pipe_log('- Node ({}) status is READY'.format(ret_namenode))
                break
            rep = increment_or_fail(num_rep, rep,
                                    'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) kube node READY check'.format(num_rep, ins_id, ret_namenode), ins_id)
            sleep(time_rep)

        rep = 0
        pipe_log('- Waiting for system agents initialization...')
        while rep <= num_rep:
            pods = pykube.objects.Pod.objects(api).filter(namespace="kube-system",
                                                          field_selector={"spec.nodeName": ret_namenode})
            count_pods = len(pods.response['items'])
            ready_pods = len([p for p in pods if p.ready])
            if count_pods == ready_pods:
                break
            pipe_log('- {} of {} agents initialized. Still waiting...'.format(ready_pods, count_pods))
            rep = increment_or_fail(num_rep, rep,
                                    'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) kube system pods check'.format(num_rep, ins_id, ret_namenode), ins_id)
            sleep(time_rep)
        pipe_log('Instance {} successfully registred in cluster with name {}\n-'.format(ins_id, ins_id))
    return ret_namenode


def label_node(nodename, run_id, api, cluster_name, cluster_role, cloud_region):
    pipe_log('Assigning instance {} to RunID: {}'.format(nodename, run_id))
    obj = {
        "apiVersion": "v1",
        "kind": "Node",
        "metadata": {
            "name": nodename,
            "labels": {
                "runid": run_id,
                "cloud_region": cloud_region
            }
        }
    }

    if cluster_name:
        obj["metadata"]["labels"]["cp-cluster-name"] = cluster_name
    if cluster_role:
        obj["metadata"]["labels"]["cp-cluster-role"] = cluster_role

    pykube.Node(api, obj).update()
    pipe_log('Instance {} is assigned to RunID: {}\n-'.format(nodename, run_id))


def get_certs_string():
    global api_token
    global api_url
    command_pattern = 'mkdir -p /etc/docker/certs.d/{url} && echo "{cert}" >> /etc/docker/certs.d/{url}/ca.crt'
    if api_url and api_token:
        pipe_api = PipelineAPI(api_url, None)
        result = pipe_api.load_certificates()
        if not result:
            return ""
        else:
            entries = []
            for url, cert in result.iteritems():
                entries.append(command_pattern.format(url=url, cert=cert))
            return " && ".join(entries)
    return ""


def get_well_known_hosts_string(cloud_region):
    pipe_log('Setting well-known hosts an instance in {} region'.format(cloud_region))
    command_pattern = 'echo {well_known_ip} {well_known_host} >> /etc/hosts'
    well_known_list = get_well_known_hosts(cloud_region)
    if not well_known_list or len(well_known_list) == 0:
        return ''

    entries = []
    for well_known_item in well_known_list:
        if not 'ip' in well_known_item or not 'host' in well_known_item:
            continue

        well_known_ip = well_known_item['ip']
        well_known_host = well_known_item['host']
        if not well_known_ip or not well_known_host:
            continue

        entries.append(command_pattern.format(well_known_ip=well_known_ip, well_known_host=well_known_host))
        pipe_log('-> {}={}'.format(well_known_ip, well_known_host))

    if len(entries) == 0:
        return ''
    return ' && '.join(entries)


def resolve_azure_api(resource):
    """ This method retrieves the latest non-preview api version for
    the given resource (unless the preview version is the only available
    api version) """
    provider = resource_client.providers.get(resource.id.split('/')[6])
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


def terminate_instance(ins_id):
    instance = compute_client.virtual_machines.get(resource_group_name, ins_id)
    if 'Name' in instance.tags:
        delete_all_by_run_id(instance.tags['Name'])
    else:
        compute_client.virtual_machines.delete(resource_group_name, ins_id).wait()


def delete_all_by_run_id(run_id):
    resources = []
    resources.extend(resource_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + run_id + "'"))
    if len(resources) > 0:
        # we need to sort resources to be sure that vm and nic will be deleted first,
        # because it has attached resorces(disks and ip)
        resources.sort(key=functools.cmp_to_key(azure_resource_type_cmp))
        vm_name = resources[0].name if str(resources[0].type).split('/')[-1].startswith('virtualMachine') else resources[0].name[0:len(VM_NAME_PREFIX) + UUID_LENGHT]
        if str(resources[0].type).split('/')[-1] == 'virtualMachines':
            detach_disks_and_nic(vm_name)
        for resource in resources:
            resource_client.resources.delete(
                resource_group_name=resource.id.split('/')[4],
                resource_provider_namespace=resource.id.split('/')[6],
                parent_resource_path='',
                resource_type=str(resource.type).split('/')[-1],
                resource_name=resource.name,
                api_version=resolve_azure_api(resource),
                parameters=resource
            ).wait()


def detach_disks_and_nic(vm_name):
    compute_client.virtual_machines.delete(resource_group_name, vm_name).wait()
    try:
        nic = network_client.network_interfaces.get(resource_group_name, vm_name + '-nic')
        nic.ip_configurations[0].public_ip_address = None
        network_client.network_interfaces.create_or_update(resource_group_name, vm_name + '-nic', nic).wait()
    except Exception as e:
        print e


def replace_common_params(cloud_region, init_script, config_section):
    pipe_log('Configuring {} settings for an instance in {} region'.format(config_section, cloud_region))
    common_list = get_cloud_config_section(cloud_region, config_section)
    if not common_list:
        return init_script

    for common_item in common_list:
        if not 'name' in common_item or not 'path' in common_item:
            continue
        item_name = common_item['name']
        item_path = common_item['path']
        if not item_name:
            continue
        if item_path == None:
            item_path = ''
        init_script = init_script.replace('@' + item_name +  '@', item_path)
        pipe_log('-> {}={}'.format(item_name, item_path))

    return init_script


def replace_proxies(cloud_region, init_script):
    return replace_common_params(cloud_region, init_script, "proxies")


def replace_swap(cloud_region, init_script):
    return replace_common_params(cloud_region, init_script, "swap")


def get_user_data_script(cloud_region, ins_type, ins_img, kube_ip, kubeadm_token):
    allowed_instance = get_allowed_instance_image(cloud_region, ins_type, ins_img)
    if allowed_instance and allowed_instance["init_script"]:
        init_script = open(allowed_instance["init_script"], 'r')
        user_data_script = init_script.read()
        certs_string = get_certs_string()
        well_known_string = get_well_known_hosts_string(cloud_region)
        init_script.close()
        user_data_script = replace_proxies(cloud_region, user_data_script)
        user_data_script = replace_swap(cloud_region, user_data_script)
        return user_data_script\
            .replace('@DOCKER_CERTS@', certs_string) \
            .replace('@WELL_KNOWN_HOSTS@', well_known_string) \
            .replace('@KUBE_IP@', kube_ip) \
            .replace('@KUBE_TOKEN@', kubeadm_token)
    else:
        raise RuntimeError('Unable to get init.sh path')


def get_res_grp_and_res_name_from_string(resource_id, resource_type):
    resource_params = resource_id.split("/")

    if len(resource_params) == 2:
        resource_group, resource = resource_params[0], resource_params[1]
    # according to full ID form: /subscriptions/<sub-id>/resourceGroups/<res-grp>/providers/Microsoft.Compute/images/<image>
    elif len(resource_params) == RESOURCE_ID_PARTS_NUMBER \
            and resource_params[RESOURCE_GROUP_KEY_INDEX] == 'resourceGroups' \
            and resource_params[RESOURCE_TYPE_INDEX] == resource_type:
        resource_group, resource = resource_params[RESOURCE_GROUP_NAME_INDEX], resource_params[RESOURCE_NAME_INDEX]
    else:
        raise RuntimeError(
            "Resource parameter doesn't match to Azure resource name convention: <resource_group>/<resource_name>"
            " or full resource id: /subscriptions/<sub-id>/resourceGroups/<res-grp>/providers/Microsoft.Compute/<type>/<name>. "
            "Node Up process will be stopped.")
    return resource_group, resource


def get_subnet_name_from_id(subnet_id):
    if "/" not in subnet_id:
        return subnet_id
    subnet_params = subnet_id.split("/")
    # according to /subscriptions/<sub>/resourceGroups/<res_grp>/providers/Microsoft.Network/virtualNetworks/<vnet>/subnets/<subnet>
    if len(subnet_params) == SUBNET_ID_PARTS_NUMBER \
            and subnet_params[RESOURCE_GROUP_KEY_INDEX] == "resourceGroups" \
            and subnet_params[SUBNET_TYPE_INDEX] == "subnets":
        return subnet_params[SUBNET_NAME_INDEX]
    else:
        raise RuntimeError("Subnet dont match form of the Azure ID "
                           "/subscriptions/<sub>/resourceGroups/<res_grp>/providers/Microsoft.Network/virtualNetworks/<vnet>/subnets/<subnet>: {}".format(subnet_id))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ins_key", type=str, required=True)
    parser.add_argument("--run_id", type=str, required=True)
    parser.add_argument("--cluster_name", type=str, required=False)
    parser.add_argument("--cluster_role", type=str, required=False)
    parser.add_argument("--ins_type", type=str, default='Standard_B2s')
    parser.add_argument("--ins_hdd", type=int, default=30)
    parser.add_argument("--ins_img", type=str, default='pipeline-azure-group/pipeline-base-image')
    parser.add_argument("--num_rep", type=int, default=100)
    parser.add_argument("--time_rep", type=int, default=3)
    parser.add_argument("--is_spot", type=bool, default=False)
    parser.add_argument("--bid_price", type=float, default=1.0)
    parser.add_argument("--kube_ip", type=str, required=True)
    parser.add_argument("--kubeadm_token", type=str, required=True)
    parser.add_argument("--kms_encyr_key_id", type=str, required=False)
    parser.add_argument("--region_id", type=str, default=None)

    args, unknown = parser.parse_known_args()
    ins_key_path = args.ins_key
    run_id = args.run_id
    ins_type = args.ins_type
    ins_hdd = args.ins_hdd
    ins_img = args.ins_img
    is_spot = args.is_spot
    num_rep = args.num_rep
    time_rep = args.time_rep
    cluster_name = args.cluster_name
    cluster_role = args.cluster_role
    kube_ip = args.kube_ip
    kubeadm_token = args.kubeadm_token
    region_id = args.region_id

    global zone
    zone = region_id

    if not kube_ip or not kubeadm_token:
        raise RuntimeError('Kubernetes configuration is required to create a new node')

    pipe_log_init(run_id)

    cloud_region = get_cloud_region(region_id)
    pipe_log('Started initialization of new calculation node in cloud region {}:\n'
             '- RunID: {}\n'
             '- Type: {}\n'
             '- Disk: {}\n'
             '- Image: {}\n'.format(cloud_region,
                                    run_id,
                                    ins_type,
                                    ins_hdd,
                                    ins_img))

    try:

        # Redefine default instance image if cloud metadata has specific rules for instance type
        allowed_instance = get_allowed_instance_image(cloud_region, ins_type, ins_img)
        if allowed_instance and allowed_instance["instance_mask"]:
            pipe_log('Found matching rule {instance_mask}/{ami} for requested instance type {instance_type}'
                     '\nImage {ami} will be used'.format(instance_mask=allowed_instance["instance_mask"],
                                                         ami=allowed_instance["instance_mask_ami"], instance_type=ins_type))
            ins_img = allowed_instance["instance_mask_ami"]

        ins_id, ins_ip = verify_run_id(run_id)

        if not ins_id:
            ins_id, ins_ip = run_instance(ins_type, cloud_region, run_id, ins_hdd, ins_img, ins_key_path, "pipeline",
                                          ins_type, is_spot, kube_ip, kubeadm_token)


        try:
            api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
        except Exception as e:
            api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
        api.session.verify = False

        delete_phantom_low_priority_kubernetes_node(api, ins_id)
        nodename = verify_regnode(ins_id, num_rep, time_rep, api)
        label_node(nodename, run_id, api, cluster_name, cluster_role, cloud_region)
        pipe_log('Node created:\n'
                 '- {}\n'
                 '- {}'.format(ins_id, ins_ip))

        # External process relies on this output
        print(ins_id + "\t" + ins_ip + "\t" + nodename)

        pipe_log('{} task finished'.format(NODEUP_TASK), status=TaskStatus.SUCCESS)
    except Exception as e:
        delete_all_by_run_id(run_id)
        pipe_log('[ERROR] ' + str(e), status=TaskStatus.FAILURE)
        raise e


if __name__ == '__main__':
    main()
