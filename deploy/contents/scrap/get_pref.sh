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

get_pref() {
    local API_URL="$1"  #Server address name 
    local API_TOKEN="$2" #Access key for pipe cli(API_TOKEN)  
    local output_dir="${3}/preference.json" #Output directory and file name where preferences will be saved
    local api_endpoint="preferences"
   
    # Perform curl request, parse HTTP response status code of good then to API GET request
    get_responce=$(curl -s -H "Authorization: Bearer ${API_TOKEN}" -H "Accept: application/json" "${API_URL}/${api_endpoint}" | jq)

    if [ $? -eq 0 ] && [ -n "$get_responce" ]; then
       echo "$get_responce" > $output_dir 
       echo_ok "Preferences from server $API_URL saved in file $output_dir"
    else
       echo_err "API request failed or empty"
       exit 1   
    fi
}
