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

from autoscaling.utils.cloud_client import CloudClient


class Ec2Client(CloudClient):

    name = "EC2"

    def get_private_ip(self, instance):
        if 'Reservations' not in instance:
            return None
        if 'Instances' not in instance['Reservations'][0]:
            return None
        if 'PrivateIpAddress' not in instance['Reservations'][0]['Instances'][0]:
            return None
        return instance['Reservations'][0]['Instances'][0]['PrivateIpAddress']

    def terminate_instance(self, run_id):
        ec2 = boto3.client('ec2')
        ins_id = self.__find_instance(ec2, run_id)
        if ins_id:
            ec2.terminate_instances(InstanceIds=[ins_id])
        else:
            raise RuntimeError("Can not describe instance {}.".format(run_id))

    def node_price_type_should_be(self, run_id, spot):
        ec2 = boto3.client('ec2')
        instance_description = self.__get_instance_description(ec2, run_id)

        expected_lifecycle_type = 'spot' if spot else 'scheduled'
        actual_lifecycle_type = instance_description['InstanceLifecycle'] \
            if 'InstanceLifecycle' in instance_description else 'scheduled'

        assert actual_lifecycle_type == expected_lifecycle_type, \
            'Price type differs.\n Expected: %s.\n Actual: %s.' \
            % (expected_lifecycle_type, actual_lifecycle_type)

    def describe_instance(self, run_id):
        ec2 = boto3.client('ec2')
        response = ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [run_id]},
                                                   {'Name': 'instance-state-name', 'Values': ['pending', 'running']}])
        if len(response['Reservations']) > 0:
            return response

    @staticmethod
    def __get_instance_description(ec2, run_id):
        response = ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [run_id]}])
        assert len(response['Reservations']) > 0, \
            "Node with run_id=%s wasn't found in aws " \
            "(Empty reservations set in response)." % run_id

        reservation = response['Reservations'][0]

        assert len(reservation['Instances']) == 1, \
            "Node with run_id=%s wasn't found in aws " \
            "(Empty instances list in response)." % run_id

        return reservation['Instances'][0]

    @staticmethod
    def __find_instance(ec2, run_id):
        response = ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [run_id]}])
        if 'Reservations' not in response:
            return None
        if 'Instances' not in response['Reservations'][0]:
            return None
        instance = response['Reservations'][0]['Instances'][0]
        if 'InstanceId' not in instance:
            return None
        return instance['InstanceId']
