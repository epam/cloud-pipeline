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

get_configmap() {
  local CP_NODE_SSH_KEY="$1" # SSH Key for connection
  local CP_NODE_USER="$2" # User for connection
  local CP_NODE_IP="$3"  # Server address
  local CP_KUBE_NAMESPACE="$4" # Kubernetes namespace
  local CP_KUBE_CONFIGMAP="$5" # Name of the configmap to persist
  local output_file="${6}"
    
    #SSH connection to the server
    ssh_responce=$(ssh -i $CP_NODE_SSH_KEY -oStrictHostKeyChecking=no $CP_NODE_USER@$CP_NODE_IP sudo kubectl get configmap $CP_KUBE_CONFIGMAP -n $CP_KUBE_NAMESPACE -o json | jq -r '.data |to_entries[] | "\(.key)=\((.value|sub("^\""; "")|sub("\"$"; "")| "\"" + . + "\""))"') 
    if [ $? -eq 0 ] && [ -n "$ssh_responce" ]; then
       echo "$ssh_responce" > $output_file
       echo_ok "config-map from server $CP_NODE_IP saved in file $output_file"
    else
       echo_err "Error occured while connecting to the server"
       return 1
    fi      
}
