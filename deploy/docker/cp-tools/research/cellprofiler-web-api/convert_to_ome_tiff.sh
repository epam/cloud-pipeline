#!/bin/bash

# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

INDEX_FILE_PATH="$1"
IMAGE_PREVIEW_DATA_ROOT="$2"
SEQUENCE_ID="$3"
OME_TIFF_IMAGE_NAME="$4"

RAW_IMAGE_DIR=$(mktemp -d --dry-run "$IMAGE_PREVIEW_DATA_ROOT/$SEQUENCE_ID/data_XXXXX.raw/")
if [[ -z "$OME_TIFF_IMAGE_NAME" ]]; then
    OME_TIFF_IMAGE_NAME="${HCS_PARSER_OME_TIFF_FILE_NAME:-data.ome.tiff}"
fi
OME_TIFF_IMAGE_PATH="$IMAGE_PREVIEW_DATA_ROOT/$SEQUENCE_ID/$OME_TIFF_IMAGE_NAME"

HCS_PROCESSING_TASK="${HCS_PROCESSING_TASK:-HCS processing}"

function log_info() {
    _message="$1"
    pipe_log_info "[$INDEX_FILE_PATH] $_message" "$HCS_PROCESSING_TASK"
}

function log_warn() {
    _message="$1"
    pipe_log_warn "[$INDEX_FILE_PATH] $_message" "$HCS_PROCESSING_TASK"
}

function cleanup_raw_dir() {
    rm -rf "$RAW_IMAGE_DIR"
}

log_info "Converting to raw..."
bioformats2raw $BIOFORMATS2RAW_EXTRA_FLAGS "$INDEX_FILE_PATH" "$RAW_IMAGE_DIR"
if [ $? -ne 0 ]; then
    log_warn "Errors during conversion to raw image, exiting..."
    cleanup_raw_dir
    exit 1
fi

log_info "Converting raw to ome.tiff..."
raw2ometiff $RAW2OMETIFF_EXTRA_FLAGS "$RAW_IMAGE_DIR" "$OME_TIFF_IMAGE_PATH"
if [ $? -ne 0 ]; then
    log_warn "Errors during conversion of raw to ome.tiff, exiting..."
    cleanup_raw_dir
    rm -f "$OME_TIFF_IMAGE_PATH"
    exit 1
fi

log_info "Cleaning up raw dir..."
cleanup_raw_dir
if [ $? -ne 0 ]; then
    log_warn "Unable to cleanup raw image dir..."
fi
log_info "Generating ome.tiff offsets..."
generate_tiff_offsets --input_file "$OME_TIFF_IMAGE_PATH"
if [ $? -ne 0 ]; then
    log_warn "Errors during offset file generation, exiting..."
    exit 1
fi
log_info "Sequence processing is finished."
exit 0
