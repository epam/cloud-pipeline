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
import argparse
import pykube

RUN_ID_LABEL = 'runid'
AWS_REGION_LABEL = 'aws_region'


def find_and_tag_instance(ec2, old_id, new_id):
    response = ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [old_id]},
                                               {'Name': 'instance-state-name', 'Values': ['pending', 'running']}])
    tags = [{'Key': 'Name', 'Value': new_id}]
    if len(response['Reservations']) > 0:
        ins_id = response['Reservations'][0]['Instances'][0]['InstanceId']
        ec2.create_tags(
            Resources=[ins_id],
            Tags=tags
        )
        return ins_id
    else:
        raise RuntimeError("Failed to find instance {}".format(old_id))


def verify_regnode(kube_api, ec2, ins_id):
    response = ec2.describe_instances(InstanceIds=[ins_id])
    nodename = response['Reservations'][0]['Instances'][0]['PrivateDnsName'].split('.', 1)[0]
    nodename_full = response['Reservations'][0]['Instances'][0]['PrivateDnsName']

    exist_node = False
    ret_namenode = ""
    node = pykube.Node.objects(kube_api).filter(field_selector={'metadata.name': nodename})
    if len(node.response['items']) > 0:
        exist_node = True
        ret_namenode = nodename
    node_full = pykube.Node.objects(kube_api).filter(field_selector={'metadata.name': nodename_full})
    if len(node_full.response['items']) > 0:
        exist_node = True
        ret_namenode = nodename_full
    if not exist_node:
        raise RuntimeError("Failed to find Node {}".format(ins_id))
    return ret_namenode


def find_instance_name(ec2, ins_id):
    response = ec2.describe_instances(InstanceIds=[ins_id])
    nodename = response['Reservations'][0]['Instances'][0]['PrivateDnsName'].split('.', 1)[0]
    return nodename


def change_label(api, nodename, new_id, aws_region):
    obj = {
        "apiVersion": "v1",
        "kind": "Node",
        "metadata": {
            "name": nodename,
            "labels": {
                RUN_ID_LABEL: new_id
            }
        }
    }
    node = pykube.Node(api, obj)
    node.labels[RUN_ID_LABEL] = new_id
    node.labels[AWS_REGION_LABEL] = aws_region
    node.update()


def get_aws_region(api, run_id):
    nodes = pykube.Node.objects(api).filter(selector={RUN_ID_LABEL: run_id})
    if len(nodes.response['items']) == 0:
        raise RuntimeError('Cannot find node matching RUN ID %s' % run_id)
    node = nodes.response['items'][0]
    labels = node['metadata']['labels']
    if AWS_REGION_LABEL not in labels:
        raise RuntimeError('Node %s is not labeled with AWS Region' % node['metadata']['name'])
    return labels[AWS_REGION_LABEL]


def get_kube_api():
    api = pykube.HTTPClient(pykube.KubeConfig.from_file("~/.kube/config"))
    api.session.verify = False
    return api


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--old_id", "-kid", type=str, required=True)
    parser.add_argument("--new_id", "-nid", type=str, required=True)
    args = parser.parse_args()
    old_id = args.old_id
    new_id = args.new_id

    kube_api = get_kube_api()
    aws_region = get_aws_region(kube_api, old_id)
    ec2 = boto3.client('ec2', region_name=aws_region)
    ins_id = find_and_tag_instance(ec2, old_id, new_id)
    nodename = verify_regnode(kube_api, ec2, ins_id)
    change_label(kube_api, nodename, new_id, aws_region)


if __name__ == '__main__':
    main()
