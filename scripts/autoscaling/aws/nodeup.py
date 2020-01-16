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
import base64
import socket
from datetime import datetime, timedelta
from time import sleep
import boto3
from botocore.config import Config
import pykube
import logging
import os
import pytz
from botocore.exceptions import ClientError
from pipeline import Logger, TaskStatus, PipelineAPI
from itertools import groupby
from operator import itemgetter
from random import randint
import json
from distutils.version import LooseVersion
import fnmatch
import sys
import math
import socket

SPOT_UNAVAILABLE_EXIT_CODE = 5
LIMIT_EXCEEDED_EXIT_CODE = 6

RUNNING = 16
PENDING = 0

NETWORKS_PARAM = "cluster.networks.config"
NODEUP_TASK = "InitializeNode"
LIMIT_EXCEEDED_ERROR_MASSAGE = 'Instance limit exceeded. A new one will be launched as soon as free space will be available.'
BOTO3_RETRY_COUNT = 6
MIN_SWAP_DEVICE_SIZE = 5

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


def get_region_settings(aws_region):
    full_settings, tags = load_cloud_config()
    for region_settings in full_settings:
        if 'name' in region_settings and region_settings['name'] == aws_region:
            return region_settings
    pipe_log('Failed to find networks settings for region: %s.' % aws_region)
    return None


def get_cloud_config_section(aws_region, section_name):
    cloud_metadata = get_region_settings(aws_region)
    if cloud_metadata and section_name in cloud_metadata and len(cloud_metadata[section_name]) > 0:
        return cloud_metadata[section_name]
    else:
        return None


def get_networks_config(aws_region):
    return get_cloud_config_section(aws_region, "networks")


def get_instance_images_config(aws_region):
    return get_cloud_config_section(aws_region, "amis")


def get_allowed_zones(aws_region):
    return list(get_networks_config(aws_region).keys())


def get_security_groups(aws_region):
    config = get_cloud_config_section(aws_region, "security_group_ids")
    if not config:
        raise RuntimeError('Security group setting is required to run an instance')
    return config

def get_well_known_hosts(aws_region):
    return get_cloud_config_section(aws_region, "well_known_hosts")

def get_allowed_instance_image(aws_region, instance_type, default_image):
    default_init_script = os.path.dirname(os.path.abspath(__file__)) + '/init.sh'
    default_object = { "instance_mask_ami": default_image, "instance_mask": None, "init_script": default_init_script }

    instance_images_config = get_instance_images_config(aws_region)
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
            return { "instance_mask_ami": instance_mask_ami, "instance_mask": instance_mask, "init_script":  init_script}

    return default_object

#############################

ROOT_DEVICE_DEFAULT = {
                        "DeviceName": "/dev/sda1",
                        "Ebs": {"VolumeSize": 40}
                      }

def root_device(ec2, ins_img):
    try:
        pipe_log('- Getting image {} block device mapping details'.format(ins_img))
        img_details = ec2.describe_images(ImageIds=[ins_img])
        pipe_log('- Block device mapping details received. Proceeding with validation'.format(ins_img))

        if len(img_details["Images"]) == 0:
            raise RuntimeError("No images found for {}".format(ins_img))
        img_obj = img_details["Images"][0]

        if "BlockDeviceMappings" not in img_obj or len(img_obj["BlockDeviceMappings"]) == 0:
            raise RuntimeError("No BlockDeviceMappings found for {}".format(ins_img))

        block_device_obj = img_obj["BlockDeviceMappings"][0]
        block_device_name = block_device_obj["DeviceName"]
        if "Ebs" not in block_device_obj:
            raise RuntimeError("No Ebs definition found for device {} in image {}".format(block_device_name, ins_img))

        device_spec = {
            "DeviceName": block_device_name,
            "Ebs": {"VolumeSize": block_device_obj["Ebs"]["VolumeSize"], "VolumeType": "gp2"}
        }
        return device_spec
    except Exception as e:
        pipe_log('Error while getting image {} root device, using default device: {}\n{}'.format(ins_img,
                                                                                                 ROOT_DEVICE_DEFAULT["DeviceName"],
                                                                                                 str(e)))
        return ROOT_DEVICE_DEFAULT

def block_device(ins_hdd, kms_encyr_key_id, name="/dev/sdb"):
    block_device_spec = {
        "DeviceName": name,
        "Ebs": {
            "VolumeSize": ins_hdd,
            "VolumeType": "gp2",
            "DeleteOnTermination": True,
            "Encrypted": True
        }
    }
    if kms_encyr_key_id:
        block_device_spec["Ebs"]["KmsKeyId"] = kms_encyr_key_id
    return block_device_spec


