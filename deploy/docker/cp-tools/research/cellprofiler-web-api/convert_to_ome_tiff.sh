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
OME_TIFF_IMAGE_NAME="$3"

RAW_IMAGE_DIR=$(mktemp -d --dry-run "$IMAGE_PREVIEW_DATA_ROOT/data_XXXXX.raw/")
if [[ -z "$OME_TIFF_IMAGE_NAME" ]]; then
    OME_TIFF_IMAGE_NAME="${HCS_PARSER_OME_TIFF_FILE_NAME:-data.ome.tiff}"
fi
OME_TIFF_IMAGE_PATH="$IMAGE_PREVIEW_DATA_ROOT/$OME_TIFF_IMAGE_NAME"

HCS_PROCESSING_TASK="${HCS_PROCESSING_TASK:-HCS processing}"

function cleanup_raw_dir() {
    rm -rf "$RAW_IMAGE_DIR"
}

bioformats2raw $BIOFORMATS2RAW_EXTRA_FLAGS "$INDEX_FILE_PATH" "$RAW_IMAGE_DIR"
if [ $? -ne 0 ]; then
    cleanup_raw_dir
    exit 1
fi

raw2ometiff $RAW2OMETIFF_EXTRA_FLAGS "$RAW_IMAGE_DIR" "$OME_TIFF_IMAGE_PATH"
if [ $? -ne 0 ]; then
    cleanup_raw_dir
    rm -f "$OME_TIFF_IMAGE_PATH"
    exit 1
fi

cleanup_raw_dir
generate_tiff_offsets --input_file "$OME_TIFF_IMAGE_PATH"
if [ $? -ne 0 ]; then
    exit 1
fi
exit 0
