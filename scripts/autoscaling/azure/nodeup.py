# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
import math
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
from azure.common.client_factory import get_client_from_auth_file, get_client_from_cli_profile
from azure.mgmt.resource import ResourceManagementClient
from azure.mgmt.compute import ComputeManagementClient
from azure.mgmt.network import NetworkManagementClient
from msrestazure.azure_exceptions import CloudError
from pipeline import Logger, TaskStatus, PipelineAPI, pack_script_contents
import jwt

VM_NAME_PREFIX = "az-"
UUID_LENGHT = 16

DISABLE_ACCESS = 'disable_external_access'
NETWORKS_PARAM = "cluster.networks.config"
NODEUP_TASK = "InitializeNode"
LIMIT_EXCEEDED_EXIT_CODE = 6
LIMIT_EXCEEDED_ERROR_MASSAGE = 'Instance limit exceeded. A new one will be launched as soon as free space will be available.'
LOW_PRIORITY_INSTANCE_ID_TEMPLATE = '(az-[a-z0-9]{16})[0-9A-Z]{6}'

MIN_SWAP_DEVICE_SIZE = 5

current_run_id = 0
api_url = None
api_token = None
script_path = None


def is_run_id_numerical(run_id):
    try:
        int(run_id)
        return True
    except ValueError:
        return False


def is_api_logging_enabled():
    global api_token
    global api_url
    global current_run_id
    return is_run_id_numerical(current_run_id) and api_url and api_token


def pipe_log_init(run_id):
    global api_token
    global api_url
    global current_run_id
    current_run_id = run_id

    api_url = os.environ["API"]
    api_token = os.environ["API_TOKEN"]

    if not is_api_logging_enabled():
        logging.basicConfig(filename='nodeup.log', level=logging.INFO, format='%(asctime)s %(message)s')


def pipe_log_warn(message):
    global api_token
    global api_url
    global script_path
    global current_run_id

    if is_api_logging_enabled():
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

    if is_api_logging_enabled():
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
    default_embedded_scripts = { "fsautoscale": os.path.dirname(os.path.abspath(__file__)) + '/fsautoscale.sh' }
    default_object = { "instance_mask_ami": default_image, "instance_mask": None, "init_script": default_init_script,
        "embedded_scripts": default_embedded_scripts }

    instance_images_config = get_instance_images_config(cloud_region)
    if not instance_images_config:
        return default_object

    for image_config in instance_images_config:
        instance_mask = image_config["instance_mask"]
        instance_mask_ami = image_config["ami"]
        init_script = image_config.get("init_script", default_object["init_script"])
        embedded_scripts = image_config.get("embedded_scripts", default_object["embedded_scripts"])
        if fnmatch.fnmatch(instance_type, instance_mask):
            return { "instance_mask_ami": instance_mask_ami, "instance_mask": instance_mask, "init_script": init_script,
            "embedded_scripts": embedded_scripts }

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
auth_file = os.environ.get('AZURE_AUTH_LOCATION', None)
if auth_file:
    resource_client = get_client_from_auth_file(ResourceManagementClient, auth_path=auth_file)
    network_client = get_client_from_auth_file(NetworkManagementClient, auth_path=auth_file)
    compute_client = get_client_from_auth_file(ComputeManagementClient, auth_path=auth_file)
else:
    resource_client = get_client_from_cli_profile(ResourceManagementClient)
    network_client = get_client_from_cli_profile(NetworkManagementClient)
    compute_client = get_client_from_cli_profile(ComputeManagementClient)

resource_group_name = os.environ["AZURE_RESOURCE_GROUP"]


def run_instance(api_url, api_token, instance_name, instance_type, cloud_region, run_id, ins_hdd, ins_img, ssh_pub_key, user,
                 ins_type, is_spot, kube_ip, kubeadm_token, pre_pull_images):
    ins_key = read_ssh_key(ssh_pub_key)
    swap_size = get_swap_size(cloud_region, ins_type, is_spot)
    user_data_script = get_user_data_script(api_url, api_token, cloud_region, ins_type, ins_img, kube_ip, kubeadm_token,
                                            swap_size, pre_pull_images)
    access_config = get_access_config(cloud_region)
    disable_external_access = False
    if access_config is not None:
        disable_external_access = DISABLE_ACCESS in access_config and access_config[DISABLE_ACCESS]
    if not is_spot:
        create_nic(instance_name, run_id, disable_external_access)
        return create_vm(instance_name, run_id, instance_type, ins_img, ins_hdd,
                         user_data_script, ins_key, user, swap_size)
    else:
        return create_low_priority_vm(instance_name, run_id, instance_type, ins_img, ins_hdd,
                                      user_data_script, ins_key, user, swap_size, disable_external_access)


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


