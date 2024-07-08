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
  local ssh_key="$1" #SSH Key for connection
  local user="$2" #User whom connection  
  local node_ip="$3"  #Server address name 
  local namespace="$4" #Kubernetes namespace 
  local configmap_name="$5" #Name of the dowloaded configmap
  local output_dir="${6}/cp-config-global.yaml" #Output directory and file name where configmap will be saved
    
    #SSH connection to the server
    ssh_responce=$(ssh -i $ssh_key -oStrictHostKeyChecking=no $user@$node_ip sudo kubectl get configmap $configmap_name -n $namespace -o yaml) 
    if [ $? -eq 0 ] && [ -n "$ssh_responce" ]; then
       echo "$ssh_responce" > $output_dir 
       echo_ok "config-map from server $node_ip saved in file $output_dir"
    else
       echo_err "Error occured while connecting to the server"
       exit 1
    fi      
}