def resource_tags():
    tags = []
    config_regions, config_tags = load_cloud_config()
    if config_tags is None:
        return tags
    for key, value in config_tags.iteritems():
        tags.append({"Key": key, "Value": value})
    return tags


def run_id_tag(run_id):
    return [{
        'Value': run_id,
        'Key': 'Name'
    }]


def get_tags(run_id):
    tags = run_id_tag(run_id)
    res_tags = resource_tags()
    if res_tags:
        tags.extend(res_tags)
    return tags


def run_id_filter(run_id):
    return {
                'Name': 'tag:Name',
                'Values': [run_id]
           }


def run_instance(api_url, api_token, bid_price, ec2, aws_region, ins_hdd, kms_encyr_key_id, ins_img, ins_key, ins_type, is_spot, num_rep, run_id, time_rep, kube_ip, kubeadm_token):
    swap_size = get_swap_size(aws_region, ins_type, is_spot)
    user_data_script = get_user_data_script(api_url, api_token, aws_region, ins_type, ins_img, kube_ip, kubeadm_token, swap_size)
    if is_spot:
        ins_id, ins_ip = find_spot_instance(ec2, aws_region, bid_price, run_id, ins_img, ins_type, ins_key, ins_hdd, kms_encyr_key_id,
                                            user_data_script, num_rep, time_rep, swap_size)
    else:
        ins_id, ins_ip = run_on_demand_instance(ec2, aws_region, ins_img, ins_key, ins_type, ins_hdd, kms_encyr_key_id, run_id, user_data_script,
                                                num_rep, time_rep, swap_size)
    return ins_id, ins_ip


def run_on_demand_instance(ec2, aws_region, ins_img, ins_key, ins_type, ins_hdd,
                           kms_encyr_key_id, run_id, user_data_script, num_rep, time_rep,
                           swap_size):
    pipe_log('Creating on demand instance')
    allowed_networks = get_networks_config(aws_region)
    additional_args = {}
    subnet_id = None
    if allowed_networks and len(allowed_networks) > 0:
        az_num = randint(0, len(allowed_networks)-1)
        az_name = allowed_networks.items()[az_num][0]
        subnet_id = allowed_networks.items()[az_num][1]
        pipe_log('- Networks list found, subnet {} in AZ {} will be used'.format(subnet_id, az_name))
        additional_args = { 'SubnetId': subnet_id, 'SecurityGroupIds': get_security_groups(aws_region)}
    else:
        pipe_log('- Networks list NOT found, default subnet in random AZ will be used')
        additional_args = { 'SecurityGroupIds': get_security_groups(aws_region)}
    response = {}
    try:
        response = ec2.run_instances(
            ImageId=ins_img,
            MinCount=1,
            MaxCount=1,
            KeyName=ins_key,
            InstanceType=ins_type,
            UserData=user_data_script,
            BlockDeviceMappings=get_block_devices(ec2, ins_img, ins_hdd, kms_encyr_key_id, swap_size),
            TagSpecifications=[
                {
                    'ResourceType': 'instance',
                    "Tags": get_tags(run_id)
                }
            ],
            **additional_args
        )
    except ClientError as client_error:
        if 'InstanceLimitExceeded' in client_error.message:
            pipe_log_warn(LIMIT_EXCEEDED_ERROR_MASSAGE)
            sys.exit(LIMIT_EXCEEDED_EXIT_CODE)
        else:
            raise client_error

    ins_id = response['Instances'][0]['InstanceId']
    ins_ip = response['Instances'][0]['PrivateIpAddress']

    pipe_log('- Instance created. ID: {}, IP: {}'.format(ins_id, ins_ip))

    status_code = get_current_status(ec2, ins_id)

    rep = 0
    while status_code != RUNNING:
        pipe_log('- Waiting for status checks completion...')
        sleep(time_rep)
        status_code = get_current_status(ec2, ins_id)
        rep = increment_or_fail(num_rep,
                                rep,
                                'Exceeded retry count ({}) for instance ({}) status check'.format(num_rep, ins_id),
                                ec2_client=ec2,
                                kill_instance_id_on_fail=ins_id)
    pipe_log('Instance created. ID: {}, IP: {}\n-'.format(ins_id, ins_ip))

    ebs_tags = resource_tags()
    if ebs_tags:
        instance_description = ec2.describe_instances(InstanceIds=[ins_id])['Reservations'][0]['Instances'][0]
        volumes = instance_description['BlockDeviceMappings']
        for volume in volumes:
            ec2.create_tags(
                Resources=[volume['Ebs']['VolumeId']],
                Tags=ebs_tags)

    return ins_id, ins_ip


