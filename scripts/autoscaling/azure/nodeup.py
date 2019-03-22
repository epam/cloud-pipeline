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
from pipeline import Logger, TaskStatus, PipelineAPI

#############################
### TODO
### This section corresponds to pipeline logging, so that we will get nodeup logs in GUI as a separate task
### But current implementation is almost a hack (e.g. relies on application.config location)
### It shall be completely rewritten once Python scripts are moved to Java
### How to make sure this will work:
### 1. application.config shall be located in ../config/application.config
### 2. application.config shall contain valid values for:
###     - api.host
###     - server.api.token
### 3. Latest "pipeline" package shall be installed using "pip install"

NETWORKS_PARAM = "cluster.networks.config"
NODEUP_TASK = "InitializeNode"

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


def get_proxies(cloud_region):
    return get_cloud_config_section(cloud_region, "proxies")


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

zone = None
resource_client = get_client_from_auth_file(ResourceManagementClient)
network_client = get_client_from_auth_file(NetworkManagementClient)
compute_client = get_client_from_auth_file(ComputeManagementClient)
resource_group_name = os.environ["AZURE_RESOURCE_GROUP"]


def run_instance(instance_type, cloud_region, run_id, ins_hdd, ins_img, ssh_pub_key, user, kms_encyr_key_id,
                 ins_type, kube_ip, kubeadm_token):
    try:
        ins_key = read_ssh_key(ssh_pub_key)
        user_data_script = get_user_data_script(cloud_region, ins_type, ins_img, kube_ip, kubeadm_token)
        instance_name = "az-" + uuid.uuid4().hex[0:16]
        create_public_ip_address(instance_name, run_id)
        create_nic(instance_name, run_id)
        return create_vm(instance_name, run_id, instance_type, ins_img, ins_hdd,
                         user_data_script, ins_key, user, kms_encyr_key_id)
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

    allowed_networks = get_networks_config(zone)
    res_group_network = None
    subnet_id = None
    security_groups = get_security_groups(zone)
    if allowed_networks and len(allowed_networks) > 0:
        az_num = randint(0, len(allowed_networks) - 1)
        az_name = allowed_networks.items()[az_num][0]
        subnet_id = allowed_networks.items()[az_num][1]
        res_group_network = az_name.split("/")
        pipe_log('- Networks list found, subnet {} in AZ {} will be used'.format(subnet_id, az_name))
    else:
        pipe_log('- Networks list NOT found, default subnet in random AZ will be used')

    if len(res_group_network) != 2:
        raise AssertionError("Please specify network as: <network_resource_group>/<network_name>")

    subnet_info = network_client.subnets.get(
        res_group_network[0],
        res_group_network[1],
        subnet_id
    )

    public_ip_address = network_client.public_ip_addresses.get(
        resource_group_name,
        instance_name + '-ip'
    )

    if len(security_groups) != 1:
        raise AssertionError("Please specify only one security group as: <resource_group>/<security_group_name>")

    res_group_and_security_group = security_groups[0].split("/")

    if len(res_group_and_security_group) != 2:
        raise AssertionError("Please specify security group as: <resource_group>/<security_group_name>")

    security_group_info = network_client.network_security_groups.get(
        res_group_and_security_group[0],
        res_group_and_security_group[1]
    )

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


def create_vm(instance_name, run_id, instance_type, instance_image, disk, user_data_script,
              ssh_pub_key, user, kms_encyr_key_id):
    nic = network_client.network_interfaces.get(
        resource_group_name,
        instance_name + '-nic'
    )
    image_param = instance_image.split("/")
    if len(image_param) != 2:
        print("node_image parameter doesn't match to Azure image name convention: <resource_group>/<image_name>")

    image = compute_client.images.get(
        image_param[0],
        image_param[1]
    )

    vm_parameters = {
        'location': zone,
        'os_profile': {
            'computer_name': instance_name,
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
        },
        'hardware_profile': {
            'vm_size': instance_type
        },
        'storage_profile': {
            'image_reference': {
               'id': image.id
            },
            "dataDisks": [
                {
                    "name": instance_name + "-data",
                    "diskSizeGB": disk,
                    "lun": 63,
                    "createOption": "Empty",
                    "managedDisk": {
                        "storageAccountType": get_disk_type(instance_type)
                    }
                }
            ]
        },
        'network_profile': {
            'network_interfaces': [{
                'id': nic.id
            }]
        },
        'tags': get_tags(run_id)
    }

    if kms_encyr_key_id:
        vault_id_key_url_secret = kms_encyr_key_id.split(";")
        vm_parameters["storage_profile"]["dataDisks"][0]["encryptionSettings"] = {
            "diskEncryptionKey": {
                "sourceVault": {
                    "id": vault_id_key_url_secret[0]
                },
                "secretUrl": vault_id_key_url_secret[2]
            },
            "enabled": True,
            "keyEncryptionKey": {
                "sourceVault": {
                    "id": vault_id_key_url_secret[0]
                },
                "keyUrl": vault_id_key_url_secret[1]
            }
        }

    creation_result = compute_client.virtual_machines.create_or_update(
        resource_group_name,
        instance_name,
        vm_parameters
    )
    creation_result.result()

    start_result = compute_client.virtual_machines.start(resource_group_name, instance_name)
    start_result.wait()

    public_ip = network_client.public_ip_addresses.get(
        resource_group_name,
        instance_name + '-ip'
    )

    private_ip = network_client.network_interfaces.get(
        resource_group_name, instance_name + '-nic').ip_configurations[0].private_ip_address

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


