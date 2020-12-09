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

function get_event_mark() {
  date -d "$(echo $1 | sed 's/.*\[\([^]]*\)\].*/\1/g')" +%s
}

function get_current_date() {
  date '+%Y-%m-%d %H:%M:%S.%N' | cut -b1-23
}

pipe_log_debug() {
  _MESSAGE="$1"
  echo "$(get_current_date): [DEBUG] $_MESSAGE"
  if [[ "$DEBUG" ]]
  then
    pipe_log_warn "[DEBUG] $_MESSAGE" "$LOG_TASK"
  fi
}

find_oom_killer_events() {
  dmesg -T | grep -E -i 'killed process'
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
    fi
  done
}

function get_current_run_id() {
  _API="$1"
  _API_TOKEN="$2"
  _NODE="$3"
  call_api "$_API" "$_API_TOKEN" "cluster/node/$_NODE/load" "GET" |
    jq -r ".payload.labels.runid" |
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
  pipe_log_error "Some of the required arguments are missing." "$LOG_TASK"
  exit 1
fi

LOG_TASK="${LOG_TASK:-OOM Logs}"
MONITORING_DELAY="${MONITORING_DELAY:-10}"

LAST_SYNC_MARK=0
if [[ -s "$SYNC_FILE" ]]; then
    LAST_SYNC_MARK=$(cat "$SYNC_FILE")
fi

RUN_ID=$(get_current_run_id "$API" "$API_TOKEN" "$NODE")

pipe_log_debug "Starting monitoring service process for run $RUN_ID..."
while true
do
  sleep "$MONITORING_DELAY"
  if [[ -z "$RUN_ID" ]]
  then
    pipe_log_debug "No run is assigned to the node."
    continue
  fi
  LAST_OOM_KILLER_EVENT=$(find_oom_killer_events | tail -1)
  if [[ -z "$LAST_OOM_KILLER_EVENT" ]]
  then
    # No OOM killer events found
    continue
  fi
  LAST_EVENT_MARK=$(get_event_mark "$LAST_OOM_KILLER_EVENT")
  if [[ "$LAST_SYNC_MARK" -gt "$LAST_EVENT_MARK" ]]
  then
    continue
  fi
  log_oom_killer_events "$LAST_SYNC_MARK"
  LAST_SYNC_MARK="$LAST_EVENT_MARK"
  echo "$LAST_SYNC_MARK" > "$SYNC_FILE"
done