def create_nic(instance_name, run_id, disable_external_access):
    subnet_info = get_subnet_info()
    security_group_info = get_security_group_info()

    nic_params = {
        'location': zone,
        'ipConfigurations': [{
            'name': 'IPConfig',
            'subnet': {
                'id': subnet_info.id
            }
        }],
        "networkSecurityGroup": {
            'id': security_group_info.id
        },
        'tags': get_tags(run_id)
    }

    if not disable_external_access:
        create_public_ip_address(instance_name, run_id)
        public_ip_address = network_client.public_ip_addresses.get(
            resource_group_name,
            instance_name + '-ip'
        )
        nic_params["ipConfigurations"][0]["publicIpAddress"] = public_ip_address

    creation_result = network_client.network_interfaces.create_or_update(
        resource_group_name,
        instance_name + '-nic',
        nic_params
    )

    return creation_result.result()


def get_access_config(cloud_region):
    return get_cloud_config_section(cloud_region, "access_config")


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


def get_data_disk(size, disk_type, lun, disk_name=None):
    disk = {
                "diskSizeGB": size,
                "lun": lun,
                "createOption": "Empty",
                "managedDisk": {
                    "storageAccountType": disk_type
                }
            }
    if disk_name is not None:
        disk["name"] = disk_name
    return disk


def get_storage_profile(disk, image, instance_type,
                        instance_name=None, swap_size=None):
    disk_type = get_disk_type(instance_type)
    disk_name = None if instance_name is None else instance_name + "-data"
    disk_lun = 62
    data_disks = [get_data_disk(disk, disk_type, disk_lun, disk_name=disk_name)]
    if swap_size is not None and swap_size > 0:
        swap_name = None if instance_name is None else instance_name + "-swap"
        data_disks.append(get_data_disk(swap_size, disk_type, disk_lun + 1, disk_name=swap_name))
    return {
        'image_reference': {
            'id': image.id
        },
        "osDisk": {
            "caching": "ReadWrite",
            "managedDisk": {
                "storageAccountType": disk_type
            },
            "createOption": "FromImage"
        },
        "dataDisks": data_disks
    }


def create_vm(instance_name, run_id, instance_type, instance_image, disk, user_data_script,
              ssh_pub_key, user, swap_size):
    nic = network_client.network_interfaces.get(
        resource_group_name,
        instance_name + '-nic'
    )
    resource_group, image_name = get_res_grp_and_res_name_from_string(instance_image, 'images')

    image = compute_client.images.get(resource_group, image_name)

    storage_profile = get_storage_profile(disk, image, instance_type,
                                          instance_name=instance_name,
                                          swap_size=swap_size)
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


