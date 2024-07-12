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
# See the License for the specific language governing permissions and
# limitations under the License.

#Example of usage:
# ./scrap.sh  -nk <CP_NODE_SSH_KEY name> \
#             -nu <CP_NODE_USER who connect to server> \
#             -cpa <server address name> \
#             -cpt <API_TOKEN> \
#             -o <output directory name>

source get_configmap.sh
source get_pref.sh
source get_services.sh
source get_version.sh
source get_tools.sh
source get_users.sh
source utils.sh

function write_scrap_result() {
  local _data_to_scrap=$1
  local _data_output_file=$2
  local _exit_code=$3
  local _output_dir=$4

  echo "${_data_to_scrap},${_data_output_file},${_exit_code}" >> "${_output_dir}"/_pitc.csv
}

function print_help() {
    echo_info "Scraping existing deployment of the Cloud-Pipeline and store its configuration and settings to the output directory."
    echo_info " * Application version"
    echo_info " * Kubernetes configmap with Cloud-Pipeline related settings (cp-config-global)"
    echo_info " * Cloud-Pipeline services deployed (cp-api-srv, cp-edge, cp-git, etc.)"
    echo_info " * Cloud-Pipeline System Preferences"
    echo_info " * List of tools with its configuration"
    echo_info " * List of registered users"
    echo_info ""
    echo_info "OPTIONS:"
    echo_info "  -o|--output-dir              Directory to write output into"
    echo_info "  -cpa|--cp-api-address        URL to the Cloud-Pipeline REST API. F.i.: https://<deployment-address>/pipeline/restapi/. Environment variable API_URL also can be defined instead of this option."
    echo_info "  -cpt|--cp-api-token          JWT token to use for authentication in Cloud-Pipeline. Environment variable API_TOKEN also can be defined instead of this option."
    echo_info "  -nk|--node-ssh-key           SSH key to user during connection to the Cloud-Pipeline node to scrap kubernetes configuration (services, configmap)"
    echo_info "  -nu|--node-user              (Optional) Username to user during ssh connection to the Cloud-Pipeline node. Default: pipeline"
    echo_info "  -na|--node-address           (Optional) Node address (IP or DNS name) to user during ssh connection to the Cloud-Pipeline node. Default: DNS name for -cpa parameter."
    echo_info "  -kn|--kube-namespace         (Optional) Name of the kubernetes namespace where configmap and Cloud-Pipeline services are located. Default: default"
    echo_info "  -kc|--kube-configmap         (Optional) Name of the kubernetes configmap to save data from. Default: cp-config-global"
    echo_info "  -sc|--stored-configuration   (Optional) Configuration to store. Possible values (combination of): 'system_preferences' 'tools' 'users', split by comma. By default script collects all possible data. Default: system_preferences,tools,users"
    echo_info "  -f|--force                   (Optional) Write output even if output directory isn't empty. Default: disabled"
    echo_info "  -h|--help                    Print this message"
}

CONFIGURATIONS_TO_STORE="system_preferences,tools,users"

UNEXPECTED=()
while [[ $# -gt 0 ]]
    do
    key="$1"

    case $key in
        -sc|--stored-configuration)
        export CONFIGURATIONS_TO_STORE="$2"
        shift # past argument
        shift # past value
        ;;
        -nk|--node-ssh-key)
        export CP_NODE_SSH_KEY="$2"
        shift # past argument
        shift # past value
        ;;
        -nu|--node-user)
        export CP_NODE_USER="$2"
        shift # past argument
        shift # past value
        ;;
        -na|--node-address)
        export CP_NODE_IP="$2"
        shift # past argument
        shift # past value
        ;;
        -cpa|--cp-api-address)
        export API_URL="$2"
        shift # past argument
        shift # past value
        ;;
        -cpt|--cp-api-token)
        export API_TOKEN="$2"
        shift # past argument
        shift # past value
        ;;
        -kn|--kube-namespace)
        export CP_KUBE_NAMESPACE="$2"
        shift # past argument
        shift # past value
        ;;
        -kc|--kube-configmap)
        export CP_KUBE_CONFIGMAP="$2"
        shift # past argument
        shift # past value
        ;;
        -o|--output-dir)
        export OUTPUT_DIR="$2"
        shift # past argument
        shift # past value
        ;;
        -f|--force)
        export FORCE_WRITE=1
        shift # past argument
        shift # past value
        ;;
        -h|--help)
        export PRINT_HELP=1
        shift # past argument
        ;;
        *)
        UNEXPECTED+=("$1") #
        shift # past argument
        ;;
