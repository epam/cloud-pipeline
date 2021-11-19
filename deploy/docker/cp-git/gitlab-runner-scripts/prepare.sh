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

_log "Running prepare stage"

if [ -z "$CUSTOM_ENV_API_TOKEN" ] || [ -z "$CUSTOM_ENV_API" ]; then
  _log "[ERROR] One of the environment variables API or API_TOKEN is not set via Secret Variables. Exiting"
  exit 1
fi

if [ -z "$CUSTOM_ENV_DOCKER_IMAGE" ]; then
  _log "[ERROR] Run docker image is not set in .gitlab-ci.yml file. Exiting"
  exit 1
fi

if [ -z "$CUSTOM_ENV_INSTANCE_TYPE" ]; then
  _log "[ERROR] Run instance type is not set in .gitlab-ci.yml file. Exiting"
  exit 1
fi

# Will wait 15 min for new run to initialize
CUSTOM_ENV_NODE_WAIT_INTERVAL_SEC=${CUSTOM_ENV_NODE_WAIT_INTERVAL_SEC:-20}
CUSTOM_ENV_NODE_WAIT_RETRY_COUNT=${CUSTOM_ENV_NODE_WAIT_RETRY_COUNT:-45}
# Do not allow to run for more than 4 hours and allow to kill the hanging runs
CUSTOM_ENV_BUILD_RUN_TIMEOUT_MIN=${CUSTOM_ENV_BUILD_RUN_TIMEOUT_MIN:-240}

export API_TOKEN=$CUSTOM_ENV_API_TOKEN
export API=$CUSTOM_ENV_API
RUN_RESULT=$(pipe run -y -id 50 -it $CUSTOM_ENV_INSTANCE_TYPE -di $CUSTOM_ENV_DOCKER_IMAGE -cmd 'sleep infinity' -t $CUSTOM_ENV_BUILD_RUN_TIMEOUT_MIN $CUSTOM_ENV_BUILD_RUN_PARAMS -- GITLAB_EXECUTOR_ID GITLAB_PROJECT:$CUSTOM_ENV_CI_PROJECT_NAME:GITLAB_BUILD_ID:$CUSTOM_ENV_CI_BUILD_ID:GITLAB_COMMIT_SHA:$CUSTOM_ENV_CI_COMMIT_SHA)
if [ $? -ne 0 ]; then
    _log "[ERROR] Failed to start run. Exiting"
    exit 1
fi

RUN_ID=$(echo $RUN_RESULT | tr -dc '0-9')
_log "Starting run $RUN_ID..."

_log "Waiting for run initialization"
RETRY=0
while [ "$INITIALIZED_STATUS" != "true" ]
do
  GET_RUN_RESPONSE=$(curl -s -k -X GET --header 'Authorization: Bearer '$CUSTOM_ENV_API_TOKEN --header 'Accept: application/json' "$CUSTOM_ENV_API/run/$RUN_ID")
  if [ $? -ne 0 ]; then
    _log "[ERROR] Failed to fetch run. Trying again"
    continue
  fi
  RUN_STATUS=$(echo "$GET_RUN_RESPONSE" | jq -r '.payload.status')
  if [ "$RUN_STATUS" != "RUNNING" ]; then
    _log "[ERROR] Failed to initialize run $RUN_ID. Status is $RUN_STATUS Exiting"
    exit 1
  fi
  INITIALIZED_STATUS=$(echo "$GET_RUN_RESPONSE" | jq -r '.payload.initialized')
  _log "$RETRY. Initialized status: $INITIALIZED_STATUS"
  sleep $CUSTOM_ENV_NODE_WAIT_INTERVAL_SEC
  let RETRY++
  # 15 min wait time
  if [ "$RETRY" == $CUSTOM_ENV_NODE_WAIT_RETRY_COUNT ]; then
        _log "[ERROR] Failed to initialize run $RUN_ID after $RETRY wait retries. Exiting"
        exit 1
  fi
done

_log "Run $RUN_ID was successfully initialized"