def create_low_priority_vm(scale_set_name, run_id, instance_type, instance_image, disk, user_data_script,
                           ssh_pub_key, user, swap_size, disable_external_access):

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
            "overprovision": False,
            "virtualMachineProfile": {
                'priority': 'Low',
                'evictionPolicy': 'delete',
                'os_profile': get_os_profile(scale_set_name, ssh_pub_key, user, user_data_script, 'computer_name_prefix'),
                'storage_profile': get_storage_profile(disk, image, instance_type, swap_size=swap_size),
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
    if not disable_external_access:
        vmss_parameters['properties']['virtualMachineProfile'] \
                       ['network_profile']['networkInterfaceConfigurations'][0] \
                       ['properties']['ipConfigurations'][0] \
                       ['publicIPAddressConfiguration'] = {"name": scale_set_name + "-publicip"}
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
    if vm_vmss_id is None:
        pipe_log('Failed to find instance in ScaleSet: {}. Seems that instance was preempted.'.format(scale_set_name))
        raise RuntimeError('Failed to find instance in ScaleSet: {}. Seems that instance was preempted.'.format(scale_set_name))

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


def generate_scale_set_vm_names(scale_set_name):
    nodes_to_delete = [scale_set_name + '%0*x' % (6, x) for x in range(0, 15)]
    return nodes_to_delete


def delete_scale_set_nodes_from_kube(kube_api, scale_set_name):
    nodes_to_delete = generate_scale_set_vm_names(scale_set_name)
    for node_to_delete in nodes_to_delete:
        delete_node_from_kube(kube_api, node_to_delete)


def delete_node_from_kube(kube_api, ins_id):
    nodes = pykube.Node.objects(kube_api).filter(field_selector={'metadata.name': ins_id})
    for node in nodes.response['items']:
        if any((condition['status'] == 'False' or condition['status'] == 'Unknown')
               and condition['type'] == "Ready" for condition in node["status"]["conditions"]):
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


def label_node(nodename, run_id, api, cluster_name, cluster_role, cloud_region, additional_labels):
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

    if additional_labels:
        for label in additional_labels:
            label_parts = label.split("=")
            if len(label_parts) == 1:
                obj["metadata"]["labels"][label_parts[0]] = None
            else:
                obj["metadata"]["labels"][label_parts[0]] = label_parts[1]

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


def replace_swap(swap_size, init_script):
    if swap_size is not None:
        return init_script.replace('@swap_size@', str(swap_size))
    return init_script


def get_swap_size(cloud_region, ins_type, is_spot):
    pipe_log('Configuring swap settings for an instance in {} region'.format(cloud_region))
    swap_params = get_cloud_config_section(cloud_region, "swap")
    if swap_params is None:
        return None
    swap_ratio = get_swap_ratio(swap_params)
    if swap_ratio is None:
        pipe_log("Swap ratio is not configured. Swap configuration will be skipped.")
        return None
    ram = get_instance_ram(cloud_region, ins_type, is_spot)
    if ram is None:
        pipe_log("Failed to determine instance RAM. Swap configuration will be skipped.")
        return None
    swap_size = int(math.ceil(swap_ratio * ram))
    if swap_size >= MIN_SWAP_DEVICE_SIZE:
        pipe_log("Swap device will be configured with size %d." % swap_size)
        return swap_size
    return None


def get_instance_ram(cloud_region, ins_type, is_spot):
    api = PipelineAPI(api_url, None)
    region_id = get_region_id(cloud_region, api)
    if region_id is None:
        return None
    instance_types = api.get_allowed_instance_types(region_id, spot=is_spot)
    ram = get_ram_from_group(instance_types, 'cluster.allowed.instance.types', ins_type)
    if ram is None:
        ram = get_ram_from_group(instance_types, 'cluster.allowed.instance.types.docker', ins_type)
    return ram


def get_ram_from_group(instance_types, group, instance_type):
    if group in instance_types:
        for current_type in instance_types[group]:
            if current_type['name'] == instance_type:
                return current_type['memory']
    return None


def get_region_id(cloud_region, api):
    regions = api.get_regions()
    if regions is None:
        return None
    for region in regions:
        if region.provider == 'AZURE' and region.region_id == cloud_region:
            return region.id
    return None


def get_swap_ratio(swap_params):
    for swap_param in swap_params:
        if not 'name' in swap_param or not 'path' in swap_param:
            continue
        item_name = swap_param['name']
        if item_name == 'swap_ratio':
            item_value = swap_param['path']
            if item_value:
                try:
                    return float(item_value)
                except ValueError:
                    pipe_log("Unexpected swap_ratio value: {}".format(item_value))
    return None


def replace_docker_images(pre_pull_images, user_data_script):
    global api_token
    payload = jwt.decode(api_token, verify=False)
    if 'sub' in payload:
        subject = payload['sub']
        user_data_script = user_data_script \
            .replace("@PRE_PULL_DOCKERS@", ",".join(pre_pull_images)) \
            .replace("@API_USER@", subject)
        return user_data_script
    else:
        raise RuntimeError("Pre-pulled docker initialization failed: unable to parse JWT token for docker auth.")


def get_user_data_script(api_url, api_token, cloud_region, ins_type, ins_img, kube_ip, kubeadm_token, swap_size,
                         pre_pull_images):
    allowed_instance = get_allowed_instance_image(cloud_region, ins_type, ins_img)
    if allowed_instance and allowed_instance["init_script"]:
        init_script = open(allowed_instance["init_script"], 'r')
        user_data_script = init_script.read()
        certs_string = get_certs_string()
        well_known_string = get_well_known_hosts_string(cloud_region)
        init_script.close()
        user_data_script = replace_proxies(cloud_region, user_data_script)
        user_data_script = replace_swap(swap_size, user_data_script)
        user_data_script = replace_docker_images(pre_pull_images, user_data_script)
        user_data_script = user_data_script.replace('@DOCKER_CERTS@', certs_string) \
                                            .replace('@WELL_KNOWN_HOSTS@', well_known_string) \
                                            .replace('@KUBE_IP@', kube_ip) \
                                            .replace('@KUBE_TOKEN@', kubeadm_token) \
                                            .replace('@API_URL@', api_url) \
                                            .replace('@API_TOKEN@', api_token)
        embedded_scripts = {}
        if allowed_instance["embedded_scripts"]:
            for embedded_name, embedded_path in allowed_instance["embedded_scripts"].items():
                embedded_scripts[embedded_name] = open(embedded_path, 'r').read()
        return pack_script_contents(user_data_script, embedded_scripts)
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
    parser.add_argument("--num_rep", type=int, default=250) # 250 x 3s = 12.5m
    parser.add_argument("--time_rep", type=int, default=3)
    parser.add_argument("--is_spot", type=bool, default=False)
    parser.add_argument("--bid_price", type=float, default=1.0)
    parser.add_argument("--kube_ip", type=str, required=True)
    parser.add_argument("--kubeadm_token", type=str, required=True)
    parser.add_argument("--kms_encyr_key_id", type=str, required=False)
    parser.add_argument("--region_id", type=str, default=None)
    parser.add_argument("--label", type=str, default=[], required=False, action='append')
    parser.add_argument("--image", type=str, default=[], required=False, action='append')

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
    pre_pull_images = args.image
    additional_labels = args.label

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
        api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
    except Exception as e:
        api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
    api.session.verify = False

    resource_name = VM_NAME_PREFIX + uuid.uuid4().hex[0:UUID_LENGHT]

    try:

        if not ins_img or ins_img == 'null':
            # Redefine default instance image if cloud metadata has specific rules for instance type
            allowed_instance = get_allowed_instance_image(cloud_region, ins_type, ins_img)
            if allowed_instance and allowed_instance["instance_mask"]:
                pipe_log('Found matching rule {instance_mask}/{ami} for requested instance type {instance_type}'
                         '\nImage {ami} will be used'.format(instance_mask=allowed_instance["instance_mask"],
                                                             ami=allowed_instance["instance_mask_ami"], instance_type=ins_type))
                ins_img = allowed_instance["instance_mask_ami"]
        else:
            pipe_log('Specified in configuration image {ami} will be used'.format(ami=ins_img))

        ins_id, ins_ip = verify_run_id(run_id)

        if not ins_id:
            api_url = os.environ["API"]
            api_token = os.environ["API_TOKEN"]
            ins_id, ins_ip = run_instance(api_url, api_token, resource_name, ins_type, cloud_region, run_id, ins_hdd, ins_img, ins_key_path,
                                          "pipeline", ins_type, is_spot, kube_ip, kubeadm_token, pre_pull_images)
        nodename = verify_regnode(ins_id, num_rep, time_rep, api)
        label_node(nodename, run_id, api, cluster_name, cluster_role, cloud_region, additional_labels)
        pipe_log('Node created:\n'
                 '- {}\n'
                 '- {}'.format(ins_id, ins_ip))

        # External process relies on this output
        print(ins_id + "\t" + ins_ip + "\t" + nodename)

        pipe_log('{} task finished'.format(NODEUP_TASK), status=TaskStatus.SUCCESS)
    except Exception as e:
        delete_all_by_run_id(run_id)
        delete_scale_set_nodes_from_kube(kube_api=api, scale_set_name=resource_name)
        pipe_log('[ERROR] ' + str(e), status=TaskStatus.FAILURE)
        raise e


if __name__ == '__main__':
    main()
