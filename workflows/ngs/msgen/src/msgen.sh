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

MSGEN_TASK_NAME="MicrosoftGenomics"

# Required pipeline parameters:
# CP_GENOMICS_REGION=
# CP_GENOMICS_KEY=
# SAMPLE="sample-1,sample-2"
# FASTQ1="cp://msgen-sample-data/chr21_1.fq.gz,cp://msgen-sample-data/chr21_1.fq.gz"
# FASTQ2="cp://msgen-sample-data/chr21_2.fq.gz,cp://msgen-sample-data/chr21_2.fq.gz"
# REFERENCE="hg19m1"
# PROCESS="snapgatk"
# OUTPUT="cp://msgen-analysis/${RUN_ID}"
#
# Optional pipeline parameters:
# BSQR=
# READ_GROUP=
# EMIT_REF_CONFIDENCE=
# BGZIP=
# CP_TRANSFER_BUCKET=

function get_storage_region_id() {
    local _STORAGE_NAME="$1"

    echo $(${CP_PYTHON2_PATH} -c "import os; from pipeline.api import PipelineAPI; print(PipelineAPI(\"$API\", \"$LOG_DIR\").find_datastorage('$_STORAGE_NAME').region_id)")
}

function expand_vars() {
    local _STRING="$1"

    echo $(${CP_PYTHON2_PATH} -c "import os; print(os.path.expandvars(\"$_STRING\"));")
}

function sample_description() {
    local _SAMPLE="$1"

    echo "sample=$_SAMPLE;run=$RUN_ID"
}

