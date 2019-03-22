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

DEMULTIPLEX_TASK_NAME="Demultiplex"
MERGE_TASK_NAME="MergeFastq"
COPY_TASK_NAME="CopyFiles"

# Folder optionally holding already demultiplexed Fastqs for MiSeq machines
MISEQ_FASTQ_DIR="Data/Intensities/BaseCalls"

function run_bcl2fastq {
    local _RUN_FOLDER=$1
    local _OUTPUT_FOLDER=$2
    local _SAMPLE_SHEET=$3

    pipe_exec "bcl2fastq --runfolder-dir $_RUN_FOLDER --output-dir $_OUTPUT_FOLDER \
     --no-lane-splitting --sample-sheet $_SAMPLE_SHEET" "${DEMULTIPLEX_TASK_NAME}"
    result=$?
    if [[ ${result} -ne 0 ]];
    then
        pipe_log_fail "Bcl2Fastq execution failed wih exit code ${result}." "${DEMULTIPLEX_TASK_NAME}"
        exit ${result}
    else
        pipe_log_success "Bcl2Fastq finished successfully." "${DEMULTIPLEX_TASK_NAME}"
    fi
}

function merge_reads {
    local _FASTQ_DIR=$1
    local _SAMPLE=$2
    local _OUTPUT_DIR=$3
    local _READ_SUFFIX=$4

    READ_MERGED="${_OUTPUT_DIR}/${_SAMPLE}_${_READ_SUFFIX}.fastq.gz"
    SAMPLE_FILES=$(find "${_FASTQ_DIR}" -name "${_SAMPLE}_*_${_READ_SUFFIX}_*.fastq.gz")
    SAMPLE_CMD=""
    while read -r SAMPLE_FILE; do
        pipe_log_info "Found read ${_READ_SUFFIX} file for sample ${_SAMPLE}: ${SAMPLE_FILE}" "${MERGE_TASK_NAME}"
        SAMPLE_CMD="${SAMPLE_CMD} ${SAMPLE_FILE}"
    done <<< "${SAMPLE_FILES}"

    cat ${SAMPLE_CMD} > "${READ_MERGED}"
    pipe_log_info "Writing merged R1 fastq for ${_SAMPLE} into file ${READ_MERGED}." "${MERGE_TASK_NAME}"
}

function merge_sample {
    local _FASTQ_DIR=$1
    local _SAMPLE=$2
    local _OUTPUT_DIR=$3
    pipe_log_info "Collecting fastq files for sample ${_SAMPLE}." "${MERGE_TASK_NAME}"

    merge_reads "${_FASTQ_DIR}" "${_SAMPLE}" "${_OUTPUT_DIR}" "R1"
    merge_reads "${_FASTQ_DIR}" "${_SAMPLE}" "${_OUTPUT_DIR}" "R2"

    pipe_log_info "Finished fastq files merge for sample ${_SAMPLE}." "${MERGE_TASK_NAME}"
}

function merge_fastq {
    local _FASTQ_DIR=$1
    local _SAMPLE_SHEET=$2
    local _OUTPUT_DIR=$3

    pipe_log_info "Reading sample sheet file ${_SAMPLE_SHEET}." "${MERGE_TASK_NAME}"

    pipe_log_info "Starting fastq merge in folder ${_FASTQ_DIR}." "${MERGE_TASK_NAME}"

    mkdir -p "${_OUTPUT_DIR}"

    SAMPLES=$(${CP_PYTHON2_PATH} ${SCRIPTS_DIR}/src/read_samples.py --sample-sheet ${_SAMPLE_SHEET})
    while read -r SAMPLE; do
        merge_sample ${_FASTQ_DIR} ${SAMPLE} ${_OUTPUT_DIR}
    done <<< "${SAMPLES}"
    pipe_log_success "Successfully finished fastq merge in folder ${_FASTQ_DIR}." "${MERGE_TASK_NAME}"
}

function copy_additional_files {
    local _SAMPLE_SHEET=$1
    local _OUTPUT_DIR=$2
    local _LOCAL_FASTQ_DIR=$3
    local _FASTQ_OUTPUT_DIR=$4

    pipe_log_info "Transferring additional files" "${COPY_TASK_NAME}"

    pipe_log_info "Copying sample sheet file" "${COPY_TASK_NAME}"
    cp "${_SAMPLE_SHEET}" "${_OUTPUT_DIR}"

    pipe_log_info "Copying InterOp folder" "${COPY_TASK_NAME}"
    mkdir -p "${_OUTPUT_DIR}InterOp"
    cp ${MACHINE_RUN_FOLDER}/InterOp/* "${_OUTPUT_DIR}InterOp/"

    pipe_log_info "Copying undertemined files" "${COPY_TASK_NAME}"
    find "${_LOCAL_FASTQ_DIR}" -name Undetermined_*.fastq.gz | xargs cp -t "${_FASTQ_OUTPUT_DIR}"

    pipe_log_success "Successfully transferred all additional files" "${COPY_TASK_NAME}"

}

pipe_log_info "Starting demultiplex for machine run folder: ${MACHINE_RUN_FOLDER}" "${DEMULTIPLEX_TASK_NAME}"

# Check whether fastq files are already present in machine run folder

LOCAL_FASTQ_DIR="${MACHINE_RUN_FOLDER}/${MISEQ_FASTQ_DIR}"
# Use default sample sheet if it is not set explicitly
SAMPLE_SHEET="${SAMPLE_SHEET:=${MACHINE_RUN_FOLDER}/SampleSheet.csv}"

if [[ -n $(find "${MACHINE_RUN_FOLDER}/${MISEQ_FASTQ_DIR}" -name '*.fastq.gz') ]];
then
    pipe_log_success "Found demultiplexed fastq files in ${MACHINE_RUN_FOLDER}/${MISEQ_FASTQ_DIR} folder. \nSkipping bcl2fastq run." "${DEMULTIPLEX_TASK_NAME}"

else
    pipe_log_info "No fastq files found in ${MACHINE_RUN_FOLDER}/${MISEQ_FASTQ_DIR} folder. \nStarting bcl2fastq run."  "${DEMULTIPLEX_TASK_NAME}"
    run_bcl2fastq "${MACHINE_RUN_FOLDER}" "${LOCAL_FASTQ_DIR}" "${SAMPLE_SHEET}"
fi

OUTPUT_DIR="${ANALYSIS_DIR}${MACHINE_RUN_FOLDER#${INPUT_DIR}}/PipelineInputData/"
FASTQ_OUTPUT_DIR="${OUTPUT_DIR}FASTQ/"

merge_fastq "${LOCAL_FASTQ_DIR}" "${SAMPLE_SHEET}" "${FASTQ_OUTPUT_DIR}"

# Copy Additional files: Undertermined fastq, SampleSheet, Interop folder
copy_additional_files "${SAMPLE_SHEET}" "${OUTPUT_DIR}" "${LOCAL_FASTQ_DIR}" "${FASTQ_OUTPUT_DIR}"