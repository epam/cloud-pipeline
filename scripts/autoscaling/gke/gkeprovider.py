# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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
import subprocess
import time
import uuid
from google.api_core.extended_operation import ExtendedOperation
import pipeline.autoscaling.utils as utils
from google.cloud import container_v1
from google.cloud import compute_v1

GCP_API_CALL_TIMEOUT=5
NO_BOOT_DEVICE_NAME = 'sdb1'
SWAP_DEVICE_NAME = 'sdb2'
INSTANCE_USER_NAME = "pipeline"

class GKEInstanceProvider(object):

    def __init__(self, cloud_region):
        self.cloud_region = cloud_region
        self.project_id = os.environ["GOOGLE_PROJECT_ID"]
        self.cluster_name = os.environ["CP_KUBE_CLUSTER_NAME"]
        self.container_client = container_v1.ClusterManagerClient()
        self.ins_group_client = compute_v1.InstanceGroupsClient()
        self.ins_group_manager_client = compute_v1.InstanceGroupManagersClient()
        self.compute_client = compute_v1.InstancesClient()
        self.disk_client = compute_v1.DisksClient()

    def run_instance(self, is_spot, bid_price, ins_type, ins_hdd, ins_img, ins_platform, ins_key, run_id,
                    pool_id, kms_encyr_key_id,
                    num_rep, time_rep, kube_ip, kubeadm_token, kubeadm_cert_hash, kube_node_token,
                    global_distribution_url, pre_pull_images, is_dedicated, docker_data_root, docker_storage_driver,
                    skip_system_images_load):
        node_pool = self.__get_or_create_node_pool(is_spot, ins_type, ins_hdd)
        instance_name, ip = self.__run_instance_in_node_pool(node_pool, num_rep, time_rep)
        utils.pipe_log(f'Started instance {instance_name} with ip {ip}')
        instance = self.__get_instance(instance_name)
        self.__update_instance_metadata(instance, ins_key)
        self.__label_instance(instance, run_id, pool_id)
        self.__set_network_tags(instance)
        self.__attach_disks(instance, ins_hdd, ins_type, run_id, is_spot)
        initialized = self.__run_init_command(instance, ip, run_id, ins_type, ins_hdd, ins_platform,
            is_spot, global_distribution_url, num_rep, time_rep)
        if not initialized:
            utils.pipe_log(f'Failed to initialize instance {instance_name}')
            raise RuntimeError(f'Failed to initialize instance {instance_name}')
        return instance_name, ip

    def find_and_tag_instance(self, old_id, new_id):
        pass

    def verify_run_id(self, run_id):
        utils.pipe_log('Checking if instance already exists for RunID {}'.format(run_id))
        instance = self.__find_instance(run_id)
        if instance and len(instance.network_interfaces)  > 0:
            ins_id = instance.name
            ins_ip = instance.network_interfaces[0].network_i_p
            utils.pipe_log('Found existing instance (ID: {}, IP: {}) for RunID {}\n-'.format(ins_id, ins_ip, run_id))
        else:
            ins_id = ''
            ins_ip = ''
            utils.pipe_log('No existing instance found for RunID {}\n-'.format(run_id))
        return ins_id, ins_ip

    def __find_instance(self, run_id):
        items = self.__filter_instances('labels.name="{}"'.format(run_id))
        if items:
            filtered = [ins for ins in items if ins.labels and ins.labels.get('name') == str(run_id)]
            if filtered and len(filtered) == 1:
                return filtered[0]
        return None

    def check_instance(self, ins_id, run_id, num_rep, time_rep):
        instance = self.__find_instance(run_id)
        if instance is not None:
            utils.pipe_log('Instance is booted. ID: {}\n-'.format(ins_id))
            return
        self.__wait_instance_running(ins_id, num_rep, time_rep)

    def get_instance_names(self, ins_id):
        instance = self.compute_client.get(
                            project=self.project_id,
                            zone=self.cloud_region,
                            instance=ins_id)

        if instance:
            # according to https://cloud.google.com/compute/docs/internal-dns#about_internal_dns
            return '{}.{}.c.{}.internal'.format(instance.name, self.cloud_region, self.project_id), instance.name
        return None, None


    def find_instance(self, run_id):
        instance = self.__find_instance(run_id)
        if instance:
            return instance.name
        return None

    def terminate_instance(self, ins_id):
        pass

    def terminate_instance_by_ip(self, node_internal_ip, node_name):
        pass

    def find_nodes_with_run_id(self, run_id):
        instance = self.find_instance(run_id)
        return [instance] if instance is not None else []

    def __filter_instances(self, filter):
        request = compute_v1.ListInstancesRequest(
                                project=self.project_id,
                                zone=self.cloud_region,
                                filter=filter
                            )
        result = self.compute_client.list(request)
        instances = []
        for instance in result:
            instances.append(instance)
        return instances

    def __get_or_create_node_pool(self, is_spot, ins_type, disk_size):
        pool_name = f"cp-worker-node-pool-{ins_type}{'-spot' if is_spot else ''}"
        cluster_link = f"projects/{self.project_id}/locations/{self.cloud_region}/clusters/{self.cluster_name}"
        request = container_v1.ListNodePoolsRequest(parent=cluster_link)
        response = self.container_client.list_node_pools(request=request)
        for node_pool in response.node_pools:
            if node_pool.name == pool_name:
                utils.pipe_log(f"Found existing node pool: {node_pool.name}")
                return node_pool
        utils.pipe_log(f"Failed to find node pool with name '{pool_name}'. Creating a new pool.")
        pool_config = container_v1.types.NodeConfig(machine_type=ins_type, disk_size_gb=disk_size,
            preemptible=is_spot, disk_type='pd-ssd', local_ssd_count=1)
        node_pool = container_v1.types.NodePool(name=pool_name, config=pool_config, initial_node_count=0)
        request = container_v1.CreateNodePoolRequest(node_pool=node_pool, parent=cluster_link)
        operation = self.container_client.create_node_pool(request)
        self.__wait_for_extended_operation(operation, "pool creation")
        while response.status != container_v1.types.Operation.Status.DONE:
            response = self.container_client.get_operation(request=container_v1.GetOperationRequest(
                name=f"projects/{self.project_id}/locations/{self.cloud_region}/operations/{response.name}")
            )
            time.sleep(GCP_API_CALL_TIMEOUT)
            utils.pipe_log('Current status: ' + response.status.name)
        utils.pipe_log(f'Node pool {pool_name} created')
        return self.container_client.get_node_pool(container_v1.GetNodePoolRequest(
            name=f"projects/{self.project_id}/locations/{self.cloud_region}/clusters/{self.cluster_name}/nodePools/{pool_name}")
        )

    def __run_instance_in_node_pool(self, node_pool, num_rep, rep):
        instance_group_manager_url = self.__get_instance_group_manager_link(node_pool)
        utils.pipe_log('Starting instance in group: ' + instance_group_manager_url)
        instance_name = "gcp-" + uuid.uuid4().hex[0:16]

        request = compute_v1.CreateInstancesInstanceGroupManagerRequest(
            instance_group_manager=instance_group_manager_url,
            project=self.project_id,
            zone=self.cloud_region,
            instance_group_managers_create_instances_request_resource=
                compute_v1.types.InstanceGroupManagersCreateInstancesRequest(
                    instances=[compute_v1.PerInstanceConfig(name=instance_name)])
        )
        operation = self.ins_group_manager_client.create_instances(request)
        self.__wait_for_extended_operation(operation, "instance creation")

        request = compute_v1.ListManagedInstancesInstanceGroupManagersRequest(
            instance_group_manager=instance_group_manager_url,
            project=self.project_id,
            zone=self.cloud_region,
        )
        response = self.ins_group_manager_client.list_managed_instances(request)
        for instance in response:
            if instance.name == instance_name:
                utils.pipe_log(f'Created instance {instance_name}')
                return self.__wait_instance_running(instance_name, num_rep, rep)
        raise RuntimeError(f'Failed to create instance {instance_name}')

    def __wait_instance_running(self, instance_name, num_rep, time_rep):
        status = None
        rep = 0
        while status != 'RUNNING':
            time.sleep(time_rep)
            response = self.__get_instance(instance_name)
            status = response.status
            rep = utils.increment_or_fail(num_rep, rep,
            f'Exceeded retry count ({num_rep}) for instance ({instance_name}) network check ')
        response = self.__get_instance(instance_name)
        utils.pipe_log(f'Instance is booted. ID: {instance_name}, IP: {response.network_interfaces[0].network_i_p}\n-')
        return instance_name, response.network_interfaces[0].network_i_p

    def __get_instance_group_manager_link(self, node_pool):
        return node_pool.instance_group_urls[0].split('/instanceGroupManagers/')[1]

    def __label_instance(self, instance, run_id, pool_id):
        labels = instance.labels
        labels.update(GKEInstanceProvider.get_tags(run_id, pool_id, self.cloud_region))
        request = compute_v1.SetLabelsInstanceRequest(
            instance=instance.name,
            project=self.project_id,
            zone=self.cloud_region,
            instances_set_labels_request_resource=
                compute_v1.types.InstancesSetLabelsRequest(
                    labels=labels,
                    label_fingerprint=instance.label_fingerprint
                )
            )
        response = self.compute_client.set_labels(request=request)
        self.__wait_for_extended_operation(response)

    def __get_instance(self, instance_name):
        request = compute_v1.GetInstanceRequest(
            instance=instance_name,
            project=self.project_id,
            zone=self.cloud_region,
        )
        return self.compute_client.get(request=request)

    def __set_network_tags(self, instance):
        tags = instance.tags.items
        network_tags = utils.get_network_tags(self.cloud_region)
        if network_tags:
            tags.extend(network_tags)
            request = compute_v1.SetTagsInstanceRequest(
                instance=instance.name,
                project=self.project_id,
                zone=self.cloud_region,
                tags_resource=compute_v1.types.Tags(
                    fingerprint=instance.tags.fingerprint,
                    items=tags
                )
            )
            response = self.compute_client.set_tags(request=request)
            self.__wait_for_extended_operation(response)

    def __attach_disks(self, instance, ins_hdd, ins_type, run_id, is_spot):
        self.__attach_disk(instance, ins_hdd, run_id, 'disk-' + instance.name, NO_BOOT_DEVICE_NAME)
        swap_size = utils.get_swap_size(self.cloud_region, ins_type, is_spot, "GCP")
        # swap_size = 0
        if swap_size is not None and swap_size > 0:
            self.__attach_disk(instance, swap_size, run_id, 'swap-' + instance.name, SWAP_DEVICE_NAME)

    def __attach_disk(self, instance, ins_hdd, run_id, disk_name, device_name):
        request = compute_v1.InsertDiskRequest(
            project=self.project_id,
            zone=self.cloud_region,
            disk_resource=compute_v1.types.Disk(
                name=disk_name,
                access_mode='READ_WRITE',
                type_=f'projects/{self.project_id}/zones/{self.cloud_region}/diskTypes/pd-ssd',
                size_gb=ins_hdd,
                labels={'name': str(run_id)}
            )
        )
        response = self.disk_client.insert(request=request)
        self.__wait_for_extended_operation(response)
        utils.pipe_log(f'Created disk {disk_name}')

        request = compute_v1.AttachDiskInstanceRequest(
            instance=instance.name,
            project=self.project_id,
            zone=self.cloud_region,
            attached_disk_resource=compute_v1.types.AttachedDisk(
                boot=False,
                auto_delete=True,
                device_name=device_name,
                mode='READ_WRITE',
                type='PERSISTENT',
                source=f'projects/{self.project_id}/zones/{self.cloud_region}/disks/{disk_name}'
            )
        )
        response = self.compute_client.attach_disk(request=request)
        self.__wait_for_extended_operation(response)
        utils.pipe_log(f'Successfully attached disk {disk_name} to instance {instance.name}')


    def __get_device(self, ins_hdd, device_name):
        return {
            'boot': False,
            'autoDelete': True,
            'deviceName': device_name,
            'mode': 'READ_WRITE_SINGLE',
            'type': 'PERSISTENT',
            'initializeParams': {
                'diskSizeGb': ins_hdd,
                'diskType': f'projects/{self.project_id}/zones/{self.cloud_region}/diskTypes/pd-ssd'
            }
        }

    def __update_instance_metadata(self, instance, ins_key):
        ssh_pub_key = utils.read_ssh_key(ins_key)
        metadata = instance.metadata.items
        metadata.append(compute_v1.types.Items(
            {
                "key": "ssh-keys",
                "value":  f"{INSTANCE_USER_NAME}:{ssh_pub_key} {INSTANCE_USER_NAME}"
            }
        ))
        metadata.append(compute_v1.types.Items(
            {
                "key": "enable-oslogin",
                "value": "FALSE"
            }
        ))
        request = compute_v1.SetMetadataInstanceRequest(
            instance=instance.name,
            project=self.project_id,
            zone=self.cloud_region,
            metadata_resource={
                "fingerprint": instance.metadata.fingerprint,
                "items": metadata
            }
        )

        response = self.compute_client.set_metadata(request=request)
        self.__wait_for_extended_operation(response)
        utils.pipe_log(f'Successfully updated SSH key for instance {instance.name}')

    def __run_init_command(self, instance, instance_ip, run_id, ins_type, ins_img, ins_platform,
        is_spot, global_distribution_url, num_rep, time_rep):
        ssh_path = utils.get_autoscale_preference('commit.deploy.key')
        user_data = self.__create_userdata_file(run_id, ins_type, ins_img, ins_platform, is_spot, global_distribution_url)
        ssh_options = self.__get_ssh_options()
        try:
            self.__wait_ssh_is_available(ssh_path, INSTANCE_USER_NAME, instance_ip, num_rep, time_rep, ssh_options)
            copy_command = f'scp -i {ssh_path} {ssh_options} "{user_data}" {INSTANCE_USER_NAME}@{instance_ip}:/tmp/'
            ret_code, out, err = self.__run_command(copy_command)
            if ret_code != 0:
                self.__log_and_raise(f'Failed to upload userdata to instance {instance.name}. ' +
                    self.__collect_output(out, err))
            run_command = f'ssh -i {ssh_path} {ssh_options} {INSTANCE_USER_NAME}@{instance_ip} sudo bash /tmp/{user_data}'
            ret_code, out, err = self.__run_command(run_command)
            if ret_code != 0:
                self.__log_and_raise(f'Failed to execute userdata on instance {instance.name}. ' +
                    self.__collect_output(out, err))
        except Exception as e:
            utils.pipe_log_warn(str(e))
            return False
        finally:
            os.remove(user_data)
        return True

    def __get_ssh_options(self):
        return "-o StrictHostKeyChecking=no -o GlobalKnownHostsFile=/dev/null -o UserKnownHostsFile=/dev/null"

    def __wait_ssh_is_available(self, ssh_key, user, ip, num_rep, time_rep, ssh_options):
        test_command = f'ssh -i {ssh_key} {ssh_options} {user}@{ip} echo test'
        utils.pipe_log('Waiting for available SSH connection')
        utils.pipe_log(f'Command {test_command}')
        ret_code, out, err = self.__run_command(test_command)
        rep = 0
        while ret_code != 0:
            utils.pipe_log('SSH connection is not available yet')
            rep = utils.increment_or_fail(num_rep, rep, 'Failed to establish SSH connection')
            time.sleep(time_rep)
            ret_code, out, err = self.__run_command(test_command)
        utils.pipe_log('Successfully established SSH connection')


    def __collect_output(self, out, err):
        message = ''
        if out:
            message = message + str(out) + '\n'
        if err:
            message = message + str(err) + '\n'
        return message

    def __create_userdata_file(self, run_id, ins_type, ins_img, ins_platform, is_spot, global_distribution_url):
        swap_size = utils.get_swap_size(self.cloud_region, ins_type, is_spot, "GCP")
        content = utils.get_user_data_script(self.cloud_region, ins_type, ins_img,
                                                      ins_platform, '', '', '', '',
                                                      global_distribution_url, swap_size,
                                                      cert_folder='/etc/containerd/certs.d')
        file_name = f'{run_id}.init.sh'
        with open(file_name, "w") as user_data:
            user_data.write(content)
        return file_name

    def __run_command(self, command):
        p = subprocess.Popen(command, shell=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        out, err = p.communicate()
        print(err)
        print(out)
        return p.returncode, out, err

    def __log_and_raise(self, message):
        utils.pipe_log_warn(message)
        raise RuntimeError(message)

    @staticmethod
    def get_tags(run_id, pool_id, cloud_region):
        tags = GKEInstanceProvider.run_id_tag(run_id)
        if pool_id:
            GKEInstanceProvider.append_tags(tags, {'pool_id': str(pool_id)})
            GKEInstanceProvider.append_tags(tags, GKEInstanceProvider.resource_tags())
            GKEInstanceProvider.append_tags(tags, utils.get_region_tags(cloud_region))
        return tags

    @staticmethod
    def append_tags(tags, tags_to_add):
        if tags_to_add is None:
            return
        for key in tags_to_add:
            tags[key.lower()] = tags_to_add[key].lower()

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
            'name': str(run_id),
        }

    @staticmethod
    def __wait_for_extended_operation(
        operation: ExtendedOperation,
        verbose_name: str = "operation",
        timeout: int = 300):
            result = operation.result(timeout=timeout)

            if operation.error_code:
                utils.pipe_log(f"Error during {verbose_name}: [Code: {operation.error_code}]: {operation.error_message}")
                utils.pipe_log(f"Operation ID: {operation.name}")
                raise operation.exception() or RuntimeError(operation.error_message)

            if operation.warnings:
                utils.pipe_log(f"Warnings during {verbose_name}:\n")
                for warning in operation.warnings:
                    utils.pipe_log(f" - {warning.code}: {warning.message}")
            return True

