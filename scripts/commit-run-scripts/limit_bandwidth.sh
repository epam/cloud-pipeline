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

function call_api() {
  _API="$1"
  _API_TOKEN="$2"
  _API_METHOD="$3"
  _HTTP_METHOD="$4"
  _HTTP_BODY="$5"
  if [[ "$_HTTP_BODY" ]]
  then
    curl -f -s -k -X "$_HTTP_METHOD" \
      --header 'Accept: application/json' \
      --header 'Authorization: Bearer '"$_API_TOKEN" \
      --header 'Content-Type: application/json' \
      --data "$_HTTP_BODY" \
      "$_API$_API_METHOD"
  else
    curl -f -s -k -X "$_HTTP_METHOD" \
      --header 'Accept: application/json' \
      --header 'Authorization: Bearer '"$_API_TOKEN" \
      --header 'Content-Type: application/json' \
      "$_API$_API_METHOD"
  fi
}

ERROR_LOG_LEVEL="ERROR"
INFO_LOG_LEVEL="INFO"
DEBUG_LOG_LEVEL="DEBUG"

function pipe_api_log() {
  _MESSAGE="$1"
  _STATUS="$2"
  if [ "$RUN_ID" ] && [ "$LOG_TASK" ]; then
    if [ "$_STATUS" == "$ERROR_LOG_LEVEL" ]; then
      STATUS="FAILURE"
    else
      STATUS="RUNNING"
    fi
    call_api "$API" "$API_TOKEN" "run/$RUN_ID/log" "POST" '{
        "date": "'"$(get_current_date)"'",
        "logText": "'"$_MESSAGE"'",
        "runId": '"$RUN_ID"',
        "status": "'"$STATUS"'",
        "taskName": "'"$LOG_TASK"'"
      }'
  fi
}

function get_current_date() {
  date '+%Y-%m-%d %H:%M:%S.%N' | cut -b1-23
}

function pipe_log_info() {
  _MESSAGE="$1"
  pipe_log "$_MESSAGE" "$INFO_LOG_LEVEL"
}

function pipe_log_error() {
  _MESSAGE="$1"
  pipe_log "$_MESSAGE" "$ERROR_LOG_LEVEL"
}

function pipe_log() {
  _MESSAGE="$1"
  _STATUS="$2"
  echo "$(get_current_date): [$_STATUS] $_MESSAGE"
  if [[ "$DEBUG" ]] || [[ "$_STATUS" != "$DEBUG_LOG_LEVEL" ]]
  then
    pipe_api_log "$_MESSAGE" "$_STATUS"
  fi
}

export LOG_TASK="LimitNetworkBandwidth"

export RUN_ID="$1"
export API="$2"
export API_TOKEN="$3"
container_id="$4"
enable="$5"
upload_rate="$6"
download_rate="$7"

COMMAND="/bin/wondershaper/wondershaper"

if [ -f "$COMMAND" ]; then
  pipe_log_info "[INFO] ${COMMAND} exists. Proceeding."
else
  pipe_log_info "[WARN] No ${COMMAND} found. Trying to install."

  cd /bin && \
  wget https://github.com/magnific0/wondershaper/archive/refs/heads/master.zip -O wondershaper.zip && \
  unzip wondershaper.zip && \
  mv wondershaper-master wondershaper && \
  rm -rf wondershaper.zip && \
  cd wondershaper && \
  sudo make install

  if [ -f "$COMMAND" ]; then
    pipe_log_info "[INFO] ${COMMAND} installed successfully. Proceeding."
  else
    pipe_log_error "[ERROR] Fail to install wondershaper. Exiting."
    exit 1
  fi
fi

interfaces=($(sudo docker exec "$container_id" ls /sys/class/net))
if [ "$?" -eq 0 ]; then
  pipe_log_info "[INFO] Network bandwidth limitation will be applied."
else
  pipe_log_error "[ERROR] No interfaces found. Network bandwidth limitation won't be applied."
  exit 1
fi

for i in "${interfaces[@]}"; do
    if [ "$i" == "lo" ] || [ -z "$i" ]; then
      continue
    fi

    link_num=$(sudo docker exec $container_id cat /sys/class/net/$i/iflink | tr -d '\r')
    interface_id=$(sudo ip ad | grep "^$link_num:" | awk -F ': ' '{print $2}' | awk -F '@' '{print $1}')
    if [ ! -z "$interface_id" ]; then
      if [ $enable == "true" ]; then
        pipe_log_info "[INFO] Clearing the old limit for run network interface $i and its node network interface $interface_id, if it exists"
        "$COMMAND" -c -a "$interface_id"
        pipe_log_info "[INFO] Enabling limit for run network interface $i and its node network interface $interface_id"
        # download_rate and upload_rate in kilobits per second
        result=$("$COMMAND" -a "$interface_id" -d "$download_rate" -u "$upload_rate")
        if [ "$?" -ne 0 ]; then
          pipe_log_error "[WARN] Failed to apply bandwidth limiting"
          pipe_log_error "[WARN] ${result}"
          exit 1
        else
          pipe_log_info "[INFO] Bandwidth limiting -d $download_rate -u $upload_rate (Kbit/sec) applied successfully"
        fi
      else
        pipe_log_info "[INFO] Clearing limit for run network interface $i and its node network interface $interface_id"
        result=$("$COMMAND" -c -a "$interface_id")
        result_code=$?
        if [ "$result_code" -ne 0 ] && [ "$result_code" -ne 2 ]; then
          pipe_log_error "[WARN] Failed to clear bandwidth limitation"
          pipe_log_error "[WARN] Output: ${result}"
          exit 1
        else
          pipe_log_info "[INFO] Bandwidth limitation cleared"
        fi
      fi
    else
      pipe_log_info "[WARN] Interface id wasn't found for "$i" interface"
    fi
done