def get_block_devices(ec2, ins_img, ins_hdd, kms_encyr_key_id, swap_size):
    block_devices = [root_device(ec2, ins_img), block_device(ins_hdd, kms_encyr_key_id)]
    if swap_size is not None and swap_size > 0:
        block_devices.append(block_device(swap_size, kms_encyr_key_id, name="/dev/sdc"))
    return block_devices

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

def get_well_known_hosts_string(aws_region):
    pipe_log('Setting well-known hosts an instance in {} region'.format(aws_region))
    command_pattern = 'echo {well_known_ip} {well_known_host} >> /etc/hosts'
    well_known_list = get_well_known_hosts(aws_region)
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

def replace_common_params(aws_region, init_script, config_section):
    pipe_log('Configuring {} settings for an instance in {} region'.format(config_section, aws_region))
    common_list = get_cloud_config_section(aws_region, config_section)
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

def replace_proxies(aws_region, init_script):
    return replace_common_params(aws_region, init_script, "proxies")


def get_swap_size(aws_region, ins_type, is_spot):
    pipe_log('Configuring swap settings for an instance in {} region'.format(aws_region))
    swap_params = get_cloud_config_section(aws_region, "swap")
    if swap_params is None:
        return None
    swap_ratio = get_swap_ratio(swap_params)
    if swap_ratio is None:
        pipe_log("Swap ratio is not configured. Swap configuration will be skipped.")
        return None
    ram = get_instance_ram(aws_region, ins_type, is_spot)
    if ram is None:
        pipe_log("Failed to determine instance RAM. Swap configuration will be skipped.")
        return None
    swap_size = int(math.ceil(swap_ratio * ram))
    if swap_size >= MIN_SWAP_DEVICE_SIZE:
        pipe_log("Swap device will be configured with size %d." % swap_size)
        return swap_size
    return None


def replace_swap(swap_size, init_script):
    if swap_size is not None:
        return init_script.replace('@swap_size@', str(swap_size))
    return init_script


def get_instance_ram(aws_region, ins_type, is_spot):
    api = PipelineAPI(api_url, None)
    region_id = get_region_id(aws_region, api)
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


def get_region_id(aws_region, api):
    regions = api.get_regions()
    if regions is None:
        return None
    for region in regions:
        if region.provider == 'AWS' and region.region_id == aws_region:
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

def get_user_data_script(api_url, api_token, aws_region, ins_type, ins_img, kube_ip, kubeadm_token, swap_size):
    allowed_instance = get_allowed_instance_image(aws_region, ins_type, ins_img)
    if allowed_instance and allowed_instance["init_script"]:
        init_script = open(allowed_instance["init_script"], 'r')
        user_data_script = init_script.read()
        certs_string = get_certs_string()
        well_known_string = get_well_known_hosts_string(aws_region)
        init_script.close()
        user_data_script = replace_proxies(aws_region, user_data_script)
        user_data_script = replace_swap(swap_size, user_data_script)
        user_data_script = user_data_script.replace('@DOCKER_CERTS@', certs_string) \
                                            .replace('@WELL_KNOWN_HOSTS@', well_known_string) \
                                            .replace('@KUBE_IP@', kube_ip) \
                                            .replace('@KUBE_TOKEN@', kubeadm_token) \
                                            .replace('@API_URL@', api_url) \
                                            .replace('@API_TOKEN@', api_token)

        # If there is a fresh "pipeline" module installed - we'll use a gzipped/self-extracting script
        # to minimize the size of the user data
        # Otherwise - raw script will be used
        try:
            from pipeline import pack_script_contents
            return pack_script_contents(user_data_script)
        except:
            return user_data_script
    else:
        raise RuntimeError('Unable to get init.sh path')


def get_current_status(ec2, ins_id):
    response = ec2.describe_instance_status(InstanceIds=[ins_id])
    if len(response['InstanceStatuses']) > 0:
        return response['InstanceStatuses'][0]['InstanceState']['Code']
    else:
        return -1

def poll_instance(sock, timeout, ip, port):
    result = -1
    sock.settimeout(float(timeout))
    try:
        result = sock.connect_ex((ip, port))
    except Exception as e:
        pass
    sock.settimeout(None)
    return result

