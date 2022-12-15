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

function die {
    echo "Endpoint didn't respond with HTTP 200: $1"
    exit 1
}

LIVENESS_JWT_TOKEN=$CP_API_LIVENESS_JWT_TOKEN

if [ -z "$LIVENESS_JWT_TOKEN" ]; then
    echo "LIVENESS_JWT_TOKEN was not specified. Exiting ..."
    exit 1
fi

API_HEALTH_ENDPOINT="https://127.0.0.1:8080/pipeline/restapi/app/info"
curl --fail -k -H "Authorization: Bearer $LIVENESS_JWT_TOKEN" "$API_HEALTH_ENDPOINT" --max-time 60 > /dev/null 2>&1 || die "$API_HEALTH_ENDPOINT"
