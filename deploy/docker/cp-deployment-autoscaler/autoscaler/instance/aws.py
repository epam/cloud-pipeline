# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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


import time

import boto3
import collections
import logging
import os
from botocore.config import Config

from autoscaler.exception import ForbiddenInstanceTerminationError
from autoscaler.instance.provider import InstanceProvider, pack_script_contents
from autoscaler.config import AutoscalingConfiguration
from autoscaler.model import Persistence, Instance


class InstanceScaleUpTimeoutError(RuntimeError):
    pass


class AwsProvider(InstanceProvider):

    def __init__(self, configuration):
        self._configuration: AutoscalingConfiguration = configuration
        os.environ['AWS_DEFAULT_REGION'] = self._configuration.instance.region
        self._ec2 = boto3.client('ec2', config=Config(retries={'max_attempts': self._configuration.misc.boto3_retry_count}))
        self._RUNNING = 16
        self._PENDING = 0

    def launch_instance(self):
        logging.info('Launching instance...')
        with open(self._configuration.instance.init_script, 'r') as init_script_file:
            user_data_script = init_script_file.read()
            kube_labels = dict(self._configuration.target.labels)
            kube_labels.update(self._configuration.target.transient_labels)
            kube_labels_string = ','.join(f'{k}={v}' for k, v in kube_labels.items())
            user_data_script = user_data_script.replace('@KUBE_IP@', self._configuration.node.kube_ip) \
                                               .replace('@KUBE_PORT@', self._configuration.node.kube_port) \
                                               .replace('@KUBE_TOKEN@', self._configuration.node.kube_token) \
                                               .replace('@KUBE_DNS_IP@', self._configuration.node.kube_dns_ip) \
                                               .replace('@KUBE_LABELS@', kube_labels_string) \
                                               .replace('@AWS_FS_URL@', self._configuration.node.aws_fs_url) \
                                               .replace('@HTTP_PROXY@', self._configuration.node.http_proxy) \
                                               .replace('@HTTPS_PROXY@', self._configuration.node.https_proxy) \
                                               .replace('@NO_PROXY@', self._configuration.node.no_proxy)
            compressed_user_data_script = pack_script_contents(user_data_script)
        raw_tags = self._merge_dicts({'Name': self._configuration.instance.name},
                                     self._configuration.target.tags,
                                     self._configuration.target.transient_tags)
        tags = [{'Key': key, 'Value': value} for key, value in raw_tags.items()]
        response = self._ec2.run_instances(
            ImageId=self._configuration.instance.image,
            MinCount=1,
            MaxCount=1,
            KeyName=self._configuration.instance.sshkey,
            InstanceType=self._configuration.instance.type,
            UserData=compressed_user_data_script,
            BlockDeviceMappings=[{
                'DeviceName': '/dev/sda1',
                'Ebs': {'VolumeSize': self._configuration.instance.disk}
            }],
            TagSpecifications=[{
                'ResourceType': 'instance',
                'Tags': tags
            }],
            MetadataOptions={
                'HttpTokens': 'optional',
                'HttpPutResponseHopLimit': 2,
                'HttpEndpoint': 'enabled'
            },
            IamInstanceProfile={
                'Arn': self._configuration.instance.role
            },
            SubnetId=self._configuration.instance.subnet,
            SecurityGroupIds=self._configuration.instance.security_groups
        )
        instances = response.get('Instances')
        instance_id = instances[0].get('InstanceId')
        instance_ip = instances[0].get('PrivateIpAddress')
        logging.info('Instance %s (%s) has been launched.', instance_id, instance_ip)

        logging.info('Initializing instance %s (%s)...', instance_id, instance_ip)
        try:
            timeout = self._configuration.timeout.scale_up_instance_timeout
            while timeout > 0:
                time.sleep(self._configuration.timeout.scale_up_instance_delay)
                timeout -= self._configuration.timeout.scale_up_instance_delay
                status_code = self._get_current_status(instance_id)
                if status_code == self._RUNNING:
                    break
            if timeout <= 0:
                logging.warning('Instance %s is not running after %s seconds.',
                                instance_id, self._configuration.timeout.scale_up_instance_timeout)
                raise InstanceScaleUpTimeoutError(instance_id)
            logging.info('Instance %s (%s) has been initialized.', instance_id, instance_ip)
            return instance_id, instance_ip
        except Exception:
            logging.warning('Instance %s (%s) initialization has failed. It will be terminated.',
                            instance_id, instance_ip)
            self.terminate_instance(Instance(name=instance_id, persistence=Persistence.TRANSIENT))
            raise

    def _get_current_status(self, instance_id):
        try:
            response = self._ec2.describe_instance_status(InstanceIds=[instance_id])
            statuses = response.get('InstanceStatuses') or [{}]
            return statuses[0].get('InstanceState', {}).get('Code')
        except Exception as e:
            if 'does not exist' in e:
                logging.info('Get status request for instance %s returned error %s.' % (instance_id, e))
                return -1
            else:
                raise e

    def terminate_instance(self, instance):
        instance_id = instance.name
        logging.info('Terminating instance %s...', instance_id)
        if instance_id in self._configuration.target.forbidden_instances:
            logging.warning('Instance %s is forbidden to be terminated.')
            raise ForbiddenInstanceTerminationError(instance_id)
        self._ec2.terminate_instances(InstanceIds=[instance_id])
        logging.info('Terminated instance %s.', instance_id)

    def get_instances(self):
        raw_tags = self._merge_multidicts(self._configuration.target.tags)
        filters = [{'Name': 'tag:' + key, 'Values': values} for key, values in raw_tags.items()]
        filters.append({'Name': 'instance-state-name', 'Values': ['pending', 'running']})
        response = self._ec2.describe_instances(Filters=filters)
        reservations = response.get('Reservations', [])
        for reservation in reservations:
            instances = reservation.get('Instances', [])
            for instance in instances:
                instance_id = instance.get('InstanceId')
                tags = instance.get('Tags', [])
                persistence = Persistence.PERSISTENT
                for tag in tags:
                    key = tag.get('Key', '')
                    value = tag.get('Value', '')
                    if self._configuration.target.transient_tags.get(key, '') == value:
                        persistence = Persistence.TRANSIENT
                        break
                yield Instance(instance_id, persistence)

    def _merge_dicts(self, *dicts):
        merged = {}
        for batch in dicts:
            merged.update(batch)
        return merged

    def _merge_multidicts(self, *dicts):
        merged = collections.defaultdict(list)
        for batch in dicts:
            for key, value in batch.items():
                merged[key].append(value)
        return merged
