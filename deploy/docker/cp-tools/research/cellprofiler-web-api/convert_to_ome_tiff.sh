#!/bin/bash

# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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
IMAGES_WORKDIR="$2"
IMAGES_DATA_ROOT="$3"
OME_TIFF_IMAGE_NAME="$4"
REMOTE_RESULTS_DIR="$5"

RAW_IMAGE_DIR=$(mktemp -d --dry-run "$IMAGES_WORKDIR/data_XXXXX.raw/")
if [[ -z "$OME_TIFF_IMAGE_NAME" ]]; then
    OME_TIFF_IMAGE_NAME="data.ome.tiff"
fi
OME_TIFF_RESULTS="$IMAGES_WORKDIR/results"
mkdir $OME_TIFF_RESULTS
OME_TIFF_IMAGE_PATH="$OME_TIFF_RESULTS/$OME_TIFF_IMAGE_NAME"

bioformats2raw $BIOFORMATS2RAW_EXTRA_FLAGS "$INDEX_FILE_PATH" "$RAW_IMAGE_DIR"
if [ $? -ne 0 ]; then
    rm -rf "$RAW_IMAGE_DIR"
    exit 1
fi

raw2ometiff $RAW2OMETIFF_EXTRA_FLAGS "$RAW_IMAGE_DIR" "$OME_TIFF_IMAGE_PATH"
if [ $? -ne 0 ]; then
    rm -rf "$RAW_IMAGE_DIR"
    rm -f "$OME_TIFF_IMAGE_PATH"
    exit 1
fi

rm -rf "$RAW_IMAGE_DIR"
generate_tiff_offsets --input_file "$OME_TIFF_IMAGE_PATH"
if [ $? -ne 0 ]; then
    exit 1
fi

cp -R "$OME_TIFF_RESULTS"/* "$REMOTE_RESULTS_DIR"
rm -rf "$IMAGES_WORKDIR"

exit 0
