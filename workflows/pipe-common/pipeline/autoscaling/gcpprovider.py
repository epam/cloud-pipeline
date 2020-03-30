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

import os
import socket
import sys
import time
import uuid

from cloudprovider import AbstractInstanceProvider, LIMIT_EXCEEDED_ERROR_MASSAGE, LIMIT_EXCEEDED_EXIT_CODE
from random import randint
from time import sleep

from googleapiclient import discovery

from pipeline.autoscaling import utils

DISABLE_ACCESS = 'disable_external_access'

OS_DISK_SIZE = 10
INSTANCE_USER_NAME = "pipeline"
NO_BOOT_DEVICE_NAME = 'sdb1'
SWAP_DEVICE_NAME = 'sdb2'

# custom instance format
GPU_CUSTOM_INSTANCE_PARTS = 5
GPU_CUSTOM_INSTANCE_TYPE_INDEX = 3
GPU_CUSTOM_INSTANCE_COUNT_INDEX = 4
GPU_NVIDIA_PREFIX = 'nvidia-tesla-'
GPU_TYPE_PREFIX = 'gpu-'


class GCPInstanceProvider(AbstractInstanceProvider):

    def __init__(self, cloud_region):
        self.cloud_region = cloud_region
        self.project_id = os.environ["GOOGLE_PROJECT_ID"]
        self.client = discovery.build('compute', 'v1')

    def run_instance(self, is_spot, bid_price, ins_type, ins_hdd, ins_img, ins_key, run_id, kms_encyr_key_id,
                     num_rep, time_rep, kube_ip, kubeadm_token):
        ssh_pub_key = utils.read_ssh_key(ins_key)
        swap_size = utils.get_swap_size(self.cloud_region, ins_type, is_spot, "GCP")
        user_data_script = utils.get_user_data_script(self.cloud_region, ins_type, ins_img,
                                                      kube_ip, kubeadm_token, swap_size)

        instance_type, gpu_type, gpu_count = self.parse_instance_type(ins_type)
        machine_type = 'zones/{}/machineTypes/{}'.format(self.cloud_region, instance_type)
        instance_name = "gcp-" + uuid.uuid4().hex[0:16]

        network_interfaces = self.__build_networks()
        if is_spot:
            utils.pipe_log('Preemptible instance with run id: ' + run_id + ' will be launched')
        body = {
            'name': instance_name,
            'machineType': machine_type,
            'scheduling': {
                'onHostMaintenance': 'terminate',
                'preemptible': is_spot
            },
            'canIpForward': True,
            'disks': self.__get_disk_devices(ins_img, OS_DISK_SIZE, ins_hdd, swap_size),
            'networkInterfaces': network_interfaces,
            'labels': GCPInstanceProvider.get_tags(run_id, self.cloud_region),
            'tags': {
                'items': utils.get_network_tags(self.cloud_region)
            },
            "metadata": {
                "items": [
                    {
                        "key": "ssh-keys",
                        "value": "{user}:{key} {user}".format(key=ssh_pub_key, user=INSTANCE_USER_NAME)
                    },
                    {
                        "key": "startup-script",
                        "value": user_data_script
                    }
                ]
            }

        }

        if gpu_type is not None and gpu_count > 0:
            gpu = {"guestAccelerators": [ 
                {
                        "acceleratorCount": [gpu_count],
                        "acceleratorType": "https://www.googleapis.com/compute/v1/projects/{project}/zones/{zone}/acceleratorTypes/{gpu_type}"
                            .format(project=self.project_id,
                                    zone=self.cloud_region,
                                    gpu_type=gpu_type)
                    }
                ]}
            body.update(gpu)

        try:
            response = self.client.instances().insert(
                project=self.project_id,
                zone=self.cloud_region,
                body=body).execute()
            self.__wait_for_operation(response['name'])
        except Exception as client_error:
            if 'quota' in client_error.__str__().lower():
                utils.pipe_log_warn(LIMIT_EXCEEDED_ERROR_MASSAGE)
                sys.exit(LIMIT_EXCEEDED_EXIT_CODE)
            else:
                raise client_error

        ip_response = self.client.instances().get(
            project=self.project_id,
            zone=self.cloud_region,
            instance=instance_name
        ).execute()

        private_ip = ip_response['networkInterfaces'][0]['networkIP']
        return instance_name, private_ip
    
    def parse_instance_type(self, ins_type):
        # Custom type with GPU: gpu-custom-4-16000-k80-1
        # Custom type with CPU only: custom-4-16000
        # Predefined type: n1-standard-1
        if not ins_type.startswith(GPU_TYPE_PREFIX):
            return ins_type, None, 0
        parts = ins_type[len(GPU_TYPE_PREFIX):].split('-')
        if len(parts) != GPU_CUSTOM_INSTANCE_PARTS:
            raise RuntimeError('Custom instance type with GPU "%s" does not match expected pattern.' % ins_type)
        gpu_type = parts[GPU_CUSTOM_INSTANCE_TYPE_INDEX]
        gpu_count = parts[GPU_CUSTOM_INSTANCE_COUNT_INDEX]
        return '-'.join(parts[0:GPU_CUSTOM_INSTANCE_TYPE_INDEX]), GPU_NVIDIA_PREFIX + gpu_type, gpu_count

    def find_and_tag_instance(self, old_id, new_id):
        instance = self.__find_instance(old_id)
        if instance:
            labels = instance['labels']
            labels['name'] = new_id
            labels_body = {'labels': labels, 'labelFingerprint': instance['labelFingerprint']}
            reassign = self.client.instances().setLabels(
                project=self.project_id,
                zone=self.cloud_region,
                instance=instance['name'],
                body=labels_body).execute()
            self.__wait_for_operation(reassign['name'])
            return instance['name']
        else:
            raise RuntimeError('Instance with id: {} not found!'.format(old_id))

    def verify_run_id(self, run_id):
        utils.pipe_log('Checking if instance already exists for RunID {}'.format(run_id))
        instance = self.__find_instance(run_id)
        if instance and len(instance['networkInterfaces'][0]) > 0:
            ins_id = instance['name']
            ins_ip = instance['networkInterfaces'][0]['networkIP']
            utils.pipe_log('Found existing instance (ID: {}, IP: {}) for RunID {}\n-'.format(ins_id, ins_ip, run_id))
        else:
            ins_id = ''
            ins_ip = ''
            utils.pipe_log('No existing instance found for RunID {}\n-'.format(run_id))
        return ins_id, ins_ip

    def check_instance(self, ins_id, run_id, num_rep, time_rep):
        utils.pipe_log('Checking instance ({}) boot state'.format(ins_id))
        port = 8888
        response = self.__find_instance(run_id)
        ipaddr = response['networkInterfaces'][0]['networkIP']
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        utils.pipe_log('- Waiting for instance boot up...')
        result = utils.poll_instance(sock, time_rep, ipaddr, port)
        rep = 0
        while result != 0:
            sleep(time_rep)
            result = utils.poll_instance(sock, time_rep, ipaddr, port)
            rep = utils.increment_or_fail(num_rep, rep,
                                          'Exceeded retry count ({}) for instance ({}) network check on port {}'.format(
                                              num_rep, ins_id, port))
        utils.pipe_log('Instance is booted. ID: {}, IP: {}\n-'.format(ins_id, ipaddr))

    def get_instance_names(self, ins_id):
        instance = self.client.instances().get(
            project=self.project_id,
            zone=self.cloud_region,
            instance=ins_id).execute()

        if instance:
            # according to https://cloud.google.com/compute/docs/internal-dns#about_internal_dns
            return '{}.{}.c.{}.internal'.format(instance['name'], self.cloud_region, self.project_id), instance['name']
        return None, None

    def find_instance(self, run_id):
        instance = self.__find_instance(run_id)
        if instance:
            return instance['name']
        return None

    def terminate_instance(self, ins_id):
        delete = self.client.instances().delete(
            project=self.project_id,
            zone=self.cloud_region,
            instance=ins_id).execute()

        self.__wait_for_operation(delete['name'])

    def terminate_instance_by_ip_or_name(self, internal_ip, node_name):
        items = self.__filter_instances("")
        for instance in items:
            if instance['networkInterfaces'][0]['networkIP'] == internal_ip:
                self.terminate_instance(instance['name'])

    def __find_instance(self, run_id):
        items = self.__filter_instances('labels.name="{}"'.format(run_id))
        if items:
            filtered = [ins for ins in items if 'labels' in ins and ins['labels']['name'] == run_id]
            if filtered and len(filtered) == 1:
                return filtered[0]
        return None

    def __filter_instances(self, filter):
        result = self.client.instances().list(
            project=self.project_id,
            zone=self.cloud_region,
            filter=filter
        ).execute()
        if 'items' in result:
            return result['items']
        else:
            return None

    def __get_boot_device(self, disk_size, image_family):
        project_and_family = image_family.split("/")
        if len(project_and_family) != 2:
            # TODO: missing exception?
            print("node_image parameter doesn't match to Google image name convention: <project>/<imageFamily>")
        image = self.client.images().get(project=project_and_family[0], image=project_and_family[1]).execute()
        if image is None or 'diskSizeGb' not in image:
            utils.pipe_log('Failed to get image disk size info. Falling back to default size %d ' % disk_size)
            image_disk_size = disk_size
        else:
            image_disk_size = image['diskSizeGb']
        return {
            'boot': True,
            'autoDelete': True,
            'deviceName': 'sda1',
            'initializeParams': {
                'diskSizeGb': image_disk_size,
                'diskType': 'projects/{}/zones/{}/diskTypes/pd-ssd'.format(self.project_id, self.cloud_region),
                'sourceImage': 'projects/{}/global/images/{}'.format(project_and_family[0], project_and_family[1])
            },
            'mode': 'READ_WRITE',
            'type': 'PERSISTENT'
        }

    def __get_disk_devices(self, ins_img, os_disk_size, ins_hdd, swap_size):
        disks = [self.__get_boot_device(os_disk_size, ins_img),
                 self.__get_device(ins_hdd, NO_BOOT_DEVICE_NAME)]
        if swap_size is not None and swap_size > 0:
            disks.append(self.__get_device(swap_size, SWAP_DEVICE_NAME))
        return disks

    def __get_device(self, ins_hdd, device_name):
        return {
            'boot': False,
            'autoDelete': True,
            'deviceName': device_name,
            'mode': 'READ_WRITE',
            'type': 'PERSISTENT',
            'initializeParams': {
                'diskSizeGb': ins_hdd,
                'diskType': 'projects/{}/zones/{}/diskTypes/pd-ssd'.format(self.project_id, self.cloud_region)
            }
        }

    def __wait_for_operation(self, operation):
        while True:
            result = self.client.zoneOperations().get(
                project=self.project_id,
                zone=self.cloud_region,
                operation=operation).execute()

            if result['status'] == 'DONE':
                if 'error' in result:
                    raise Exception(result['error'])
                return result

            time.sleep(1)

    def __build_networks(self):
        region_name = self.cloud_region[:self.cloud_region.rfind('-')]
        allowed_networks = utils.get_networks_config(self.cloud_region)
        subnet_id = 'default'
        network_name = 'default'
        if allowed_networks and len(allowed_networks) > 0:
            network_num = randint(0, len(allowed_networks) - 1)
            network_name = allowed_networks.items()[network_num][0]
            subnet_id = allowed_networks.items()[network_num][1]
            utils.pipe_log(
                '- Networks list found, subnet {} in Network {} will be used'.format(subnet_id, network_name))
        else:
            utils.pipe_log('- Networks list NOT found, default subnet in random AZ will be used')

        access_config = utils.get_access_config(self.cloud_region)
        disable_external_access = False
        if access_config is not None:
            disable_external_access = DISABLE_ACCESS in access_config and access_config[DISABLE_ACCESS]

        network = {
            'network': 'projects/{project}/global/networks/{network}'.format(project=self.project_id,
                                                                             network=network_name),
            'subnetwork': 'projects/{project}/regions/{region}/subnetworks/{subnet}'.format(
                project=self.project_id, subnet=subnet_id, region=region_name)
        }
        if not disable_external_access:
            network['accessConfigs'] = [
                {
                    'name': 'External NAT',
                    'type': 'ONE_TO_ONE_NAT'
                }
            ]
        return [network]

    @staticmethod
    def resource_tags():
        tags = {}
        _, config_tags = utils.load_cloud_config()
        if config_tags is None:
            return tags
        for key, value in config_tags.iteritems():
            tags.update({key: value})
        return tags

    @staticmethod
    def run_id_tag(run_id):
        return {
            'name': run_id,
        }

    @staticmethod
    def get_tags(run_id, cloud_region):
        tags = GCPInstanceProvider.run_id_tag(run_id)
        GCPInstanceProvider.append_tags(tags, GCPInstanceProvider.resource_tags())
        GCPInstanceProvider.append_tags(tags, utils.get_region_tags(cloud_region))
        return tags

    @staticmethod
    def append_tags(tags, tags_to_add):
        if tags_to_add is None:
            return
        for key in tags_to_add:
            tags[key.lower()] = tags_to_add[key].lower()
