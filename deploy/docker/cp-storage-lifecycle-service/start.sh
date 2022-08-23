#!/bin/bash
# Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

if [ -z "${API_URL}" ]; then
    echo "API_URL is not provided!"
    exit 22
fi

if [ -z "${API_TOKEN}" ]; then
    echo "API_TOKEN is not provided!"
    exit 22
fi

python3 ${CP_SLS_HOME}/sls/sls/app.py --cp-api-url=${API_URL} \
         --max-execution-running-days=${CP_STORAGE_LIFECYCLE_DAEMON_MAX_EXECUTION_RUNNING_DAYS:-2} \
         --mode=${CP_STORAGE_LIFECYCLE_DAEMON_MODE:-single} \
         --at="${CP_STORAGE_LIFECYCLE_DAEMON_AT_TIME:-00:05}"




