#!/bin/bash

# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

function _log {
  echo [$(date '+%Y-%m-%d %H:%M:%S')] "$1"
}

function call_api {
  API_METHOD="$1"
  HTTP_METHOD="$2"
  HTTP_BODY="$3"
  local response=""
  if [[ "$HTTP_BODY" ]]
  then
    response=$(curl -s -k -X "$HTTP_METHOD" \
      --header 'Authorization: Bearer '"$CUSTOM_ENV_API_TOKEN" \
      --header 'Content-Type: application/json' \
      --data "$HTTP_BODY" \
      "$CUSTOM_ENV_API/$API_METHOD")
  else
    response=$(curl -s -k -X "$HTTP_METHOD" \
      --header 'Authorization: Bearer '"$CUSTOM_ENV_API_TOKEN" \
      "$CUSTOM_ENV_API/$API_METHOD")
  fi
  echo "$response"
}

function check_api_response_status {
  local response_json="$1"
  local response_status=$(echo "$response_json" | grep -Po '"status":.*?[^\\]"'|awk -F':' '{print $2}'| cut -d'"' -f 2)
  local result=0
  if [ "$response_status" == "ERROR" ] || [[ "$response_status" == "40"* ]]; then
      let result=1
  fi
  return $result
}

_log "Running run stage"

_CP_GITLAB_RUNNER_STAGE_SCRIPT="$1"
if [ -z "$_CP_GITLAB_RUNNER_STAGE_SCRIPT" ] || [ ! -f "$_CP_GITLAB_RUNNER_STAGE_SCRIPT" ]; then
  _log "Stage build script is not provided as a first argument or a corresponding file does not exist, exiting"
  exit 1
fi

# Wait for 5 min to grab the SSH credentials
ATTEMPTS=60
for ATTEMPT in $(seq 1 "$ATTEMPTS") ; do
  payload_filter="{}"
read -r -d '' payload_filter <<-EOF
{
   "page": 1,
   "pageSize": 10,
   "partialParameters": "GITLAB_PROJECT:$CUSTOM_ENV_CI_PROJECT_NAME:GITLAB_BUILD_ID:$CUSTOM_ENV_CI_BUILD_ID:GITLAB_COMMIT_SHA:$CUSTOM_ENV_CI_COMMIT_SHA"
}
EOF
  GET_RUN_RESPONSE=$(call_api "run/filter?loadLinks=true" "POST" "$payload_filter")
  check_api_response_status "$GET_RUN_RESPONSE"
  get_result=$?
  if [ $get_result -ne 0 ]; then
    _log "[WARNING] Try #${ATTEMPT}. Failed to fetch run by GITLAB_PROJECT:$CUSTOM_ENV_CI_PROJECT_NAME:GITLAB_BUILD_ID:$CUSTOM_ENV_CI_BUILD_ID:GITLAB_COMMIT_SHA:$CUSTOM_ENV_CI_COMMIT_SHA"
    sleep 5
    continue
  fi

  RUN_ID=$(echo "$GET_RUN_RESPONSE" | jq -r 'first(.[].elements[]).id')
  POD_IP=$(echo "$GET_RUN_RESPONSE" | jq -r 'first(.[].elements[]).podIP')
  _log "Run id: $RUN_ID"
  _log "Pod ip: $POD_IP"

  GET_RUN_RESPONSE=$(call_api "run/${RUN_ID}" "GET")
  check_api_response_status "$GET_RUN_RESPONSE"
  get_result=$?
  if [ $get_result -ne 0 ]; then
    _log "[WARNING] Try #${ATTEMPT}. Failed to fetch run by id: $RUN_ID"
    sleep 5
    continue
  else
    SSH_PASSWORD=$(echo "$GET_RUN_RESPONSE" | jq -r '.payload.sshPassword')
    break
  fi
  _log "[ERROR] Failed to fetch run $RUN_ID"
  exit 1
done

sshpass -p $SSH_PASSWORD scp -o StrictHostKeyChecking=no -o GlobalKnownHostsFile=/dev/null -o UserKnownHostsFile=/dev/null -q $_CP_GITLAB_RUNNER_STAGE_SCRIPT root@$POD_IP:/tmp/run.sh
sshpass -p $SSH_PASSWORD ssh -T -o StrictHostKeyChecking=no -o GlobalKnownHostsFile=/dev/null -o UserKnownHostsFile=/dev/null -q root@$POD_IP '/bin/bash /tmp/run.sh'
