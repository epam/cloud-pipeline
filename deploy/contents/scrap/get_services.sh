#!/bin/bash

# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
# See the License for the specific language governing peKrmissions and
# limitations under the License.

source utils.sh

get_services() {
    local CP_NODE_SSH_KEY="$1" # SSH key
    local CP_NODE_USER="$2" # USER
    local $CP_NODE_IP="$3" # Server address
    local output_file="${4}/cp-services.json" #Output directory and file name where preferences will be saved

    #SSH connection to the server
    ssh_responce=$(ssh -i $CP_NODE_SSH_KEY -oStrictHostKeyChecking=no $CP_NODE_USER@$CP_NODE_IP sudo kubectl get nodes -o json | jq ['.items[] | .metadata.labels | to_entries[] | select(.key | startswith("cloud-pipeline/")) | .key | sub("cloud-pipeline/";"") | select(startswith("cp-"))'])
    if [ $? -eq 0 ] && [ -n "$ssh_responce" ]; then
       echo "$ssh_responce" > $output_file
       echo_ok "list of services from server $CP_NODE_IP saved in file $output_file"
    else
       echo_err "Error occured while connecting to the server."
       exit 1
    fi    
}
