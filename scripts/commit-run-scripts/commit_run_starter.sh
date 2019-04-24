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

download_file() {
    local _FILE_URL=$1
    wget -q --no-check-certificate -O ${_FILE_URL} 2>/dev/null || curl -s -k -O ${_FILE_URL}
    _DOWNLOAD_RESULT=$?
    return "$_DOWNLOAD_RESULT"
}

export API=$1
export API_TOKEN=$2
COMMIT_DISTRIBUTION_URL=$3
DISTRIBUTION_URL=$4
export RUN_ID=$5
export CONTAINER_ID=$6
export CLEAN_UP=$7
export STOP_PIPELINE=$8
TIMEOUT=$9
export REGISTRY_TO_PUSH=${10}
export REGISTRY_TO_PUSH_ID=${11}
export TOOL_GROUP_ID=${12}
export NEW_IMAGE_NAME=${13}

export SCRIPTS_DIR="$(pwd)/commit-run-scripts"
export COMMON_REPO_DIR="$(pwd)/pipe-common"

export FULL_NEW_IMAGE_NAME="${REGISTRY_TO_PUSH}/${NEW_IMAGE_NAME}"
export CP_PYTHON2_PATH=python

if [[ "$#" -ge 15 ]]; then
    export DOCKER_LOGIN=${14}
    export DOCKER_PASSWORD=${15}
fi

if [[ "$#" -ge 16 ]]; then
    export IS_PIPELINE_AUTH=${16}
fi

export PRE_COMMIT_COMMAND=${17}
export POST_COMMIT_COMMAND=${18}

export TASK_NAME="CommitPipelineRun"

export RUNS_ROOT='/runs'

mkdir $SCRIPTS_DIR

_DOWNLOAD_RESULT=0
cd $SCRIPTS_DIR
download_file "${COMMIT_DISTRIBUTION_URL}commit_run.sh" && \
download_file "${COMMIT_DISTRIBUTION_URL}cleanup_container.sh" && \
download_file "${COMMIT_DISTRIBUTION_URL}common_commit_initialization.sh"

_DOWNLOAD_RESULT=$?
if [ "$_DOWNLOAD_RESULT" -ne 0 ];
then
    pipe_log_fail "[ERROR] Commit scripts download failed. Exiting." $TASK_NAME
    echo "[ERROR] Commit scripts download failed. Exiting"
    exit "$_DOWNLOAD_RESULT"
fi
chmod +x $SCRIPTS_DIR/*

. $SCRIPTS_DIR/common_commit_initialization.sh

install_pip

mkdir $COMMON_REPO_DIR
cd $COMMON_REPO_DIR
install_pipeline_code

# Verify container size and warn user befor committing
# TODO: Fail a job if not enough disk space
# TODO: pass MAX_CONTAINER_SIZE_GB from outside
MAX_CONTAINER_SIZE_GB=3
CONTAINER_SIZE_B=$(docker inspect ${CONTAINER_ID} -s --format "{{.SizeRw}}")  # Bytes
CONTAINER_SIZE_GB=$(( ((CONTAINER_SIZE_B / 1024) / 1024) / 1024 ))            # Gb

if (( CONTAINER_SIZE_GB > MAX_CONTAINER_SIZE_GB )); then
    pipe_log_warn "[WARN] Committing current container will result into ~${CONTAINER_SIZE_GB}Gb (${CONTAINER_SIZE_B}b) of data. This may cause issues while pushing this docker layer to the registry. Max ${MAX_CONTAINER_SIZE_GB} is suggested to be pushed" "$TASK_NAME"
fi
#

$SCRIPTS_DIR/commit_run.sh & kill_on_timeout "Commit"