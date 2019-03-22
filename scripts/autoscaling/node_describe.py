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
import boto3


def verify_run_id(ec2, run_id):
    response = ec2.describe_instances(Filters=[{'Name': 'tag:Name', 'Values': [run_id]}, {'Name': 'instance-state-name', 'Values': ['pending', 'running']}])
    if len(response['Reservations']) > 0:
        ins_id = response['Reservations'][0]['Instances'][0]['InstanceId']
        ins_ip = response['Reservations'][0]['Instances'][0]['PrivateIpAddress']
    else:
        raise RuntimeError("Failed to find instance {}".format(run_id))
    return ins_id, ins_ip


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run_id", type=str, required=True)
    parser.add_argument("--region_id", type=str, required=True)
    args = parser.parse_args()
    run_id = args.run_id
    region_id = args.region_id
    ec2 = boto3.client('ec2', region_name=region_id)
    ins_id, ins_ip = verify_run_id(ec2, run_id)
    print(ins_id + "\t" + ins_ip)


if __name__ == '__main__':
    main()
