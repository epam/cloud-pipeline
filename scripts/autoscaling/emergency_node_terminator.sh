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


download_file() {
    local _FILE_URL=$1
    wget -q --no-check-certificate -O "${_FILE_URL}" 2>/dev/null || curl -s -k -O "${_FILE_URL}"
    _DOWNLOAD_RESULT=$?
    return "$_DOWNLOAD_RESULT"
}

get_current_run_id() {
  _API="$1"
  _API_TOKEN="$2"
  _NODE="$3"
  call_api "$_API" "$_API_TOKEN" "cluster/node/$_NODE/run" "GET" |
    jq -r ".payload.runId" |
    grep -v "^null$"
}

get_run_image() {
  _API="$1"
  _API_TOKEN="$2"
  _RUN_ID="$3"
  call_api "$_API" "$_API_TOKEN" "run/$_RUN_ID" "GET" |
    jq -r ".payload.dockerImage" |
    grep -v "^null$"
}

terminate_run() {
  _API="$1"
  _API_TOKEN="$2"
  _RUN_ID="$3"
  call_api "$_API" "$_API_TOKEN" "run/$_RUN_ID" "POST"
}

export API="$1"
export API_TOKEN="$2"
export NODE="$3"
DISTRIBUTION_URL=${API%"/restapi"}

SCRIPTS_DIR="$(pwd)/common"

download_file "${DISTRIBUTION_URL}/common/utils.sh"

_DOWNLOAD_RESULT=$?
if [ "$_DOWNLOAD_RESULT" -ne 0 ]; then
    echo "[ERROR] Helper scripts download failed. Exiting"
    exit "$_DOWNLOAD_RESULT"
fi
chmod +x $SCRIPTS_DIR/* && \
source $SCRIPTS_DIR/utils.sh

docker ps --format '{{.ID}} {{.Image}}' | while read container_id image_name; do
    defunct=$(sudo docker top $container_id | awk 'NR>1 {print $NF}' | grep "<defunct>")
    if [ ! -z "$defunct" ]; then
      RUN_ID=$(get_current_run_id "$API" "$API_TOKEN" "$NODE")
      if [ -z "$RUN_ID" ]; then
        pipe_log_debug "No run is assigned to the node."
        continue
      fi
      run_image=$(get_run_image "$API" "$API_TOKEN" "$RUN_ID" | awk -F ':' '{print $1}')
      if [ -z "$run_image" ]; then
        pipe_log_debug "No image found for "$RUN_ID" run id."
        continue
      fi
      if [ "$image_name" -eq "$run_image" ]; then
        pipe_log_error "Run "$RUN_ID" becomes a zombie and will be forcefully finished."
        terminate_run "$API" "$API_TOKEN" "$RUN_ID"
      fi
    fi
done
