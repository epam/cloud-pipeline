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

check_last_exit_code() {
   exit_code=$1
   msg_if_fail=$2
   msg_if_success=$3

   if [[ "$#" -ge 4 ]]; then
       cmd_on_failure=${@:4}
   fi

   if [[ "$exit_code" -ne 0 ]]; then
        pipe_log_fail "$msg_if_fail" "$TASK_NAME"

        if [[ ${cmd_on_failure} ]]; then
           eval "${cmd_on_failure}"
        fi
        exit 1
    else
        pipe_log_info "$msg_if_success" "$TASK_NAME"
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
        pipe_log_fail "$_MSG_IF_FAIL" "$TASK_NAME"

        if [[ ${_CMD_ON_FAILURE} ]]; then
           eval ${_CMD_ON_FAILURE}
        fi
        exit 1
    else
        pipe_log_info "$_MSG_IF_SUCCESS" "$TASK_NAME"
        echo $_RESULT
    fi
}

commit_file() {
    image_name=$1
    pipe_log_info "[INFO] Committing pipeline's image ..." "$TASK_NAME"

    pipe_exec "docker commit --pause=false ${CONTAINER_ID} $image_name > /dev/null" "$TASK_NAME"
}

install_pip() {
    pip --version
    if [[ "$?" -ne 0 ]]; then
        echo "Installing pip"
        curl "https://bootstrap.pypa.io/get-pip.py" -o "get-pip.py"
        python get-pip.py
    fi
}

install_pipeline_code() {
    export _CP_PIP_EXTRA_ARGS="${_CP_PIP_EXTRA_ARGS} 
                                --index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple
                                --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com"

    echo "Installing pipeline packages and code"
    pip install $_CP_PIP_EXTRA_ARGS -I -q setuptools==44.1.1
    mkdir $COMMON_REPO_DIR
    cd $COMMON_REPO_DIR
    download_file ${DISTRIBUTION_URL}pipe-common.tar.gz
    _DOWNLOAD_RESULT=$?
    if [ "$_DOWNLOAD_RESULT" -ne 0 ];
    then
        echo "[ERROR] Main repository download failed. Exiting."
        exit "$_DOWNLOAD_RESULT"
    fi
    tar xf pipe-common.tar.gz
    pip install $_CP_PIP_EXTRA_ARGS . -q -I
    # Init path for shell scripts from common repository
    chmod +x $COMMON_REPO_DIR/shell/*
    export PATH=$PATH:$COMMON_REPO_DIR/shell
}

kill_on_timeout() {
    pid_to_kill=$!
    description=$1
    pgid_to_kill=$(ps -o pgid= $pid_to_kill | grep -o '[0-9]*')

    echo "[INFO] $description process with PID: $pid_to_kill ($pgid_to_kill) will be killed after timeout: ${TIMEOUT} secs"

    # TODO: Poll pgid, if committed fine - shall not sleep for the whole $TIMEOUT, just return
    sleep ${TIMEOUT}

    kill -0 $pgid_to_kill
    if [[ $? -eq 0 ]]; then
        pipe_log_fail "[ERROR] $description timeout elapsed (${TIMEOUT} sec). $description process is going to be killed" $TASK_NAME

        # TODO: Are we killing a job if a pipeline is stopped?
        # TODO: (Low) Introduce commit cancelation from API, as it can last for hours
        kill -9 -$pgid_to_kill
    fi
}

commit_hook() {
    container_id=${1}
    prefix=${2}
    clean_up=${3}
    stop_pipeline=${4}
    hook_command_path=${5}

    if [[ ! -z "${hook_command_path}" ]]; then
        docker exec ${container_id} test -f ${hook_command_path} > /dev/null 2> /dev/null
        if [[ $? -eq 0 ]]; then
            pipe_log_info "[INFO] Run ${prefix}-commit command from path ${hook_command_path}" "$TASK_NAME"
            pipe_exec "docker exec ${container_id} sh -c '${hook_command_path} ${clean_up} ${stop_pipeline}'" "$TASK_NAME"
            check_last_exit_code $? "[ERROR] There are some troubles while executing ${prefix}-commit script." \
                    "[INFO] ${prefix}-commit operations were successfully performed."
        else
            pipe_log_info "[INFO] ${prefix}-commit script ${hook_command_path} not found" "$TASK_NAME"
        fi
    fi
}
