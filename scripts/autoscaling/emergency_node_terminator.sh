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

##############################
# Work with CP API
##############################
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


##############################
# Run logging
##############################
ERROR_LOG_LEVEL="ERROR"
INFO_LOG_LEVEL="INFO"
DEBUG_LOG_LEVEL="DEBUG"

function pipe_api_log() {
  _MESSAGE="$1"
  _STATUS="$2"
  if [[ "$RUN_ID" ]] && [[ "$LOG_TASK" ]]
  then
    if [[ "$_STATUS" == "$ERROR_LOG_LEVEL" ]]
    then
      STATUS="FAILURE"
    else
      STATUS="RUNNING"
    fi
    call_api "$_API" "$_API_TOKEN" "run/$RUN_ID/log" "POST" '{
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

function pipe_log_debug() {
  _MESSAGE="$1"
  pipe_log "$_MESSAGE" "$DEBUG_LOG_LEVEL"
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

##############################
# System Preferences
##############################
function get_system_preferences() {
  _API="$1"
  _API_TOKEN="$2"
  call_api "$_API" "$_API_TOKEN" "preferences" "GET" |
    jq -r '.payload[] | .name + "=" + .value' |
    grep -v "^null$"
}

function resolve_system_preference() {
  _PREFERENCES="$1"
  _PREFERENCE="$2"
  _DEFAULT_VALUE="$3"

  NAME_AND_VALUE=$(echo "$_PREFERENCES" | grep "$_PREFERENCE=")
  VALUE="${NAME_AND_VALUE#$_PREFERENCE=}"
  if [[ "$VALUE" ]]
  then
    echo "$VALUE"
  else
    echo "$_DEFAULT_VALUE"
  fi
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

terminate_node() {
  _API="$1"
  _API_TOKEN="$2"
  _NODE="$3"
  call_api "$_API" "$_API_TOKEN" "cluster/node/$_NODE" "DELETE"
}

export API="$1"
export API_TOKEN="$2"
export NODE="$3"
export LOG_TASK="DefunctRunMonitor"

PREFERENCES=$(get_system_preferences "$API" "$API_TOKEN")
MONITORING_DELAY_PREFERENCE="${MONITORING_DELAY_PREFERENCE:-cluster.instance.defunct.container.monitoring.delay}"
MONITORING_DELAY_PREFERENCE_DEFAULT="${MONITORING_DELAY_PREFERENCE_DEFAULT:-300}"
MONITORING_DELAY=$(resolve_system_preference "$PREFERENCES" "$MONITORING_DELAY_PREFERENCE" "$MONITORING_DELAY_PREFERENCE_DEFAULT")

pipe_log_debug "Starting defunct container monitoring $NODE..."
while true
do
  sleep "$MONITORING_DELAY"
  pipe_log_debug "Start monitoring cycle for $NODE..."
  docker ps --format '{{.ID}} {{.Image}}' | while read container_id image_name; do
      defunct=$(sudo docker top $container_id | awk 'NR>1 {print $0}' | grep "<defunct>" | wc -l )
      non_defunct=$(sudo docker top $container_id | awk 'NR>1 {print $0}' | grep -v "<defunct>" | wc -l )
      if [ "$defunct" -gt 0 ] && [ "${non_defunct}" -eq 0 ]; then
        pipe_log_debug "Found zombie container container_id=${container_id} image_name=${image_name}."
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
          terminate_node "$API" "$API_TOKEN" "$NODE"
        fi
      fi
  done
  pipe_log_debug "Finish monitoring cycle for $NODE..."
done