function submit_workflow() {
    local _SAMPLE="$1"
    local _FASTQ1="$2"
    local _FASTQ2="$3"
    local _REFERENCE="$4"
    local _PROCESS="$5"
    local _OUTPUT="$6"
    local _GENOMICS_REGION="$7"
    local _GENOMICS_URL="$8"
    local _GENOMICS_KEY="$9"
    local _BSQR_ENABLED="${10:-true}"
    local _READ_GROUP="${11}"
    local _EMIT_REF_CONFIDENCE="${12:-none}"
    local _BGZIP_ENABLED="${13}"
    local _IGNORE_AZURE_REGION="${14}"

    DESCRIPTION=$(sample_description "$_SAMPLE")
    PROCESS_ARGS="R=$_REFERENCE"
    SYNCHRONOUS="false"

    INPUT_FASTQ1_NAME="${_FASTQ1#*://*/}"
    INPUT_FASTQ2_NAME="${_FASTQ2#*://*/}"
    INPUT_FASTQ1_STORAGE_NAME="${_FASTQ1%/$INPUT_FASTQ1_NAME}"
    INPUT_FASTQ2_STORAGE_NAME="${_FASTQ2%/$INPUT_FASTQ2_NAME}"
    if [[ "$INPUT_FASTQ1_STORAGE_NAME" != "$INPUT_FASTQ2_STORAGE_NAME" ]]
    then
        pipe_log_fail "Input fastq files for $_SAMPLE sample are located in different data storages: $INPUT_FASTQ1_STORAGE_NAME and $INPUT_FASTQ2_STORAGE_NAME. " "$MSGEN_TASK_NAME"
        return 1
    fi
    INPUT_STORAGE_NAME="${_FASTQ1%/$INPUT_FASTQ1_NAME}"
    INPUT_STORAGE_NAME="${INPUT_STORAGE_NAME#*://}"
    INPUT_STORAGE_REGION_ID=$(get_storage_region_id "$INPUT_STORAGE_NAME")
    INPUT_STORAGE_ACCOUNT=$(expand_vars "\$CP_ACCOUNT_ID_$INPUT_STORAGE_REGION_ID")
    INPUT_STORAGE_ACCOUNT_KEY=$(expand_vars "\$CP_ACCOUNT_KEY_$INPUT_STORAGE_REGION_ID")
    INPUT_STORAGE_REGION=$(expand_vars "\$CP_ACCOUNT_REGION_$INPUT_STORAGE_REGION_ID")
    if [[ "$INPUT_STORAGE_REGION" != "$_GENOMICS_REGION" ]]
    then
        pipe_log_warn "Input data storage region for $_SAMPLE sample '$INPUT_STORAGE_REGION' differs with Genomics account region '$_GENOMICS_REGION'." "$MSGEN_TASK_NAME"
        _IGNORE_AZURE_REGION="true"
    fi

    OUTPUT_PATH="${_OUTPUT%/}/"
    OUTPUT_STORAGE_PATH="${OUTPUT_PATH#*://*/}"
    OUTPUT_STORAGE_NAME="${OUTPUT_PATH%/$OUTPUT_STORAGE_PATH}"
    OUTPUT_STORAGE_NAME="${OUTPUT_STORAGE_NAME#*://}"
    OUTPUT_STORAGE_PATH="$OUTPUT_STORAGE_PATH$_SAMPLE/$_SAMPLE"
    OUTPUT_STORAGE_REGION_ID=$(get_storage_region_id "$OUTPUT_STORAGE_NAME")
    OUTPUT_STORAGE_ACCOUNT=$(expand_vars "\$CP_ACCOUNT_ID_$OUTPUT_STORAGE_REGION_ID")
    OUTPUT_STORAGE_ACCOUNT_KEY=$(expand_vars "\$CP_ACCOUNT_KEY_$OUTPUT_STORAGE_REGION_ID")
    OUTPUT_STORAGE_REGION=$(expand_vars "\$CP_ACCOUNT_REGION_$OUTPUT_STORAGE_REGION_ID")
    if [[ "$OUTPUT_STORAGE_REGION" != "$_GENOMICS_REGION" ]]
    then
        pipe_log_warn "Output data storage region for $_SAMPLE sample '$OUTPUT_STORAGE_REGION' differs with Genomics account region '$_GENOMICS_REGION'." "$MSGEN_TASK_NAME"
        _IGNORE_AZURE_REGION="true"
    fi

    # Expected output:
    #
    # Microsoft Genomics command-line client v0.9.0
    # Copyright (c) 2019 Microsoft. All rights reserved.
    # [03/15/2019 17:06:19 - Workflow ID: 10003]: Message: Successfully submitted
    #        Process: snapgatk-20190308_1
    #        Description:
    SUBMIT_COMMAND="msgen submit --api-url $_GENOMICS_URL \
                                 --access-key $_GENOMICS_KEY \
                                 --process-name $_PROCESS \
                                 --process-args $PROCESS_ARGS \
                                 --poll $SYNCHRONOUS \
                                 --bqsr-enabled $_BSQR_ENABLED \
                                 --emit-ref-confidence $_EMIT_REF_CONFIDENCE \
                                 --input-storage-account-name $INPUT_STORAGE_ACCOUNT \
                                 --input-storage-account-key $INPUT_STORAGE_ACCOUNT_KEY \
                                 --input-storage-account-container $INPUT_STORAGE_NAME \
                                 --input-blob-name-1 $INPUT_FASTQ1_NAME \
                                 --input-blob-name-2 $INPUT_FASTQ2_NAME \
                                 --output-storage-account-name $OUTPUT_STORAGE_ACCOUNT \
                                 --output-storage-account-key $OUTPUT_STORAGE_ACCOUNT_KEY \
                                 --output-storage-account-container $OUTPUT_STORAGE_NAME \
                                 --output-filename-base $OUTPUT_STORAGE_PATH \
                                 --ignore-azure-region $_IGNORE_AZURE_REGION \
                                 --description $DESCRIPTION"
    if [[ ! -z "$_READ_GROUP" ]]
    then
        SUBMIT_COMMAND="$SUBMIT_COMMAND --read-group $_READ_GROUP"
    fi
    if [[ ! -z "$_BGZIP_ENABLED" ]]
    then
        SUBMIT_COMMAND="$SUBMIT_COMMAND --bgzip-output $_BGZIP_ENABLED"
    fi
    SUBMIT_OUTPUT=$($SUBMIT_COMMAND)
    WORKFLOW_ID=$(echo "$SUBMIT_OUTPUT" | grep -oP '(?<=Workflow ID: )\w+')

    if [[ -z "$WORKFLOW_ID" ]]
    then
        pipe_log_fail "Microsoft Genomics workflow id for $_SAMPLE sample wasn't found in submit command output." "$MSGEN_TASK_NAME"
        echo "$SUBMIT_OUTPUT"
        return 1
    else
        pipe_log_info "Microsoft Genomics workflow $WORKFLOW_ID has been submitted for $_SAMPLE sample." "$MSGEN_TASK_NAME"
    fi
}

