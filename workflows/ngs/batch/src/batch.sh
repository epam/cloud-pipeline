#!/usr/bin/env bash

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

# Parameters
#SAMPLE SHEET - INPUT
#MACHINE_RUN_FOLDER
#ANALYSIS_FOLDER

#DEMULTIPLEX_PIPELINE
#DEMULTIPLEX_VERSION
#DEMULTIPLEX_INSTANCE
#DEMULTIPLEX_DISK

#ANALYTICAL_PIPELINE
#ANALYTICAL_VERSION
#ANALYTICAL_INSTANCE
#ANALYTICAL_DISK

function run_task {
    local _SCRIPT=$1
    local _PIPELINE=$2
    local _VERSION=$3
    local _INSTANCE_TYPE=$4
    local _INSTANCE_DISK=$5
    local _TASK_NAME=$6

    TASK_CMD="${CP_PYTHON2_PATH} ${_SCRIPT} --pipeline ${_PIPELINE} \
 --version ${_VERSION}  --task ${_TASK_NAME} --instance-type ${_INSTANCE_TYPE} --instance-disk ${_INSTANCE_DISK}"
    pipe_exec "${TASK_CMD}" "${_TASK_NAME}"
    _result=$?
    if [[ ${_result} -ne 0 ]]; then
        pipe_log_error "${_TASK_NAME} processing failed." "${_TASK_NAME}"
        exit 1
    fi
}

DEMULTIPLEX_TASK_NAME="Demultiplex"

pipe_log_info "Starting batch processing. Demultiplexing BCL data ${MACHINE_RUN_FOLDER}" "${DEMULTIPLEX_TASK_NAME}"
pipe_log_info "Starting demultiplexing bcl data in run folder ${MACHINE_RUN_FOLDER}." "${DEMULTIPLEX_TASK_NAME}"
run_task "${SCRIPTS_DIR}/src/run_demultiplex.py" "${DEMULTIPLEX_PIPELINE}" "${DEMULTIPLEX_VERSION}" "${DEMULTIPLEX_INSTANCE}" \
 "${DEMULTIPLEX_DISK}" "${DEMULTIPLEX_TASK_NAME}"
pipe_log_success "Finished demultiplexing bcl data in run folder ${MACHINE_RUN_FOLDER}." "${DEMULTIPLEX_TASK_NAME}"


ANALYSIS_TASK_NAME="AnalyticalPipelines"
pipe_log_info "Launching analytical pipeline for samples specified in ${SAMPLE_SHEET}" "${ANALYSIS_TASK_NAME}"
run_task "${SCRIPTS_DIR}/src/run_analytical.py" "${ANALYTICAL_PIPELINE}" "${ANALYTICAL_VERSION}" "${ANALYTICAL_INSTANCE}" \
 "${ANALYTICAL_DISK}" "${ANALYSIS_TASK_NAME}"

pipe_log_success "Finished analytical processing." "${ANALYSIS_TASK_NAME}"