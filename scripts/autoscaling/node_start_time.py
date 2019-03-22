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
import pytz
import pykube

RUN_ID_LABEL = 'runid'
AWS_REGION_LABEL = 'aws_region'


def get_instance_start_time(ec2, run_id):
    response = ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [run_id]}])
    start_date = response['Reservations'][0]['Instances'][0]['LaunchTime'].astimezone(pytz.utc)
    print(str(start_date)[:-6])


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
    parser.add_argument("--run_id", "-kid", type=str, required=True)
    args = parser.parse_args()
    run_id = args.run_id
    kube_api = get_kube_api()
    aws_region = get_aws_region(kube_api, run_id)
    ec2 = boto3.client('ec2', region_name=aws_region)
    return get_instance_start_time(ec2, run_id)


if __name__ == '__main__':
    main()
