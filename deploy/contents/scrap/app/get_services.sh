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
    local CP_NODE_IP="$3" # Server address
    local output_file="${4}"

    #SSH connection to the server
    local node_labels=$(ssh -i $CP_NODE_SSH_KEY -oStrictHostKeyChecking=no $CP_NODE_USER@$CP_NODE_IP sudo kubectl get nodes -o json | jq -r '.items[] | .metadata.labels | to_entries[] | select(.key | startswith("cloud-pipeline/")) | .key | sub("cloud-pipeline/";"") | select(startswith("cp-"))')
    local pod_names=$(ssh -i $CP_NODE_SSH_KEY -oStrictHostKeyChecking=no $CP_NODE_USER@$CP_NODE_IP sudo kubectl get po -o jsonpath='{.items[*].metadata.name}' | tr ' ' '\n')
    declare -A matched_services
    
    #Read labels from the variable and check each pod name for a matching label substring to find matching pods.Save matched labels to array.
    while IFS= read -r label; do
      while IFS= read -r pod_name; do
        if [[ "$pod_name" == *"$label"* ]]; then
            matched_services["$label"]=1
        fi
      done <<< "$pod_names"
    done <<< "$node_labels"

    #If array not empty, save all keys to output  
    if [ "${#matched_services[@]}" -gt 0 ]; then
       for service in $(printf "%s\n" "${!matched_services[@]}" | sort); do
           echo "$service" >> "$output_file"
       done
       echo_ok "list of services from server $CP_NODE_IP saved in file $output_file"
    else
       echo_err "Error occurred while connecting to the server."
       return 1
    fi    
}
