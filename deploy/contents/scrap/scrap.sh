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

#This script connects and gather Kubernetes ConfigMap, Cloud-Pipeline preferences, List of the installed cp services and installed pipectl version
# from provided server address and (optionally) IP of the Kubernetes Node.

#Example of usage ./scrap.sh  -k <CP_NODE_SSH_KEY name>  -u <CP_NODE_USER who connect to server> -s <server adress name> -t <API_TOKEN> -o <output directory name>

source get_configmap.sh
source get_pref.sh
source get_services.sh
source get_version.sh
source get_tools.sh
source utils.sh

function write_scrap_result() {
  local _data_to_scrap=$1
  local _exit_code=$2
  local _output_dir=$3

  if [ "${_exit_code}" -eq 0 ]; then
      echo "${_data_to_scrap},0" >> "${_output_dir}"/revision_metadata.csv
  else
      echo "${_data_to_scrap},${_exit_code}" >> "${_output_dir}"/revision_metadata.csv
  fi
}


while [[ $# -gt 0 ]]
    do
    key="$1"

    case $key in
        -k)
        export CP_NODE_SSH_KEY="$2"
        shift # past argument
        shift # past value
        ;;
        -u)
        export CP_NODE_USER="$2"
        shift # past argument
        shift # past value
        ;;
        -s)
        export API_URL="$2"
        shift # past argument
        shift # past value
        ;;
        -i)
        export CP_NODE_IP="$2"
        shift # past argument
        shift # past value
        ;;
        -t)
        export API_TOKEN="$2"
        shift # past argument
        shift # past value
        ;;
        -n)
        export CP_KUBE_NAMESPACE="$2"
        shift # past argument
        shift # past value
        ;;
        -c)
        export CP_KUBE_CONFIGMAP="$2"
        shift # past argument
        shift # past value
        ;;
        -o)
        export OUTPUT_DIR="$2"
        shift # past argument
        shift # past value
        ;;
        -f|--force)
        export FORCE_WRITE=1
        shift # past argument
        shift # past value
        ;;
esac        
done

if [ -z "$CP_NODE_SSH_KEY" ]; then
    echo_warn "Please provide -k option with the ssh key to connect to the node with access to the Cloud-Pipeline kubectl cluster."
    exit 1
else
    if [ ! -f "$CP_NODE_SSH_KEY" ]; then
        echo_warn "Can't find ssh key in path $CP_NODE_SSH_KEY. Please check the ssh key location."
        exit 1
    fi
fi

if [ -z "$API_URL" ]; then
    echo_warn "Please provide the URL to the Cloud-Pipeline API endpoint by specifying -s option or API_URL environment variable."
    exit 1
fi

if [ -z "$API_TOKEN" ]; then
    echo_warn "Please provide JWT Token for the Cloud-Pipeline API endpoint by specifying -t option or API_TOKEN environment variable."
    exit 1
fi

if [ -z "$CP_NODE_IP" ]; then
   CP_NODE_IP=$(echo "$API_URL" | grep -oP "(?<=https://)([^/]+)")
fi 

if [ -z "$CP_KUBE_CONFIGMAP" ]; then
   CP_KUBE_CONFIGMAP="cp-config-global"
fi

if [ -z "$CP_KUBE_NAMESPACE" ]; then
   CP_KUBE_NAMESPACE="default"
fi

if [ ! -d "$OUTPUT_DIR" ]; then
    echo_warn "Output directory does not exist."
    mkdir -p "$OUTPUT_DIR"
    echo_ok "Output directory $OUTPUT_DIR created."
else
    echo_ok "Output directory exists"
fi

if [ "$(ls -A "$OUTPUT_DIR")" ]; then
   if [ "$FORCE_WRITE" != 1 ]; then
      echo_warn "Output directory is not empty, if you still want to write there please use -f or --force flags."
      exit 1
   fi
fi

echo_info "- Retrieving Cloud-Pipeline main configmap from server"
get_configmap "$CP_NODE_SSH_KEY" "$CP_NODE_USER" "$CP_NODE_IP" "$CP_KUBE_NAMESPACE" "$CP_KUBE_CONFIGMAP" "$OUTPUT_DIR"
write_scrap_result "configmap" "$?" "$OUTPUT_DIR"

echo_info "- Retrieving preferences from server"
get_pref "$API_URL" "$API_TOKEN" "$OUTPUT_DIR"
write_scrap_result "system_preference" "$?" "$OUTPUT_DIR"

echo_info "- Retrieving list on installed services from server"
get_services "$CP_NODE_SSH_KEY" "$CP_NODE_USER" "$CP_NODE_IP" "$OUTPUT_DIR"
write_scrap_result "app_services" "$?" "$OUTPUT_DIR"

echo_info "- Retrieving installed pipectl version from server"
get_version "$API_URL" "$API_TOKEN" "$OUTPUT_DIR"
write_scrap_result "app_version" "$?" "$OUTPUT_DIR"

echo_info "- Retrieving installed docker tools from server"
get_tools "$API_URL" "$API_TOKEN" "$OUTPUT_DIR"
write_scrap_result "tools" "$?" "$OUTPUT_DIR"

echo_ok "Cloud-Pipeline point-in-time configuration saved in directory $(realpath OUTPUT_DIR)"