def check_instance(ec2, ins_id, run_id, num_rep, time_rep):
    pipe_log('Checking instance ({}) boot state'.format(ins_id))
    port=8888
    response = ec2.describe_instances(InstanceIds=[ins_id])
    ipaddr = response['Reservations'][0]['Instances'][0]['PrivateIpAddress']
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    pipe_log('- Waiting for instance boot up...')
    result = poll_instance(sock, time_rep, ipaddr, port)
    rep = 0
    active = False
    while result != 0 or not active:
        sleep(time_rep)
        active = instance_is_active(ec2, ins_id)
        result = poll_instance(sock, time_rep, ipaddr, port)
        rep = increment_or_fail(num_rep, rep, 'Exceeded retry count ({}) for instance ({}) network check on port {}'.format(num_rep, ins_id, port),
                                ec2_client=ec2,
                                kill_instance_id_on_fail=ins_id)
    pipe_log('Instance is booted. ID: {}, IP: {}\n-'.format(ins_id, ipaddr))


def label_node(nodename, run_id, api, cluster_name, cluster_role, aws_region):
    pipe_log('Assigning instance {} to RunID: {}'.format(nodename, run_id))
    obj = {
        "apiVersion": "v1",
        "kind": "Node",
        "metadata": {
            "name": nodename,
            "labels": {
                "runid": run_id,
                "cloud_region": aws_region
            }
        }
    }

    if cluster_name:
        obj["metadata"]["labels"]["cp-cluster-name"] = cluster_name
    if cluster_role:
        obj["metadata"]["labels"]["cp-cluster-role"] = cluster_role

    pykube.Node(api, obj).update()
    pipe_log('Instance {} is assigned to RunID: {}\n-'.format(nodename, run_id))


def instance_is_active(ec2, instance_id):
    status = get_current_status(ec2, instance_id)
    return status == RUNNING or status == PENDING


def verify_run_id(ec2, run_id):
    pipe_log('Checking if instance already exists for RunID {}'.format(run_id))
    response = ec2.describe_instances(
        Filters=[
            run_id_filter(run_id),
            {
                'Name': 'instance-state-name',
                'Values': ['pending', 'running']
            }
        ]
    )
    ins_id = ''
    ins_ip = ''
    if len(response['Reservations']) > 0 and  instance_is_active(ec2, response['Reservations'][0]['Instances'][0]['InstanceId']):
        ins_id = response['Reservations'][0]['Instances'][0]['InstanceId']
        ins_ip = response['Reservations'][0]['Instances'][0]['PrivateIpAddress']
        pipe_log('Found existing instance (ID: {}, IP: {}) for RunID {}\n-'.format(ins_id, ins_ip, run_id))
    else:
        pipe_log('No existing instance found for RunID {}\n-'.format(run_id))
    return ins_id, ins_ip


def verify_regnode(ec2, ins_id, num_rep, time_rep, run_id, api):
    response = ec2.describe_instances(InstanceIds=[ins_id])
    nodename_full = response['Reservations'][0]['Instances'][0]['PrivateDnsName']
    nodename = nodename_full.split('.', 1)[0]
    nodenames = [nodename, nodename_full, ins_id]
    pipe_log('Waiting for instance {} registration in cluster with name(s) {}'.format(ins_id, nodenames))

    ret_namenode = ''
    rep = 0
    while rep <= num_rep:
        ret_namenode = find_node(nodenames, api)
        if ret_namenode:
            break
        rep = increment_or_fail(num_rep, rep,
                                'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) cluster registration'.format(num_rep, ins_id, nodename),
                                kill_instance_id_on_fail=ins_id)
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
                                    'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) kube node READY check'.format(num_rep, ins_id, ret_namenode),
                                    kill_instance_id_on_fail=ins_id)
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
                                    'Exceeded retry count ({}) for instance (ID: {}, NodeName: {}) kube system pods check'.format(num_rep, ins_id, ret_namenode),
                                    kill_instance_id_on_fail=ins_id)
            sleep(time_rep)
        pipe_log('Instance {} successfully registred in cluster with name {}\n-'.format(ins_id, nodename))
    return ret_namenode


def terminate_instance(ec2_client, instance_id, spot_request_id=None):
    if not instance_id or len(instance_id) == 0:
        pipe_log('[ERROR] None or empty string specified when calling terminate_instance, nothing will be done')
        return

    try:
        if spot_request_id:
            pipe_log('Cancel Spot request ({})'.format(spot_request_id))
            ec2_client.cancel_spot_instance_requests(request_ids=[spot_request_id])

        response = ec2_client.terminate_instances(
            InstanceIds=[
                instance_id
            ]
        )
    except Exception as terminate_exception:
        pipe_log('[ERROR] Error during instance {} termination:\n{}'.format(instance_id, str(terminate_exception)))
        return

    if 'TerminatingInstances' not in response or len(response['TerminatingInstances']) == 0:
        pipe_log('[ERROR] Unable to parse response of the {} instance termination request. '
                'TerminatingInstances entry not found or it contains 0 elements')
        return

    termination_state=response['TerminatingInstances'][0]
    prev_state=termination_state['PreviousState']['Name']
    current_state=termination_state['CurrentState']['Name']

    pipe_log('Instance {} state is changed from {} to {}'.format(instance_id, prev_state, current_state))

