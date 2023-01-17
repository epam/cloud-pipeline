#!/bin/bash

# Copyright 2019-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

EDGE_HEALTH_ENDPOINT="http://127.0.0.1:8888/edge-health"
WETTY_HEALTH_ENDPOINT="http://127.0.0.1:8888/wetty-health"

curl --fail "$EDGE_HEALTH_ENDPOINT" --max-time 60 > /dev/null 2>&1 || die "$EDGE_HEALTH_ENDPOINT"
curl --fail "$WETTY_HEALTH_ENDPOINT" --max-time 60 > /dev/null 2>&1 || die "$WETTY_HEALTH_ENDPOINT"
