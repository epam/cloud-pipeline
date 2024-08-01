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
# Work with local commands
##############################
check_last_exit_code() {
   exit_code=$1
   msg_if_fail=$2
   msg_if_success=$3

   if [[ "$#" -ge 4 ]]; then
       cmd_on_failure=${@:4}
   fi

   if [[ "$exit_code" -ne 0 ]]; then
        pipe_log_error "$msg_if_fail"

        if [[ ${cmd_on_failure} ]]; then
           eval "${cmd_on_failure}"
        fi
        exit 1
    else
        pipe_log_info "$msg_if_success"
    fi
}

run_command_and_check_result() {
   local _RUN_COMMAND=$1
   local _MSG_IF_FAIL=$2
   local _MSG_IF_SUCCESS=$3

   if [[ "$#" -eq 4 ]]; then
       _CMD_ON_FAILURE=$4
   fi

   local _RESULT=`${_RUN_COMMAND}`
   exit_code=$?
   if [[ "$exit_code" -ne 0 ]]; then
        pipe_log_error "$_MSG_IF_FAIL"

        if [[ ${_CMD_ON_FAILURE} ]]; then
           eval ${_CMD_ON_FAILURE}
        fi
        exit 1
    else
        pipe_log_info "$_MSG_IF_SUCCESS"
        echo $_RESULT
    fi
}

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