def increment_or_fail(num_rep, rep, error_message, ec2_client=None, kill_instance_id_on_fail=None):
    rep = rep + 1
    if rep > num_rep:
        if kill_instance_id_on_fail:
            spot_request_id = None
            pipe_log('[ERROR] Operation timed out and an instance {} will be terminated\n'
                     'See more details below'.format(kill_instance_id_on_fail))
            instance = ec2_client.describe_instances(InstanceIds=[kill_instance_id_on_fail])['Reservations'][0]['Instances'][0]
            if 'SpotInstanceRequestId' in instance and instance['SpotInstanceRequestId']:
                spot_request_id = instance['SpotInstanceRequestId']
            terminate_instance(ec2_client, kill_instance_id_on_fail, spot_request_id)
        raise RuntimeError(error_message)
    return rep


def find_node(nodes, api):
    for nodename in nodes:
        ret_namenode = get_nodename(api, nodename)
        if ret_namenode:
            return ret_namenode
    return ''


def get_nodename(api, nodename):
    node = pykube.Node.objects(api).filter(field_selector={'metadata.name': nodename})
    if len(node.response['items']) > 0:
        return nodename
    else:
        return ''

def get_mean(values):
    n = 0
    sum = 0.0
    for v in values:
        sum += v
        n += 1
    return sum / n

def get_availability_zones(ec2):
    zones = ec2.describe_availability_zones()
    return [zone['ZoneName'] for zone in zones['AvailabilityZones']]

def get_spot_prices(ec2, aws_region, instance_type, hours=3):
    prices = []
    allowed_networks = get_networks_config(aws_region)
    if allowed_networks and len(allowed_networks) > 0:
        zones = list(allowed_networks.keys())
    else:
        zones = get_availability_zones(ec2)
    history = ec2.describe_spot_price_history(
        StartTime=datetime.today() - timedelta(hours=hours),
        EndTime=datetime.today(),
        InstanceTypes=[instance_type],
        ProductDescriptions=['Linux/UNIX'],
        Filters=[{'Name': 'availability-zone', 'Values': zones}],
    )
    history = history['SpotPriceHistory']
    grouper = itemgetter('AvailabilityZone')
    for zone, items in groupby(sorted(history, key=grouper), key=grouper):
        price = get_mean([float(i['SpotPrice']) for i in items])
        prices.append((zone, price))
    return sorted(prices, key=lambda t: t[1])

def exit_if_spot_unavailable(run_id, last_status):
    # will exit with code '5' if a spot request can't be fulfilled
    if last_status in ['capacity-not-available', 'capacity-oversubscribed', 'constraint-not-fulfillable']:
        pipe_log('[ERROR] Could not fulfill spot request for run {}, status: {}'.format(run_id, last_status),
                 status=TaskStatus.FAILURE)
        sys.exit(SPOT_UNAVAILABLE_EXIT_CODE)