function get_workflow_status_and_message() {
    local _GENOMICS_URL="$1"
    local _GENOMICS_KEY="$2"
    local _WORKFLOW_SAMPLE="$3"

    DESCRIPTION=$(sample_description "$_WORKFLOW_SAMPLE")

    # Expected output:
    #
    # Microsoft Genomics command-line client v0.9.0
    # Copyright (c) 2019 Microsoft. All rights reserved.
    # Workflow List
    # -------------
    # Total Count  : 1
    #
    # Workflow ID     : 10068
    # Status          : Completed successfully
    # Message         :
    # Process         : snapgatk-20190308_1
    # Description     : sample=sample-1;run=29
    # Created Date    : Thu, 28 Mar 2019 18:04:52 GMT
    # End Date        : Thu, 28 Mar 2019 18:26:07 GMT
    # Wall Clock Time : 0h 21m 15s
    # Bases Processed : 1,348,613,600 (1 GBase)
    LIST_OUTPUT=$(msgen list --api-url "$_GENOMICS_URL" \
                             --access-key "$_GENOMICS_KEY" \
                             --with-description "$DESCRIPTION")
    WORKFLOW_STATUS=$(echo "$LIST_OUTPUT" | grep -oP '(?<=Status          : ).*')
    WORKFLOW_MESSAGE=$(echo "$LIST_OUTPUT" | grep -oP '(?<=Message         : ).*')
    echo "$WORKFLOW_STATUS"
    echo "$WORKFLOW_MESSAGE"
}

function check_workflow_finished() {
    local _WORKFLOW_ID="$1"
    local _WORKFLOW_SAMPLE="$2"
    local _OLD_WORKFLOW_STATE="$3"
    local _WORKFLOW_STATUS="$4"
    local _WORKFLOW_MESSAGE="$5"

    if [[ "$_WORKFLOW_STATUS" == "Completed successfully" ]]
    then
        pipe_log_success "Microsoft Genomics workflow $_WORKFLOW_ID has finished successfully." "$_WORKFLOW_SAMPLE"
        return 0
    fi
    if [[ "$_WORKFLOW_STATUS" =~ ^(Failed|Cancelled) ]]
    then
        if [[ -z "$_WORKFLOW_MESSAGE" ]]
        then
            pipe_log_fail "Microsoft Genomics workflow $_WORKFLOW_ID has finished with an error." "$_WORKFLOW_SAMPLE"
        else
            pipe_log_fail "Microsoft Genomics workflow $_WORKFLOW_ID has finished with an error: $_WORKFLOW_MESSAGE" "$_WORKFLOW_SAMPLE"
        fi
        return 0
    fi
    WORKFLOW_STATE="${WORKFLOW_MESSAGE:-$WORKFLOW_STATUS}"
    if [[ "$WORKFLOW_STATE" != "$_OLD_WORKFLOW_STATE" ]]
    then
        pipe_log_info "Microsoft Genomics workflow $_WORKFLOW_ID state has changed: $WORKFLOW_STATE." "$_WORKFLOW_SAMPLE"
    fi
    return 1
}

function check_workflow_finished_successfully() {
    local _WORKFLOW_STATUS="$1"

    if [[ "$_WORKFLOW_STATUS" != "Completed successfully" ]]
    then
        return 1
    fi
}

function upload_inputs_to_azure() {
    local _REMOTE_PATHS_VAR="$1"
    local _TRANSFER_BUCKET="$2"
    UPLOADED_PATHS=$(${CP_PYTHON2_PATH} ${SCRIPTS_DIR}/src/transfer_sources.py --paths-var "$_REMOTE_PATHS_VAR" \
                                                                               --transfer-bucket "$_TRANSFER_BUCKET" \
                                                                               --upload \
                                                                               --task-name "$MSGEN_TASK_NAME")
    if [[ "$?" != "0" ]]
    then
        pipe_log_fail "Microsoft Genomics inputs uploading has failed." "$MSGEN_TASK_NAME"
        exit 1
    fi
    echo "$UPLOADED_PATHS" | tail -n 1
}

