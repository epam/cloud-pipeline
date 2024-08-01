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

source utils.sh

get_users() {
    local API_URL="$1"  #Server address name 
    local API_TOKEN="$2" #Access key for pipe cli(API_TOKEN)  
    local API_ENDPOINT="user/export"
    local OUTPUT_FILE="$3"

    # Perform curl request, parse HTTP response status code of good then to API POST request
    get_responce=$(curl -X POST -H "Authorization: Bearer ${API_TOKEN}" -H "Content-Type: application/json" -H "Accept: application/octet-stream" -d '{ 
    "includeAttributes": false,
    "includeDataStorage": false,
    "includeEmail": false,
    "includeFirstLoginDate": false,
    "includeGroups": false,
    "includeHeader": true,
    "includeId": false,
    "includeRegistrationDate": false,
    "includeRoles": true,
    "includeStatus": false,
    "includeUserName": true
    }' "${API_URL}/$API_ENDPOINT")

    if [ -n "$get_responce" ]; then
       echo "$get_responce" > $OUTPUT_FILE
       echo_ok "$API_ENDPOINT from server $API_URL saved in file $OUTPUT_FILE"
    else
       echo_err "API request failed or empty from ${API_URL}/${API_ENDPOINT}"
       return 1
    fi
}
