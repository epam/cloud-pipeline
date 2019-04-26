#!/bin/bash
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

echoerr() { echo "$@" 1>&2; }

run_instance_json=$(aws ec2 run-instances --image-id "$CP_AWS_NEW_AMI" \
                                          --region "$CP_AWS_NEW_REGION" \
                                          --count 1 \
                                          --instance-type "$CP_AWS_NEW_SIZE" \
                                          --block-device-mapping "DeviceName=$CP_AWS_NEW_DISK_NAME,Ebs={VolumeSize=$CP_AWS_NEW_DISK_SIZE,VolumeType=gp2}" \
                                          --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${dist_build}.${dist_commit}}]" \
                                          --key-name $CP_AWS_NEW_KEY_NAME \
                                          --security-group-ids $CP_AWS_NEW_SG)

run_instance_result=$?

if [ $run_instance_result -ne 0 ]; then
    echoerr "ERROR: unable to start instance"
    echoerr "$run_instance_json"
    exit 1
fi

instance_id=$(echo $run_instance_json | jq -r '.Instances[0].InstanceId')
echoerr "Instance is started with: $instance_id. Waiting for initialization..."

echoerr "Wait for the initialization to finish"
instance_state_code=0
while [ "$instance_state_code" != "16" ]; do
    instance_state_code=$(aws ec2 --region $CP_AWS_NEW_REGION describe-instances --instance-id "$instance_id" | jq -r '.Reservations[0].Instances[0].State.Code')
    sleep 5
done
echoerr "Instance $instance_id is running"

instance_ip=$(aws ec2 --region $CP_AWS_NEW_REGION describe-instances --instance-id "$instance_id" | jq -r ".Reservations[0].Instances[0].$CP_AWS_NEW_IP_TYPE")
if [ -z "$instance_ip" ]; then
    echoerr "Cannot retrieve $CP_AWS_NEW_IP_TYPE of the $instance_id instance"
fi
echo $instance_ip
