#!/bin/bash

# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
# See the License for the specific language governing peKrmissions and
# limitations under the License.

export RED='\033[0;31m'
export GREEN='\033[0;32m'
export YELLOW='\033[1;33m'
export SET='\033[0m'

function echo_info() {
    echo "$@" 1>&2;
}

function echo_err() {
    echo -e "${RED}$*${SET}" 1>&2;
}

function echo_warn() {
    echo -e "${YELLOW}$*${SET}" 1>&2;
}

function echo_ok() {
    echo -e "${GREEN}$*${SET}" 1>&2;
}

function call_cp_api() {
    local API_URL="$1"  #Server address name
    local API_TOKEN="$2" #Access key for pipe cli(API_TOKEN)
    local API_ENDPOINT="$3"
    local OUTPUT_FILE="$4"

    # Perform curl request, parse HTTP response status code of good then to API GET request
    get_responce=$(curl -s -H "Authorization: Bearer ${API_TOKEN}" -H "Accept: application/json" "${API_URL}/${API_ENDPOINT}" | jq '.payload')

    if [ $? -eq 0 ] && [ -n "$get_responce" ]; then
       echo "$get_responce" > $OUTPUT_FILE
       echo_ok "$API_ENDPOINT from server $API_URL saved in file $OUTPUT_FILE"
    else
       echo_err "API request failed or empty"
       exit 1
    fi
}