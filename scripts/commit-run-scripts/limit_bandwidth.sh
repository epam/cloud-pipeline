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


export LOG_TASK="LimitNetworkBandwidth"
export RUN_ID="$1"
export API="$2"
export API_TOKEN="$3"
distribution_url="$4"
container_id="$5"
enable="$6"
upload_rate="$7"
download_rate="$8"

COMMAND="/bin/wondershaper"
SCRIPTS_DIR="$(pwd)/commit-run-scripts"

set -x

download_file() {
    local _FILE_URL=$1
    wget -q --no-check-certificate -O ${_FILE_URL} 2>/dev/null || curl -s -k -O ${_FILE_URL}
    _DOWNLOAD_RESULT=$?
    return "$_DOWNLOAD_RESULT"
}

download_file "${distribution_url}common_utils.sh"

_DOWNLOAD_RESULT=$?
if [ "$_DOWNLOAD_RESULT" -ne 0 ];
then
    echo "[ERROR] Helper scripts download failed. Exiting"
    exit "$_DOWNLOAD_RESULT"
fi
chmod +x $SCRIPTS_DIR/* && \
source $SCRIPTS_DIR/common_utils.sh

wondershaper_installed=$([ -f $COMMAND ])
check_last_exit_code "${wondershaper_installed}" "[INFO] ${COMMAND} exists. Proceeding." \
                     "[ERROR] No ${COMMAND} found. Can't limit network bandwidth."

interfaces=$(sudo docker exec -it $container_id ls /sys/class/net | tr -d '\r')
check_last_exit_code $? "[INFO] Network bandwidth limitation will be applied." \
                     "[ERROR] No interfaces found. Network bandwidth limitation won't be applied."

for i in $interfaces
  do
    if [ "$i" != "lo" ]; then
      link_num=$(sudo docker exec -it $container_id cat /sys/class/net/$i/iflink | tr -d '\r')
      interface_id=$(sudo ip ad | grep "^$link_num:" | awk -F ': ' '{print $2}' | awk -F '@' '{print $1}')
      if [ ! -z "$interface_id" ]; then
        if [ $enable == "true" ]; then
          # download_rate and upload_rate in kilobits per second
          result=$("$COMMAND" -a "$interface_id" -d "$download_rate" -u "$upload_rate")
          if [ "$?" -ne 0 ]; then
            pipe_log_error "[WARN] Failed to apply bandwidth limiting"
            pipe_log_error "[WARN] ${result}"
          else
            pipe_log_info "[INFO] Bandwidth limiting -d $download_rate -u $upload_rate applied successfully"
          fi
        else
          result=$("$COMMAND" -c -a "$interface_id")
          if [ "$?" -ne 0 ]; then
            pipe_log_error "[WARN] Failed to clear bandwidth limitation"
            pipe_log_error "[WARN] ${result}"
          else
            pipe_log_info "[INFO] Bandwidth limitation cleared"
          fi
        fi
      else
        pipe_log_error "[WARN] Interface id wasn't found for "$i" interface"
      fi
    fi
done
