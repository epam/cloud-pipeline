#!/bin/bash

# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
  _API_METHOD="$1"
  _HTTP_METHOD="$2"
  _HTTP_BODY="$3"
  if [[ "$_HTTP_BODY" ]]
  then
    curl -f -s -k -X "$_HTTP_METHOD" \
      --header 'Accept: application/json' \
      --header 'Authorization: Bearer '"$API_TOKEN" \
      --header 'Content-Type: application/json' \
      --data "$_HTTP_BODY" \
      "$API$_API_METHOD"
  else
    curl -f -s -k -X "$_HTTP_METHOD" \
      --header 'Accept: application/json' \
      --header 'Authorization: Bearer '"$API_TOKEN" \
      --header 'Content-Type: application/json' \
      "$API$_API_METHOD"
  fi
}

function get_event_mark() {
  date -d "$(echo $1 | sed 's/.*\[\([^]]*\)\].*/\1/g')" +%s
}

function get_current_date() {
  date '+%Y-%m-%d %H:%M:%S.%N' | cut -b1-23
}

function pipe_log_debug() {
  _MESSAGE="$1"
  if [[ "$DEBUG" ]]
  then
    echo "$(get_current_date): [DEBUG] $_MESSAGE"
  fi
}

function find_oom_killer_events() {
  if [ "$OOM_EXCLUDE_EVENTS" ]; then
    local _GREP_EXCLUDE="| grep -E -v '$OOM_EXCLUDE_EVENTS'"
  fi
  local _GREP_CMD="dmesg -T | grep -E -i 'killed process' $_GREP_EXCLUDE"
  eval "$_GREP_CMD"
}

function log_oom_killer_events() {
  _LAST_SYNC_MARK="$1"
  find_oom_killer_events | while read -r i
  do
    OOM_KILLER_EVENT_MARK=$(get_event_mark "$i")
    if [[ "$OOM_KILLER_EVENT_MARK" -gt "$_LAST_SYNC_MARK" ]]
    then
      EVENT_MESSAGE=$(echo "$i" | sed -e 's/\[[^][]*\]//g' | xargs)
      pipe_log_warn "[WARN] $EVENT_MESSAGE" "$LOG_TASK"
      if [ "$CP_OOM_TAG_RUNS" == "true" ]; then
        pipe_tag "$CP_OOM_TAG_NAME" "$CP_OOM_TAG_VALUE"
      fi
    fi
  done
}

function get_current_run_id() {
  _NODE="$1"
  call_api "cluster/node/$_NODE/run" "GET" |
    jq -r ".payload.runId" |
    grep -v "^null$"
}

function get_preference() {
  _PREFERENCE_NAME="$1"
  call_api "preferences/$_PREFERENCE_NAME" "GET" |
    jq -r ".payload.value" |
    grep -v "^null$"
}

while [[ "$#" -gt "0" ]]
do
  case "$1" in
  -u | --api-url)
    API="$2"
    shift
    shift
    ;;
  -t | --api-token)
    API_TOKEN="$2"
    shift
    shift
    ;;
  -n | --node-name)
    NODE="$2"
    shift
    shift
    ;;
  -d | --monitoring-delay)
    MONITORING_DELAY="$2"
    shift
    shift
    ;;
  -f | --sync-file)
    SYNC_FILE="$2"
    shift
    shift
    ;;
  -e | --debug)
    DEBUG="true"
    shift
    ;;
  *)
    pipe_log_debug "Unexpected argument $1 will be skipped."
    shift
    ;;
  esac
done

if [[ -z "$API" ]] || [[ -z "$API_TOKEN" ]] || [[ -z "$NODE" ]] || [[ -z "$SYNC_FILE" ]]
then
  if [[ -z "$API" ]]; then
    echo "Missing required argument --api-url"
  fi
  if [ -z "$API_TOKEN" ]; then
      echo "Missing required argument --api-token"
  fi
  if [ -z "$NODE" ]; then
      echo "Missing required argument --node-name"
  fi
  if [ -z "$SYNC_FILE" ]; then
      echo "Missing required argument --sync-file"
  fi
  exit 1
fi

export API
export API_TOKEN

LOG_TASK="${LOG_TASK:-OOM Logs}"
MONITORING_DELAY="${MONITORING_DELAY:-10}"
CP_OOM_TAG_RUNS="${CP_OOM_TAG_RUNS:-false}"
CP_OOM_TAG_NAME="${CP_OOM_TAG_NAME:-PROC_OUT_OF_MEMORY}"
CP_OOM_TAG_VALUE="${CP_OOM_TAG_VALUE:-true}"

LAST_SYNC_MARK=0
if [[ -s "$SYNC_FILE" ]]; then
    LAST_SYNC_MARK=$(cat "$SYNC_FILE")
fi

pipe_log_debug "Starting monitoring service process for node $NODE..."

pipe_log_debug "Getting OOM logger preferences"
export OOM_EXCLUDE_EVENTS=$(get_preference "system.oom.exclude.events")
pipe_log_debug "  -> system.oom.exclude.events: $OOM_EXCLUDE_EVENTS"

while true
do
  sleep "$MONITORING_DELAY"
  LAST_OOM_KILLER_EVENT=$(find_oom_killer_events | tail -1)
  if [[ -z "$LAST_OOM_KILLER_EVENT" ]]
  then
    # No OOM killer events found
    continue
  fi
  LAST_EVENT_MARK=$(get_event_mark "$LAST_OOM_KILLER_EVENT")
  if [[ "$LAST_SYNC_MARK" -gt "$LAST_EVENT_MARK" ]]
  then
    # No new OOM killer events found
    continue
  fi
  RUN_ID=$(get_current_run_id "$NODE")
  if [[ -z "$RUN_ID" ]]
  then
    pipe_log_debug "No run is assigned to the node."
    continue
  fi
  if ! [[ "$RUN_ID" =~ ^[0-9]+$ ]]
  then
    pipe_log_debug "The run ID $RUN_ID is not a number. Processing will be skipped."
    continue
  fi
  export RUN_ID
  log_oom_killer_events "$LAST_SYNC_MARK"
  LAST_SYNC_MARK="$LAST_EVENT_MARK"
  echo "$LAST_SYNC_MARK" > "$SYNC_FILE"
done