function download_outputs_from_azure() {
    local _LOCAL_PATHS="$1"
    local _TRANSFER_BUCKET="$2"
    DOWNLOADED_PATHS=$(${CP_PYTHON2_PATH} ${SCRIPTS_DIR}/src/transfer_sources.py --paths "$_LOCAL_PATHS" \
                                                                                 --transfer-bucket "$_TRANSFER_BUCKET" \
                                                                                 --task-name "$MSGEN_TASK_NAME")
    if [[ "$?" != "0" ]]
    then
        pipe_log_fail "Microsoft Genomics outputs downloading has failed." "$MSGEN_TASK_NAME"
        exit 1
    fi
    echo "$DOWNLOADED_PATHS" | tail -n 1
}

GENOMICS_REGION="$CP_GENOMICS_REGION"
GENOMICS_URL="https://$GENOMICS_REGION.microsoftgenomics.net"
GENOMICS_KEY="$CP_GENOMICS_KEY"

if [[ -z "$GENOMICS_URL" || -z "$GENOMICS_KEY" ]]
then
    pipe_log_fail "Microsoft Genomics url and access key are not set for the current region." "$MSGEN_TASK_NAME"
    exit 1
fi

IGNORE_AZURE_REGION="false"

TRANSFER_BUCKET="$CP_TRANSFER_BUCKET"
OUTPUT=$(expand_vars "$OUTPUT")
AZURE_OUTPUT="$OUTPUT"
if [[ ! -z "$TRANSFER_BUCKET" ]]
then
    TRANSFER_BUCKET_REGION_ID=$(get_storage_region_id "$TRANSFER_BUCKET")
    TRANSFER_BUCKET_REGION=$(expand_vars "\$CP_ACCOUNT_REGION_$TRANSFER_BUCKET_REGION_ID")

    if [[ "$AZURE_OUTPUT" != cp://* && "$AZURE_OUTPUT" != az://* ]]
    then
        AZURE_OUTPUT="az://$TRANSFER_BUCKET/transfer/$RUN_ID$AZURE_OUTPUT"

        if [[ "$TRANSFER_BUCKET_REGION" != "$GENOMICS_REGION" ]]
        then
            pipe_log_warn "Transfer data storage region '$TRANSFER_BUCKET_REGION_ID' differs with Genomics account region '$GENOMICS_REGION'." "$MSGEN_TASK_NAME"
            IGNORE_AZURE_REGION="true"
        fi

        pipe_log_info "Uploading FASTQ1 local files to transfer bucket az://$TRANSFER_BUCKET." "$MSGEN_TASK_NAME"
        FASTQ1=$(upload_inputs_to_azure "FASTQ1" "$TRANSFER_BUCKET")
        if [[ "$?" != "0" ]]
        then
            exit 1
        fi

        pipe_log_info "Uploading FASTQ2 local files to transfer bucket az://$TRANSFER_BUCKET." "$MSGEN_TASK_NAME"
        FASTQ2=$(upload_inputs_to_azure "FASTQ2" "$TRANSFER_BUCKET")
        if [[ "$?" != "0" ]]
        then
            exit 1
        fi
    fi
fi

IFS=',' read -r -a SAMPLES <<< "$SAMPLE"
IFS=',' read -r -a FASTQ1S <<< "$FASTQ1"
IFS=',' read -r -a FASTQ2S <<< "$FASTQ2"

WORKFLOW_IDS=()
WORKFLOW_SAMPLES=()
WORKFLOW_STATES=()
FAILED_SAMPLES=()
for index in "${!SAMPLES[@]}"
do
    CURRENT_SAMPLE="${SAMPLES[index]}"
    CURRENT_FASTQ1="${FASTQ1S[index]}"
    CURRENT_FASTQ2="${FASTQ2S[index]}"
    submit_workflow "$CURRENT_SAMPLE" \
                    "$CURRENT_FASTQ1" \
                    "$CURRENT_FASTQ2" \
                    "$REFERENCE" \
                    "$PROCESS" \
                    "$AZURE_OUTPUT" \
                    "$GENOMICS_REGION" \
                    "$GENOMICS_URL" \
                    "$GENOMICS_KEY" \
                    "$BSQR" \
                    "$READ_GROUP" \
                    "$EMIT_REF_CONFIDENCE" \
                    "$BGZIP" \
                    "$IGNORE_AZURE_REGION"
    if [[ "$?" != "0" ]]
    then
        FAILED_SAMPLES+=("$CURRENT_SAMPLE")
        continue
    fi
    WORKFLOW_IDS+=("$WORKFLOW_ID")
    WORKFLOW_SAMPLES+=("$CURRENT_SAMPLE")
    WORKFLOW_STATES+=("")
done

POLLS_NUMBER=360
POLLING_INTERVAL=10
for POLL_NUMBER in $(seq 1 "$POLLS_NUMBER")
do
    REMAINING_WORKFLOW_IDS=()
    REMAINING_WORKFLOW_SAMPLES=()
    REMAINING_WORKFLOW_STATES=()
    for index in "${!WORKFLOW_IDS[@]}"
    do
        WORKFLOW_ID="${WORKFLOW_IDS[index]}"
        WORKFLOW_SAMPLE="${WORKFLOW_SAMPLES[index]}"
        OLD_WORKFLOW_STATUS="${WORKFLOW_STATES[index]}"
        { IFS= read -r WORKFLOW_STATUS && IFS= read -r WORKFLOW_MESSAGE; } <<< $(get_workflow_status_and_message "$GENOMICS_URL" \
                                                                                                                 "$GENOMICS_KEY" \
                                                                                                                 "$WORKFLOW_SAMPLE")
        check_workflow_finished "$WORKFLOW_ID" "$WORKFLOW_SAMPLE" "$OLD_WORKFLOW_STATUS" "$WORKFLOW_STATUS" "$WORKFLOW_MESSAGE"
        if [[ "$?" != "0" ]]
        then
            REMAINING_WORKFLOW_IDS+=("$WORKFLOW_ID")
            REMAINING_WORKFLOW_SAMPLES+=("$WORKFLOW_SAMPLE")
            REMAINING_WORKFLOW_STATES+=("${WORKFLOW_MESSAGE:-$WORKFLOW_STATUS}")
            continue
        fi

        if ! check_workflow_finished_successfully "$WORKFLOW_STATUS"
        then
            FAILED_SAMPLES+=("$WORKFLOW_SAMPLE")
        fi

        WORKFLOW_EXECUTION_TIME=$(expr "$POLL_NUMBER" \* "$POLLING_INTERVAL")
        if [[ "$POLL_NUMBER" == "$POLLS_NUMBER" ]]
        then
            FAILED_SAMPLES+=("$WORKFLOW_SAMPLE")
            pipe_log_fail "Microsoft Genomics workflow hasn't finished after $WORKFLOW_EXECUTION_TIME seconds." "$WORKFLOW_SAMPLE"
        fi
    done
    WORKFLOW_IDS=("${REMAINING_WORKFLOW_IDS[@]}")
    WORKFLOW_SAMPLES=("${REMAINING_WORKFLOW_SAMPLES[@]}")
    WORKFLOW_STATES=("${REMAINING_WORKFLOW_STATES[@]}")
    if [[ "${#WORKFLOW_IDS[@]}" == "0" ]]
    then
        break
    fi
    sleep "$POLLING_INTERVAL"
done

if [[ "${#FAILED_SAMPLES[@]}" != "0" ]]
then
    ERRORS="${FAILED_SAMPLES[@]}"
    pipe_log_fail "Several Microsoft Genomics sample analyses have failed: $ERRORS." "$MSGEN_TASK_NAME"
    exit 1
fi

if [[ ! -z "$TRANSFER_BUCKET" && "$AZURE_OUTPUT" != "$OUTPUT" ]]
then
    download_outputs_from_azure "$OUTPUT" "$TRANSFER_BUCKET"
fi

pipe_log_success "All Microsoft Genomics workflows have finished successfully." "$MSGEN_TASK_NAME"
