# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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
import pykube
import argparse
import time

RUN_ID_LABEL = 'runid'
AWS_REGION_LABEL = 'aws_region'
CLOUD_REGION_LABEL = 'cloud_region'
KUBE_CONFIG_PATH = '~/.kube/config'
AWS_TERMINATION_ATTEMPTS = 3


def get_aws_instance(ec2, node_internal_ip):
    if node_internal_ip is None:
        return None
    # Trying to find node by internal ip address:
    response = ec2.describe_instances(Filters=[{'Name': 'private-ip-address', 'Values': [node_internal_ip]}])
    if 'Reservations' in response:
        reservations = response['Reservations']
        if len(reservations) > 0 and 'Instances' in reservations[0]:
            instances = reservations[0]['Instances']
            if len(instances) > 0 and 'InstanceId' in instances[0]:
                # EC2 Node found
                return { 'id': instances[0]['InstanceId'], 'state': instances[0]['State']['Name'] }
    return None


def terminate_instance(ec2, node_internal_ip):
    instance = get_aws_instance(ec2, node_internal_ip)
    terminated_instance = None
    if instance is not None:
        ec2.terminate_instances(InstanceIds=[instance['id']])
        terminated_instance = get_aws_instance(ec2, node_internal_ip)
    return terminated_instance == None or terminated_instance['state'] != 'running'


def delete_kubernetes_node(kube_api, node_name):
    if node_name is not None and get_node(kube_api, node_name) is not None:
        obj = {
            "apiVersion": "v1",
            "kind": "Node",
            "metadata": {
                "name": node_name
            }
        }
        pykube.Node(kube_api, obj).delete()


def get_node(kube_api, nodename):
    nodes = pykube.Node.objects(kube_api).filter(field_selector={'metadata.name': nodename})
    if len(nodes.response['items']) == 0:
        return None
    return nodes.response['items'][0]


def get_aws_region(api, nodename):
    node = get_node(api, nodename)
    if node is None:
        raise RuntimeError('Cannot find node matching name %s' % nodename)
    labels = node['metadata']['labels']
    if AWS_REGION_LABEL not in labels and CLOUD_REGION_LABEL not in labels:
        raise RuntimeError('Node %s is not labeled with AWS Region' % node['metadata']['name'])
    return labels[CLOUD_REGION_LABEL] if CLOUD_REGION_LABEL in labels else labels[AWS_REGION_LABEL]


def get_kube_api():
    try:
        api = pykube.HTTPClient(pykube.KubeConfig.from_service_account())
    except Exception:
        api = pykube.HTTPClient(pykube.KubeConfig.from_file(KUBE_CONFIG_PATH))
    api.session.verify = False
    return api


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--internal_ip", "-ip", type=str, required=True)
    parser.add_argument("--node_name", "-n", type=str, required=True)
    args, unknown = parser.parse_known_args()
    kube_api = get_kube_api()
    aws_region = get_aws_region(kube_api, args.node_name)
    delete_kubernetes_node(kube_api, args.node_name)

    ec2 = boto3.client('ec2', region_name=aws_region)
    termination_attempts = 1
    is_terminated = False
    while termination_attempts <= AWS_TERMINATION_ATTEMPTS:
        is_terminated = terminate_instance(ec2, args.internal_ip)
        if is_terminated:
            break
        else:
            time.sleep(1)
        termination_attempts+=1
    if not is_terminated:
        raise RuntimeError("Unable to terminate {} after {} attempts".format(args.internal_ip, termination_attempts))


if __name__ == '__main__':
    main()