def find_spot_instance(ec2, aws_region, bid_price, run_id, ins_img, ins_type, ins_key,
                       ins_hdd, kms_encyr_key_id, user_data_script, num_rep, time_rep,
                       swap_size):
    pipe_log('Creating spot request')

    pipe_log('- Checking spot prices for current region...')
    spot_prices = get_spot_prices(ec2, aws_region, ins_type)

    allowed_networks = get_networks_config(aws_region)
    cheapest_zone = ''
    if len(spot_prices) == 0:
        pipe_log('- Unable to get prices for a spot of type {}, cheapest zone can not be determined'.format(ins_type))
    else:
        cheapest_zone, lowest_price = spot_prices[0]
        pipe_log('- Prices for {} spots:\n'.format(ins_type) +
                '\n'.join('{0}: {1:.5f}'.format(zone, price) for zone, price in spot_prices) + '\n' +
                '{} zone will be used'.format(cheapest_zone))
    specifications = {
            'ImageId': ins_img,
            'InstanceType': ins_type,
            'KeyName': ins_key,
            'UserData': base64.b64encode(user_data_script.encode('utf-8')).decode('utf-8'),
            'BlockDeviceMappings': get_block_devices(ec2, ins_img, ins_hdd, kms_encyr_key_id, swap_size),
        }
    if allowed_networks and cheapest_zone in allowed_networks:
        subnet_id = allowed_networks[cheapest_zone]
        pipe_log('- Networks list found, subnet {} in AZ {} will be used'.format(subnet_id, cheapest_zone))
        specifications['SubnetId'] = subnet_id
        specifications['SecurityGroupIds'] = get_security_groups(aws_region)
        specifications['Placement'] = { 'AvailabilityZone': cheapest_zone }
    else:
        pipe_log('- Networks list NOT found or cheapest zone can not be determined, default subnet in a random AZ will be used')
        specifications['SecurityGroupIds'] = get_security_groups(aws_region)

    current_time = datetime.now(pytz.utc) + timedelta(seconds=10)

    response = None
    try:
        response = ec2.request_spot_instances(
            SpotPrice=str(bid_price),
            InstanceCount=1,
            Type='one-time',
            ValidFrom=current_time,
            ValidUntil=current_time + timedelta(seconds=num_rep * time_rep),
            LaunchSpecification=specifications,
        )
    except ClientError as client_error:
        if 'Max spot instance count exceeded' in client_error.message or \
                'InstanceLimitExceeded' in client_error.message:
            pipe_log_warn(LIMIT_EXCEEDED_ERROR_MASSAGE)
            sys.exit(LIMIT_EXCEEDED_EXIT_CODE)
        else:
            raise client_error

    rep = 0
    ins_id = ''
    ins_ip = ''

    request_id = response['SpotInstanceRequests'][0]['SpotInstanceRequestId']

    if not request_id:
        raise RuntimeError('Spot instance request did not return a SpotInstanceRequestId')

    pipe_log('- Spot request was sent. SpotInstanceRequestId: {}. Waiting for spot request registration...'.format(request_id))

    # Await for spot request registration (sometimes SpotInstanceRequestId is not returned immediately)
    while rep <= num_rep:
        try:
            requests_list = ec2.describe_spot_instance_requests(SpotInstanceRequestIds=[request_id])
            if len(requests_list['SpotInstanceRequests']) > 0:
                break

        except Exception as e:
            if e.response['Error']['Code'] != "InvalidSpotInstanceRequestID.NotFound":
                raise e

        rep = increment_or_fail(num_rep,
                                rep,
                                'Exceeded retry count ({}) while waiting for spot request {}'.format(num_rep, request_id))

        pipe_log('- Spot request {} is not yet available. Still waiting...'.format(request_id))
        sleep(time_rep)
    #

    pipe_log('- Spot request {} is registered'.format(request_id))
    ec2.create_tags(
        Resources=[request_id],
        Tags=run_id_tag(run_id),
    )

    pipe_log('- Spot request {} was tagged with RunID {}. Waiting for request fulfillment...'.format(request_id, run_id))

    last_status = ''
    while rep <= num_rep:
        current_request = ec2.describe_spot_instance_requests(SpotInstanceRequestIds=[request_id])['SpotInstanceRequests'][0]
        status = current_request['Status']['Code']
        last_status = status
        if status == 'fulfilled':
            ins_id = current_request['InstanceId']
            instance = None
            try:
                instance = ec2.describe_instances(InstanceIds=[ins_id])
            except Exception as describe_ex:
                if describe_ex.response['Error']['Code'] == "InvalidInstanceID.NotFound":
                    pipe_log('- Spot request {} is already fulfilled but instance id {} can not be found yet. Still waiting...'.format(request_id, ins_id))
                    rep = increment_or_fail(num_rep, rep,
                                'Exceeded retry count ({}) for spot instance. Spot instance request status code: {}.'
                                .format(num_rep, status))
                    sleep(time_rep)
                    continue
                else:
                    raise describe_ex
            instance_reservation = instance['Reservations'][0]['Instances'][0] if instance else None
            if not instance_reservation or 'PrivateIpAddress' not in instance_reservation or not instance_reservation['PrivateIpAddress']:
                pipe_log('- Spot request {} is already fulfilled but PrivateIpAddress is not yet assigned. Still waiting...'.format(request_id))
                rep = increment_or_fail(num_rep, rep,
                                'Exceeded retry count ({}) for spot instance. Spot instance request status code: {}.'
                                .format(num_rep, status),
                                ec2_client=ec2,
                                kill_instance_id_on_fail=ins_id)
                sleep(time_rep)
                continue
            ins_ip = instance_reservation['PrivateIpAddress']
            ec2.create_tags(
                Resources=[ins_id],
                Tags=get_tags(run_id),
            )

            ebs_tags = resource_tags()
            if ebs_tags:
                volumes = instance_reservation['BlockDeviceMappings']
                for volume in volumes:
                    ec2.create_tags(
                        Resources=[volume['Ebs']['VolumeId']],
                        Tags=ebs_tags)

            pipe_log('Instance is successfully created for spot request {}. ID: {}, IP: {}\n-'.format(request_id, ins_id, ins_ip))
            break
        pipe_log('- Spot request {} is not yet fulfilled. Still waiting...'.format(request_id))
        # TODO: review all this logic, it is difficult to read and maintain
        if rep >= num_rep:
            exit_if_spot_unavailable(run_id, last_status)
        rep = increment_or_fail(num_rep, rep,
                                'Exceeded retry count ({}) for spot instance. Spot instance request status code: {}.'
                                 .format(num_rep, status))
        sleep(time_rep)

    exit_if_spot_unavailable(run_id, last_status)

    return ins_id, ins_ip