esac        
done

if [ "$PRINT_HELP" == "1" ]; then
    print_help
    exit 0
fi

if [ "${#UNEXPECTED[@]}" -gt 0 ]; then
    echo_warn "There are unexpected arguments: ${UNEXPECTED[@]}"
    echo_warn "Please, see help message for possible options: "
    echo_warn
    print_help
    exit 1
fi

if [ -z "$CP_NODE_SSH_KEY" ]; then
    echo_warn "Please provide -k option with the ssh key to connect to the node with access to the Cloud-Pipeline kubectl cluster."
    exit 1
else
    if [ ! -f "$CP_NODE_SSH_KEY" ]; then
        echo_warn "Can't find ssh key in path $CP_NODE_SSH_KEY. Please check the ssh key location."
        exit 1
    fi
fi

if [ -z "$OUTPUT_DIR" ]; then
    echo_warn "Please provide the output directory with -o option to store Cloud-Pipeline configuration."
    exit 1
fi

if [ -z "$API_URL" ]; then
    echo_warn "Please provide the URL to the Cloud-Pipeline API endpoint by specifying -cpa option or API_URL environment variable."
    exit 1
fi

if [ -z "$API_TOKEN" ]; then
    echo_warn "Please provide JWT Token for the Cloud-Pipeline API endpoint by specifying -cpt option or API_TOKEN environment variable."
    exit 1
fi

if [ -z "$CP_NODE_IP" ]; then
   CP_NODE_IP=$(echo "$API_URL" | grep -oP "(?<=https://)([^/]+)")
fi

if [ -z "$CP_NODE_USER" ]; then
   CP_NODE_USER="pipeline"
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

echo_info "- Retrieving application version from server"
_version_file="$OUTPUT_DIR/version.json"
get_version "$API_URL" "$API_TOKEN" "$_version_file"
write_scrap_result "version" "$(basename $_version_file)" "$?" "$OUTPUT_DIR"

echo_info "- Retrieving list on installed services from server"
_services_file="$OUTPUT_DIR/services.json"
get_services "$CP_NODE_SSH_KEY" "$CP_NODE_USER" "$CP_NODE_IP" "${_services_file}"
write_scrap_result "services" "$(basename ${_services_file})" "$?" "$OUTPUT_DIR"

echo_info "- Retrieving Cloud-Pipeline main configmap from server"
_configmap_file="$OUTPUT_DIR/config.properties"
get_configmap "$CP_NODE_SSH_KEY" "$CP_NODE_USER" "$CP_NODE_IP" "$CP_KUBE_NAMESPACE" "$CP_KUBE_CONFIGMAP" "${_configmap_file}"
write_scrap_result "configmap" "$(basename ${_configmap_file})" "$?" "$OUTPUT_DIR"

if [[ "$CONFIGURATIONS_TO_STORE" =~ "system_preferences" ]]; then
   echo_info "- Retrieving preferences from server"
   _system_pref_file="$OUTPUT_DIR/system-preferences.json"
   get_pref "$API_URL" "$API_TOKEN" "${_system_pref_file}"
   write_scrap_result "system_preference" "$(basename ${_system_pref_file})" "$?" "$OUTPUT_DIR"
fi     

if [[ "$CONFIGURATIONS_TO_STORE" =~ "users" ]]; then
   echo_info "- Retrieving registered users from server"
   _users_file="$OUTPUT_DIR/users.json"
   get_users "$API_URL" "$API_TOKEN" "${_users_file}"
   write_scrap_result "users" "$(basename ${_users_file})" "$?" "$OUTPUT_DIR"
fi   
   
if [[ "$CONFIGURATIONS_TO_STORE" =~ "tools" ]]; then
   echo_info "- Retrieving installed docker tools from server"
   _tools_dir="$OUTPUT_DIR/dockers-manifest"
   get_tools "$API_URL" "$API_TOKEN" "${_tools_dir}"
   write_scrap_result "tools" "$(basename ${_tools_dir})" "$?" "$OUTPUT_DIR"
fi

echo_ok "Cloud-Pipeline point-in-time configuration saved in directory $(realpath $OUTPUT_DIR)"