def increment_or_fail(num_rep, rep, error_message):
    rep = rep + 1
    if rep > num_rep:
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


def run_id_filter(run_id):
    return {
                'Name': 'tag:Name',
                'Values': [run_id]
           }


def verify_run_id(run_id):
    pipe_log('Checking if instance already exists for RunID {}'.format(run_id))
    vm_name = None
    private_ip = None
    for resource in resource_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + run_id + "'"):
        if str(resource.type).split('/')[-1] == "virtualMachines":
            vm_name = resource.name

    if vm_name is not None:
        private_ip = network_client.network_interfaces\
            .get(resource_group_name, vm_name + '-nic').ip_configurations[0].private_ip_address

    return vm_name, private_ip


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
    public_ip = network_client.public_ip_addresses.get(
        resource_group_name,
        ins_id + '-ip'
    )
    nodename_full = public_ip.dns_settings.fqdn
    nodename = nodename_full.split('.', 1)[0]
    pipe_log('Waiting for instance {} registration in cluster with name {}'.format(ins_id, nodename))

    ret_namenode = ''
    rep = 0
    while rep <= num_rep:
        ret_namenode = find_node(nodename, nodename_full, api)
        if ret_namenode:
            break
        rep = increment_or_fail(num_rep, rep,
                                'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) cluster registration'.format(num_rep, ins_id, nodename))
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
                                    'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) kube node READY check'.format(num_rep, ins_id, ret_namenode))
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
                                    'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) kube system pods check'.format(num_rep, ins_id, ret_namenode))
            sleep(time_rep)
        pipe_log('Instance {} successfully registred in cluster with name {}\n-'.format(ins_id, nodename))
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
    if str(r1.type).split('/')[-1] == "virtualMachines":
        return -1
    elif str(r1.type).split('/')[-1] == "networkInterfaces" and str(r2.type).split('/')[-1] != "virtualMachines":
        return -1
    return 0


def delete_all_by_run_id(run_id):
    resources = []
    for resource in resource_client.resources.list(filter="tagName eq 'Name' and tagValue eq '" + run_id + "'"):
        resources.append(resource)
        # we need to sort resources to be sure that vm and nic will be deleted first, because it has attached resorces(disks and ip)
        resources.sort(key=functools.cmp_to_key(azure_resource_type_cmp))
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


def replace_proxies(cloud_region, init_script):
    pipe_log('Setting proxy settings for an instance in {} region'.format(cloud_region))
    proxies_list = get_proxies(cloud_region)
    if not proxies_list:
        return init_script

    for proxy_item in proxies_list:
        if not 'name' in proxy_item or not 'path' in proxy_item:
            continue
        proxy_name = proxy_item['name']
        proxy_path = proxy_item['path']
        if not proxy_name:
            continue
        if proxy_path == None:
            proxy_path = ''
        init_script = init_script.replace('@' + proxy_name + '@', proxy_path)
        pipe_log('-> {}={}'.format(proxy_name, proxy_path))

    return init_script


def get_user_data_script(cloud_region, ins_type, ins_img, kube_ip, kubeadm_token):
    allowed_instance = get_allowed_instance_image(cloud_region, ins_type, ins_img)
    if allowed_instance and allowed_instance["init_script"]:
        init_script = open(allowed_instance["init_script"], 'r')
        user_data_script = init_script.read()
        certs_string = get_certs_string()
        well_known_string = get_well_known_hosts_string(cloud_region)
        init_script.close()
        user_data_script = replace_proxies(cloud_region, user_data_script)
        return user_data_script\
            .replace('@DOCKER_CERTS@', certs_string) \
            .replace('@WELL_KNOWN_HOSTS@', well_known_string) \
            .replace('@KUBE_IP@', kube_ip) \
            .replace('@KUBE_TOKEN@', kubeadm_token)
    else:
        raise RuntimeError('Unable to get init.sh path')


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

    args = parser.parse_args()
    ins_key_path = args.ins_key
    run_id = args.run_id
    ins_type = args.ins_type
    ins_hdd = args.ins_hdd
    ins_img = args.ins_img
    num_rep = args.num_rep
    time_rep = args.time_rep
    cluster_name = args.cluster_name
    cluster_role = args.cluster_role
    kube_ip = args.kube_ip
    kubeadm_token = args.kubeadm_token
    region_id = args.region_id
    kms_encyr_key_id = args.kms_encyr_key_id

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
                                          kms_encyr_key_id, ins_type, kube_ip, kubeadm_token)


        try:
            api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
        except Exception as e:
            api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
        api.session.verify = False

        nodename = verify_regnode(ins_id, num_rep, time_rep, api)
        label_node(nodename, run_id, api, cluster_name, cluster_role, cloud_region)
        pipe_log('Node created:\n'
                 '- {}\n'
                 '- {}'.format(ins_id, ins_ip))

        # External process relies on this output
        print(ins_id + "\t" + ins_ip + "\t" + nodename)

        pipe_log('{} task finished'.format(NODEUP_TASK), status=TaskStatus.SUCCESS)
    except Exception as e:
        pipe_log('[ERROR] ' + str(e), status=TaskStatus.FAILURE)
        raise e


if __name__ == '__main__':
    main()