def tag_name_is_present(instance):
    return 'Tags' in instance and len([tag for tag in instance['Tags'] if tag['Key'] == 'Name']) > 0


def wait_for_fulfilment(status):
    return status == 'not-scheduled-yet' or status == 'pending-evaluation' \
           or status == 'pending-fulfillment' or status == 'fulfilled'


def check_spot_request_exists(ec2, num_rep, run_id, time_rep):
    pipe_log('Checking if spot request for RunID {} already exists...'.format(run_id))
    for interation in range(0, 5):
        spot_req = get_spot_req_by_run_id(ec2, run_id)
        if spot_req:
            request_id = spot_req['SpotInstanceRequestId']
            status = spot_req['Status']['Code']
            pipe_log('- Spot request for RunID {} already exists: SpotInstanceRequestId: {}, Status: {}'.format(run_id, request_id, status))
            rep = 0
            if status == 'request-canceled-and-instance-running' and instance_is_active(ec2, spot_req['InstanceId']):
                return tag_and_get_instance(ec2, spot_req, run_id)
            if wait_for_fulfilment(status):
                while status != 'fulfilled':
                    pipe_log('- Spot request ({}) is not yet fulfilled. Waiting...'.format(request_id))
                    sleep(time_rep)
                    spot_req = ec2.describe_spot_instance_requests(
                        SpotInstanceRequestIds=[request_id])['SpotInstanceRequests'][0]
                    status = spot_req['Status']['Code']
                    pipe_log('Exceeded retry count ({}) for spot instance (SpotInstanceRequestId: {}). Spot instance request status code: {}.'
                                .format(num_rep, request_id, status))
                    rep = rep + 1
                    if rep > num_rep:
                        exit_if_spot_unavailable(run_id, status)
                        return '', ''
                if  instance_is_active(ec2, spot_req['InstanceId']):
                    return tag_and_get_instance(ec2, spot_req, run_id)
        sleep(5)
    pipe_log('No spot request for RunID {} found\n-'.format(run_id))
    return '', ''


def get_spot_req_by_run_id(ec2, run_id):
    response = ec2.describe_spot_instance_requests(Filters=[run_id_filter(run_id)])
    for spot_req in response['SpotInstanceRequests']:
        status = spot_req['Status']['Code']
        if wait_for_fulfilment(status) or status == 'request-canceled-and-instance-running':
            return spot_req
    return None


def tag_and_get_instance(ec2, spot_req, run_id):
    ins_id = spot_req['InstanceId']
    pipe_log('Setting \"Name={}\" tag for instance {}'.format(run_id, ins_id))
    instance = ec2.describe_instances(InstanceIds=[ins_id])
    ins_ip = instance['Reservations'][0]['Instances'][0]['PrivateIpAddress']
    if not tag_name_is_present(instance):  # create tag name if not presents
        ec2.create_tags(
            Resources=[ins_id],
            Tags=get_tags(run_id),
        )
        pipe_log('Tag ({}) created for instance ({})\n-'.format(run_id, ins_id))
    else:
        pipe_log('Tag ({}) is already set for instance ({}). Skip tagging\n-'.format(run_id, ins_id))
    return ins_id, ins_ip


