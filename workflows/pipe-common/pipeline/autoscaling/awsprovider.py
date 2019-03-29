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

import boto3
from botocore.config import Config
from distutils.version import LooseVersion
from random import randint
from time import sleep
import base64
import socket
from datetime import datetime, timedelta
import pytz
from itertools import groupby
from operator import itemgetter
import sys

from cloudprovider import AbstractInstanceProvider
from pipeline import TaskStatus
from pipeline.autoscaling import utils

ROOT_DEVICE_DEFAULT = {
    "DeviceName": "/dev/sda1",
    "Ebs": {"VolumeSize": 40}
}


class AWSInstanceProvider(AbstractInstanceProvider):

    def __init__(self, cloud_region, num_rep=10):
        self.cloud_region = cloud_region
        boto3.setup_default_session(region_name=cloud_region)

        # Hacking max max_attempts to get rid of
        # "An error occurred (RequestLimitExceeded) when calling the ... operation (reached max retries: 4)"
        # Official solution shall be provided with https://github.com/boto/botocore/pull/1260, waiting for release
        # This is applied to the old versions of botocore
        boto3_version = LooseVersion(boto3.__version__)
        boto3_version_retries = LooseVersion("1.7")
        utils.pipe_log('Using boto3 version {}'.format(boto3.__version__))

        self.ec2 = None
        if boto3_version < boto3_version_retries:
            try:
                self.ec2 = boto3.client('ec2')
                if hasattr(self.ec2.meta.events, "_unique_id_handlers"):
                    self.ec2.meta.events._unique_id_handlers['retry-config-ec2']['handler']._checker.__dict__['_max_attempts'] = num_rep
            except Exception as inner_exception:
                utils.pipe_log('Unable to modify retry config:\n{}'.format(str(inner_exception)))
        else:
            self.ec2 = boto3.client('ec2', config=Config(retries={'max_attempts': num_rep}))

    def run_instance(self, is_spot, bid_price, ins_type, ins_hdd, ins_img, ins_key, run_id, kms_encyr_key_id,
                     num_rep, time_rep, kube_ip, kubeadm_token):

        ins_id, ins_ip = self.__check_spot_request_exists(num_rep, run_id, time_rep)
        if ins_id:
            return ins_id, ins_ip

        user_data_script = utils.get_user_data_script(self.cloud_region, ins_type, ins_img, kube_ip, kubeadm_token)
        if is_spot:
            ins_id, ins_ip = self.__find_spot_instance(bid_price, run_id, ins_img, ins_type, ins_key, ins_hdd,
                                                             kms_encyr_key_id, user_data_script, num_rep, time_rep)
        else:
            ins_id, ins_ip = self.__run_on_demand_instance(ins_img, ins_key, ins_type, ins_hdd,
                                                                 kms_encyr_key_id, run_id, user_data_script,
                                                                 num_rep, time_rep)
        return ins_id, ins_ip

    def check_instance(self, ins_id, run_id, num_rep, time_rep):
        utils.pipe_log('Checking instance ({}) boot state'.format(ins_id))
        port=8888
        response = self.ec2.describe_instances(InstanceIds=[ins_id])
        ipaddr = response['Reservations'][0]['Instances'][0]['PrivateIpAddress']
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        utils.pipe_log('- Waiting for instance boot up...')
        result = utils.poll_instance(sock, time_rep, ipaddr, port)
        rep = 0
        while result != 0:
            sleep(time_rep)
            result = utils.poll_instance(sock, time_rep, ipaddr, port)
            rep = self.__increment_or_fail(num_rep, rep, 'Exceeded retry count ({}) for instance ({}) network check on port {}'.format(num_rep, ins_id, port),
                                           kill_instance_id_on_fail=ins_id)
        utils.pipe_log('Instance is booted. ID: {}, IP: {}\n-'.format(ins_id, ipaddr))

    def find_and_tag_instance(self, old_id, new_id):
        response = self.ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [old_id]},
                                                   {'Name': 'instance-state-name', 'Values': ['pending', 'running']}])
        tags = [{'Key': 'Name', 'Value': new_id}]
        if len(response['Reservations']) > 0:
            ins_id = response['Reservations'][0]['Instances'][0]['InstanceId']
            self.ec2.create_tags(
                Resources=[ins_id],
                Tags=tags
            )
            return ins_id
        else:
            raise RuntimeError("Failed to find instance {}".format(old_id))

    def verify_run_id(self, run_id):
        utils.pipe_log('Checking if instance already exists for RunID {}'.format(run_id))
        response = self.ec2.describe_instances(
            Filters=[
                AWSInstanceProvider.run_id_filter(run_id),
                {
                    'Name': 'instance-state-name',
                    'Values': ['pending', 'running']
                }
            ]
        )
        if len(response['Reservations']) > 0:
            ins_id = response['Reservations'][0]['Instances'][0]['InstanceId']
            ins_ip = response['Reservations'][0]['Instances'][0]['PrivateIpAddress']
            utils.pipe_log('Found existing instance (ID: {}, IP: {}) for RunID {}\n-'.format(ins_id, ins_ip, run_id))
        else:
            ins_id = ''
            ins_ip = ''
            utils.pipe_log('No existing instance found for RunID {}\n-'.format(run_id))
        return ins_id, ins_ip

    def terminate_instance(self, instance_id):
        if not instance_id or len(instance_id) == 0:
            utils.pipe_log('[ERROR] None or empty string specified when calling terminate_instance, nothing will be done')
            return

        try:
            response = self.ec2.terminate_instances(
                InstanceIds=[
                    instance_id
                ]
            )
        except Exception as terminate_exception:
            utils.pipe_log('[ERROR] Error during instance {} termination:\n{}'.format(instance_id, str(terminate_exception)))
            return

        if 'TerminatingInstances' not in response or len(response['TerminatingInstances']) == 0:
            utils.pipe_log('[ERROR] Unable to parse response of the {} instance termination request. '
                           'TerminatingInstances entry no found or it contains 0 elements')
            return

        termination_state=response['TerminatingInstances'][0]
        prev_state=termination_state['PreviousState']['Name']
        current_state=termination_state['CurrentState']['Name']

        utils.pipe_log('Instance {} state is changed from {} to {}'.format(instance_id, prev_state, current_state))

    def get_instance_names(self, ins_id):
        response = self.ec2.describe_instances(InstanceIds=[ins_id])
        nodename_full = response['Reservations'][0]['Instances'][0]['PrivateDnsName']
        nodename = nodename_full.split('.', 1)[0]

        return nodename, nodename_full

    def find_instance(self, run_id):
        response = self.ec2.describe_instances(
                Filters=[
                    self.run_id_filter(run_id),
                    {
                        'Name': 'instance-state-name',
                        'Values': ['pending', 'running', 'rebooting']
                    }
                ]
            )
        if len(response['Reservations']) > 0:
            ins_id = response['Reservations'][0]['Instances'][0]['InstanceId']
        else:
            ins_id = None
        return ins_id

    def terminate_instance_by_ip(self, node_internal_ip):
        instance_id = self.__get_aws_instance_id(node_internal_ip)
        if instance_id is not None:
            self.ec2.terminate_instances(InstanceIds=[instance_id])

    @staticmethod
    def get_mean(values):
        n = 0
        _sum = 0.0
        for v in values:
            _sum += v
            n += 1
        return _sum / n

    @staticmethod
    def run_id_filter(run_id):
        return {
            'Name': 'tag:Name',
            'Values': [run_id]
        }

    def __run_on_demand_instance(self, ins_img, ins_key, ins_type, ins_hdd, kms_encyr_key_id, run_id, user_data_script,
                                 num_rep, time_rep):
        utils.pipe_log('Creating on demand instance')
        allowed_networks = utils.get_networks_config(self.cloud_region)
        additional_args = {}
        subnet_id = None
        if allowed_networks and len(allowed_networks) > 0:
            az_num = randint(0, len(allowed_networks) - 1)
            az_name = allowed_networks.items()[az_num][0]
            subnet_id = allowed_networks.items()[az_num][1]
            utils.pipe_log('- Networks list found, subnet {} in AZ {} will be used'.format(subnet_id, az_name))
            additional_args = {'SubnetId': subnet_id, 'SecurityGroupIds': utils.get_security_groups(self.cloud_region)}
        else:
            utils.pipe_log('- Networks list NOT found, default subnet in random AZ will be used')
            additional_args = {'SecurityGroupIds': utils.get_security_groups(self.cloud_region)}
        response = self.ec2.run_instances(
            ImageId=ins_img,
            MinCount=1,
            MaxCount=1,
            KeyName=ins_key,
            InstanceType=ins_type,
            UserData=user_data_script,
            BlockDeviceMappings=[self.__root_device(ins_img),
                                 AWSInstanceProvider.block_device(ins_hdd, kms_encyr_key_id)],
            TagSpecifications=[
                {
                    'ResourceType': 'instance',
                    "Tags": AWSInstanceProvider.get_tags(run_id)
                }
            ],
            **additional_args
        )
        ins_id = response['Instances'][0]['InstanceId']
        ins_ip = response['Instances'][0]['PrivateIpAddress']

        utils.pipe_log('- Instance created. ID: {}, IP: {}'.format(ins_id, ins_ip))

        status_code = self.get_current_status(ins_id)

        rep = 0
        while status_code != 16:
            utils.pipe_log('- Waiting for status checks completion...')
            sleep(time_rep)
            status_code = self.get_current_status(ins_id)
            rep = self.__increment_or_fail(num_rep,
                                           rep,
                                           'Exceeded retry count ({}) for instance ({}) status check'.format(num_rep,
                                                                                                             ins_id),
                                           kill_instance_id_on_fail=ins_id)
        utils.pipe_log('Instance created. ID: {}, IP: {}\n-'.format(ins_id, ins_ip))

        ebs_tags = AWSInstanceProvider.resource_tags()
        if ebs_tags:
            instance_description = self.ec2.describe_instances(InstanceIds=[ins_id])['Reservations'][0]['Instances'][0]
            volumes = instance_description['BlockDeviceMappings']
            for volume in volumes:
                self.ec2.create_tags(
                    Resources=[volume['Ebs']['VolumeId']],
                    Tags=ebs_tags)

        return ins_id, ins_ip

    def get_current_status(self, ins_id):
        response = self.ec2.describe_instance_status(InstanceIds=[ins_id])
        if len(response['InstanceStatuses']) > 0:
            return response['InstanceStatuses'][0]['InstanceState']['Code']
        else:
            return -1

    def __root_device(self, ins_img):
        try:
            utils.pipe_log('- Getting image {} block device mapping details'.format(ins_img))
            img_details = self.ec2.describe_images(ImageIds=[ins_img])
            utils.pipe_log('- Block device mapping details received. Proceeding with validation')

            if len(img_details["Images"]) == 0:
                raise RuntimeError("No images found for {}".format(ins_img))
            img_obj = img_details["Images"][0]

            if "BlockDeviceMappings" not in img_obj or len(img_obj["BlockDeviceMappings"]) == 0:
                raise RuntimeError("No BlockDeviceMappings found for {}".format(ins_img))

            block_device_obj = img_obj["BlockDeviceMappings"][0]
            block_device_name = block_device_obj["DeviceName"]
            if "Ebs" not in block_device_obj:
                raise RuntimeError(
                    "No Ebs definition found for device {} in image {}".format(block_device_name, ins_img))

            device_spec = {
                "DeviceName": block_device_name,
                "Ebs": {"VolumeSize": block_device_obj["Ebs"]["VolumeSize"], "VolumeType": "gp2"}
            }
            return device_spec
        except Exception as e:
            utils.pipe_log('Error while getting image {} root device, using default device: {}\n{}'.format(ins_img,
                                                                                                           ROOT_DEVICE_DEFAULT[
                                                                                                               "DeviceName"],
                                                                                                           str(e)))
            return ROOT_DEVICE_DEFAULT

    @staticmethod
    def block_device(ins_hdd, kms_encyr_key_id):
        block_device_spec = {
            "DeviceName": "/dev/sdb",
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

    def __get_availability_zones(self):
        zones = self.ec2.describe_availability_zones()
        return [zone['ZoneName'] for zone in zones['AvailabilityZones']]

    def __get_spot_prices(self, aws_region, instance_type, hours=3):
        prices = []
        allowed_networks = utils.get_networks_config(aws_region)
        if allowed_networks and len(allowed_networks) > 0:
            zones = list(allowed_networks.keys())
        else:
            zones = self.__get_availability_zones()
        history = self.ec2.describe_spot_price_history(
            StartTime=datetime.today() - timedelta(hours=hours),
            EndTime=datetime.today(),
            InstanceTypes=[instance_type],
            ProductDescriptions=['Linux/UNIX'],
            Filters=[{'Name': 'availability-zone', 'Values': zones}],
        )
        history = history['SpotPriceHistory']
        grouper = itemgetter('AvailabilityZone')
        for zone, items in groupby(sorted(history, key=grouper), key=grouper):
            price = AWSInstanceProvider.get_mean([float(i['SpotPrice']) for i in items])
            prices.append((zone, price))
        return sorted(prices, key=lambda t: t[1])

    @staticmethod
    def exit_if_spot_unavailable(run_id, last_status):
        # will exit with code '5' if a spot request can't be fulfilled
        if last_status in ['capacity-not-available', 'capacity-oversubscribed', 'constraint-not-fulfillable']:
            utils.pipe_log('[ERROR] Could not fulfill spot request for run {}, status: {}'.format(run_id, last_status),
                           status=TaskStatus.FAILURE)
            sys.exit(5)

    def __find_spot_instance(self, bid_price, run_id, ins_img, ins_type, ins_key, ins_hdd, kms_encyr_key_id, user_data_script, num_rep, time_rep):
        utils.pipe_log('Creating spot request')

        utils.pipe_log('- Checking spot prices for current region...')
        spot_prices = self.__get_spot_prices(self.ec2, self.cloud_region, ins_type)

        allowed_networks = utils.get_networks_config(self.cloud_region)
        cheapest_zone = ''
        if len(spot_prices) == 0:
            utils.pipe_log('- Unable to get prices for a spot of type {}, cheapest zone can not be determined'.format(ins_type))
        else:
            cheapest_zone, _ = spot_prices[0]
            utils.pipe_log('- Prices for {} spots:\n'.format(ins_type) +
                           '\n'.join('{0}: {1:.5f}'.format(zone, price) for zone, price in spot_prices) + '\n' +
                           '{} zone will be used'.format(cheapest_zone))

        specifications = {
            'ImageId': ins_img,
            'InstanceType': ins_type,
            'KeyName': ins_key,
            'UserData': base64.b64encode(user_data_script.encode('utf-8')).decode('utf-8'),
            'BlockDeviceMappings': [self.__root_device(ins_img), AWSInstanceProvider.block_device(ins_hdd, kms_encyr_key_id)],
        }
        if allowed_networks and cheapest_zone in allowed_networks:
            subnet_id = allowed_networks[cheapest_zone]
            utils.pipe_log('- Networks list found, subnet {} in AZ {} will be used'.format(subnet_id, cheapest_zone))
            specifications['SubnetId'] = subnet_id
            specifications['SecurityGroupIds'] = utils.get_security_groups(self.cloud_region)
            specifications['Placement'] = { 'AvailabilityZone': cheapest_zone }
        else:
            utils.pipe_log('- Networks list NOT found or cheapest zone can not be determined, default subnet in a random AZ will be used')
            specifications['SecurityGroupIds'] = utils.get_security_groups(self.cloud_region)

        current_time = datetime.now(pytz.utc) + timedelta(seconds=10)
        response = self.ec2.request_spot_instances(
            SpotPrice=str(bid_price),
            InstanceCount=1,
            Type='one-time',
            ValidFrom=current_time,
            ValidUntil=current_time + timedelta(seconds=num_rep * time_rep),
            LaunchSpecification=specifications,
        )
        rep = 0
        ins_id = ''
        ins_ip = ''

        request_id = response['SpotInstanceRequests'][0]['SpotInstanceRequestId']

        if not request_id:
            raise RuntimeError('Spot instance request did not return a SpotInstanceRequestId')

        utils.pipe_log('- Spot request was sent. SpotInstanceRequestId: {}. Waiting for spot request registration...'.format(request_id))

        # Await for spot request registration (sometimes SpotInstanceRequestId is not returned immediately)
        while rep <= num_rep:
            try:
                requests_list = self.ec2.describe_spot_instance_requests(SpotInstanceRequestIds=[request_id])
                if len(requests_list['SpotInstanceRequests']) > 0:
                    break

            except Exception as e:
                if e.response['Error']['Code'] != "InvalidSpotInstanceRequestID.NotFound":
                    raise e

            rep = self.__increment_or_fail(num_rep, rep,
                                           'Exceeded retry count ({}) while waiting for spot request {}'.format(num_rep, request_id),
                                           kill_instance_id_on_fail=ins_id)

            utils.pipe_log('- Spot request {} is not yet available. Still waiting...'.format(request_id))
            sleep(time_rep)
        #

        utils.pipe_log('- Spot request {} is registered'.format(request_id))
        self.ec2.create_tags(
            Resources=[request_id],
            Tags=utils.run_id_tag(run_id),
        )

        utils.pipe_log('- Spot request {} was tagged with RunID {}. Waiting for request fulfillment...'.format(request_id, run_id))

        last_status = ''
        while rep <= num_rep:
            current_request = self.ec2.describe_spot_instance_requests(SpotInstanceRequestIds=[request_id])['SpotInstanceRequests'][0]
            status = current_request['Status']['Code']
            if status == 'fulfilled':
                ins_id = current_request['InstanceId']
                instance = None
                try:
                    instance = self.ec2.describe_instances(InstanceIds=[ins_id])
                except Exception as describe_ex:
                    if describe_ex.response['Error']['Code'] == "InvalidInstanceID.NotFound":
                        utils.pipe_log('- Spot request {} is already fulfilled but instance id {} can not be found yet. Still waiting...'.format(request_id, ins_id))
                        rep = self.__increment_or_fail(num_rep, rep,
                                                       'Exceeded retry count ({}) for spot instance. Spot instance request status code: {}.'.format(num_rep, status),
                                                       kill_instance_id_on_fail=ins_id)
                        sleep(time_rep)
                        continue
                    else:
                        raise describe_ex
                instance_reservation = instance['Reservations'][0]['Instances'][0] if instance else None
                if not instance_reservation or 'PrivateIpAddress' not in instance_reservation or not instance_reservation['PrivateIpAddress']:
                    utils.pipe_log('- Spot request {} is already fulfilled but PrivateIpAddress is not yet assigned. Still waiting...'.format(request_id))
                    rep = self.__increment_or_fail(num_rep, rep,
                                                   'Exceeded retry count ({}) for spot instance. Spot instance request status code: {}.'.format(num_rep, status),
                                                   kill_instance_id_on_fail=ins_id)
                    sleep(time_rep)
                    continue
                ins_ip = instance_reservation['PrivateIpAddress']
                self.ec2.create_tags(
                    Resources=[ins_id],
                    Tags=AWSInstanceProvider.get_tags(run_id),
                )

                ebs_tags = AWSInstanceProvider.resource_tags()
                if ebs_tags:
                    volumes = instance_reservation['BlockDeviceMappings']
                    for volume in volumes:
                        self.ec2.create_tags(
                            Resources=[volume['Ebs']['VolumeId']],
                            Tags=ebs_tags)

                utils.pipe_log('Instance is successfully created for spot request {}. ID: {}, IP: {}\n-'.format(request_id, ins_id, ins_ip))
                break
            last_status = status
            utils.pipe_log('- Spot request {} is not yet fulfilled. Still waiting...'.format(request_id))
            rep = self.__increment_or_fail(num_rep, rep,
                                           'Exceeded retry count ({}) for spot instance. Spot instance request status code: {}.'.format(num_rep, status),
                                           kill_instance_id_on_fail=ins_id)
            sleep(time_rep)

        self.exit_if_spot_unavailable(run_id, last_status)

        return ins_id, ins_ip

    @staticmethod
    def tag_name_is_present(instance):
        return 'Tags' in instance and len([tag for tag in instance['Tags'] if tag['Key'] == 'Name']) > 0

    @staticmethod
    def wait_for_fulfilment(status):
        return status == 'not-scheduled-yet' or status == 'pending-evaluation' \
               or status == 'pending-fulfillment' or status == 'fulfilled'

    def __check_spot_request_exists(self, num_rep, run_id, time_rep):
        utils.pipe_log('Checking if spot request for RunID {} already exists...'.format(run_id))
        for _ in range(0, 5):
            response = self.ec2.describe_spot_instance_requests(Filters=[self.run_id_filter(run_id)])
            if len(response['SpotInstanceRequests']) > 0:
                request_id = response['SpotInstanceRequests'][0]['SpotInstanceRequestId']
                status = response['SpotInstanceRequests'][0]['Status']['Code']
                utils.pipe_log('- Spot request for RunID {} already exists: SpotInstanceRequestId: {}, Status: {}'.format(run_id, request_id, status))
                rep = 0
                if status == 'request-canceled-and-instance-running':
                    return self.__tag_and_get_instance(response, run_id)
                if AWSInstanceProvider.wait_for_fulfilment(status):
                    while status != 'fulfilled':
                        utils.pipe_log('- Spot request ({}) is not yet fulfilled. Waiting...'.format(request_id))
                        sleep(time_rep)
                        response = self.ec2.describe_spot_instance_requests(
                            SpotInstanceRequestIds=[request_id]
                        )
                        status = response['SpotInstanceRequests'][0]['Status']['Code']
                        utils.pipe_log('Exceeded retry count ({}) for spot instance (SpotInstanceRequestId: {}). Spot instance request status code: {}.'
                                       .format(num_rep, request_id, status))
                        rep = rep + 1
                        if rep > num_rep:
                            AWSInstanceProvider.exit_if_spot_unavailable(run_id, status)
                            return '', ''
                    return self.__tag_and_get_instance(response, run_id)
            sleep(5)
        utils.pipe_log('No spot request for RunID {} found\n-'.format(run_id))
        return '', ''

    def __get_aws_instance_id(self, node_internal_ip):
        if node_internal_ip is None:
            return None
        # Trying to find node by internal ip address:
        response = self.ec2.describe_instances(Filters=[{'Name': 'private-ip-address', 'Values': [node_internal_ip]}])
        if 'Reservations' in response:
            reservations = response['Reservations']
            if len(reservations) > 0 and 'Instances' in reservations[0]:
                instances = reservations[0]['Instances']
                if len(instances) > 0 and 'InstanceId' in instances[0]:
                    # EC2 Node found
                    return instances[0]['InstanceId']
        return None

    def __tag_and_get_instance(self, response, run_id):
        ins_id = response['SpotInstanceRequests'][0]['InstanceId']
        utils.pipe_log('Setting \"Name={}\" tag for instance {}'.format(run_id, ins_id))
        instance = self.ec2.describe_instances(InstanceIds=[ins_id])
        ins_ip = instance['Reservations'][0]['Instances'][0]['PrivateIpAddress']
        if not AWSInstanceProvider.tag_name_is_present(instance):  # create tag name if not presents
            self.ec2.create_tags(
                Resources=[ins_id],
                Tags=AWSInstanceProvider.get_tags(run_id),
            )
            utils.pipe_log('Tag ({}) created for instance ({})\n-'.format(run_id, ins_id))
        else:
            utils.pipe_log('Tag ({}) is already set for instance ({}). Skip tagging\n-'.format(run_id, ins_id))
        return ins_id, ins_ip

    def __increment_or_fail(self, num_rep, rep, error_message, kill_instance_id_on_fail=None):
        rep = rep + 1
        if rep > num_rep:
            if kill_instance_id_on_fail:
                utils.pipe_log('[ERROR] Operation timed out and an instance {} will be terminated\n'
                         'See more details below'.format(kill_instance_id_on_fail))
                self.ec2.terminate_instance(kill_instance_id_on_fail)

            raise RuntimeError(error_message)
        return rep

    @staticmethod
    def resource_tags():
        tags = []
        _, config_tags = utils.load_cloud_config()
        if config_tags is None:
            return tags
        for key, value in config_tags.iteritems():
            tags.append({"Key": key, "Value": value})
        return tags

    @staticmethod
    def run_id_tag(run_id):
        return [{
            'Value': run_id,
            'Key': 'Name'
        }]

    @staticmethod
    def get_tags(run_id):
        tags = AWSInstanceProvider.run_id_tag(run_id)
        res_tags = AWSInstanceProvider.resource_tags()
        if res_tags:
            tags.extend(res_tags)
        return tags