def get_aws_region(region_id):
    if region_id is not None:
        return region_id
    regions, tags = load_cloud_config()
    for region in regions:
        if 'default' in region and region['default']:
            return region['name']
    pipe_log('Failed to determine region for EC2 instance')
    raise RuntimeError('Failed to determine region for EC2 instance')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--ins_key", type=str, required=True)
    parser.add_argument("--run_id", type=str, required=True)
    parser.add_argument("--cluster_name", type=str, required=False)
    parser.add_argument("--cluster_role", type=str, required=False)
    parser.add_argument("--ins_type", type=str, default='m4.large')
    parser.add_argument("--ins_hdd", type=int, default=30)
    parser.add_argument("--ins_img", type=str, default='ami-f68f3899')
    parser.add_argument("--num_rep", type=int, default=250) # 250 x 3s = 12.5m
    parser.add_argument("--time_rep", type=int, default=3)
    parser.add_argument("--is_spot", type=bool, default=False)
    parser.add_argument("--bid_price", type=float, default=1.0)
    parser.add_argument("--kube_ip", type=str, required=True)
    parser.add_argument("--kubeadm_token", type=str, required=True)
    parser.add_argument("--kms_encyr_key_id", type=str, required=False)
    parser.add_argument("--region_id", type=str, default=None)

    args, unknown = parser.parse_known_args()
    ins_key = args.ins_key
    run_id = args.run_id
    ins_type = args.ins_type
    ins_hdd = args.ins_hdd
    ins_img = args.ins_img
    num_rep = args.num_rep
    time_rep = args.time_rep
    is_spot = args.is_spot
    bid_price = args.bid_price
    cluster_name = args.cluster_name
    cluster_role = args.cluster_role
    kube_ip = args.kube_ip
    kubeadm_token = args.kubeadm_token
    kms_encyr_key_id = args.kms_encyr_key_id
    region_id = args.region_id


    if not kube_ip or not kubeadm_token:
        raise RuntimeError('Kubernetes configuration is required to create a new node')

    pipe_log_init(run_id)

    aws_region = get_aws_region(region_id)
    boto3.setup_default_session(region_name=aws_region)
    pipe_log('Started initialization of new calculation node in AWS region {}:\n'
             '- RunID: {}\n'
             '- Type: {}\n'
             '- Disk: {}\n'
             '- Image: {}\n'
             '- IsSpot: {}\n'
             '- BidPrice: {}\n-'.format(aws_region,
                                        run_id,
                                        ins_type,
                                        ins_hdd,
                                        ins_img,
                                        str(is_spot),
                                        str(bid_price)))

    try:
        # Hacking max max_attempts to get rid of
        # "An error occurred (RequestLimitExceeded) when calling the ... operation (reached max retries: 4)"
        # Official solution shall be provided with https://github.com/boto/botocore/pull/1260, waiting for release
        # This is applied to the old versions of botocore
        boto3_version = LooseVersion(boto3.__version__)
        boto3_version_retries = LooseVersion("1.7")
        pipe_log('Using boto3 version {}'.format(boto3.__version__))

        ec2 = None
        if boto3_version < boto3_version_retries:
            try:
                ec2 = boto3.client('ec2')
                if hasattr(ec2.meta.events, "_unique_id_handlers"):
                    ec2.meta.events._unique_id_handlers['retry-config-ec2']['handler']._checker.__dict__['_max_attempts'] = BOTO3_RETRY_COUNT
            except Exception as inner_exception:
                pipe_log('Unable to modify retry config:\n{}'.format(str(inner_exception)))
        else:
            ec2 = boto3.client('ec2', config=Config(retries={'max_attempts': BOTO3_RETRY_COUNT}))



        # Redefine default instance image if cloud metadata has specific rules for instance type
        allowed_instance = get_allowed_instance_image(aws_region, ins_type, ins_img)
        if allowed_instance and allowed_instance["instance_mask"]:
            pipe_log('Found matching rule {instance_mask}/{ami} for requested instance type {instance_type}\nImage {ami} will be used'.format(instance_mask=allowed_instance["instance_mask"],
                                                                                                                                              ami=allowed_instance["instance_mask_ami"],
                                                                                                                                              instance_type=ins_type))
            ins_img = allowed_instance["instance_mask_ami"]

        ins_id, ins_ip = verify_run_id(ec2, run_id)
        if not ins_id:
            ins_id, ins_ip = check_spot_request_exists(ec2, num_rep, run_id, time_rep)

        if not ins_id:
            api_url = os.environ["API"]
            api_token = os.environ["API_TOKEN"]
            ins_id, ins_ip = run_instance(api_url, api_token, bid_price, ec2, aws_region, ins_hdd, kms_encyr_key_id, ins_img, ins_key, ins_type, is_spot,
                                        num_rep, run_id, time_rep, kube_ip, kubeadm_token)

        check_instance(ec2, ins_id, run_id, num_rep, time_rep)

        api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
        api.session.verify = False

        nodename = verify_regnode(ec2, ins_id, num_rep, time_rep, run_id, api)
        label_node(nodename, run_id, api, cluster_name, cluster_role, aws_region)
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
