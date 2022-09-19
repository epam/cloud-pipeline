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

if [ -z "$API" ]; then
    if [ -z "${CP_API_SRV_INTERNAL_HOST}" ] || [ -z "$CP_API_SRV_INTERNAL_PORT" ]; then
      echo "API is not provided and CP_API_SRV_INTERNAL_HOST CP_API_SRV_INTERNAL_PORT also is not present, can't construct API!"
      exit 22
    fi
    export API="https://${CP_API_SRV_INTERNAL_HOST}:${CP_API_SRV_INTERNAL_PORT}/pipeline/restapi/"
fi


if [ -z "${API_TOKEN}" ]; then
    if [ "CP_API_JWT_ADMIN" ]; then
       export API_TOKEN="$CP_API_JWT_ADMIN"
    else
       echo "API_TOKEN is not provided!"
       exit 22
    fi
fi

if [ -z "${CP_STORAGE_LIFECYCLE_RUN_COMMAND}" ]; then
     echo "CP_STORAGE_LIFECYCLE_RUN_COMMAND is not provided! Possible values are: 'restore' or 'archive'"
     exit 22
fi

python3 ${CP_SLS_HOME}/sls/sls/app.py --cp-api-url=${API} \
         --max-execution-running-days=${CP_STORAGE_LIFECYCLE_DAEMON_MAX_EXECUTION_RUNNING_DAYS:-2} \
         --mode=${CP_STORAGE_LIFECYCLE_DAEMON_MODE:-single} \
         --command=${CP_STORAGE_LIFECYCLE_RUN_COMMAND} \
         --start-each=${CP_STORAGE_LIFECYCLE_DAEMON_START_EACH} \
         --start-at="${CP_STORAGE_LIFECYCLE_DAEMON_START_AT}" \
         --log-dir="${CP_SLS_HOME}/logs" \
         --log-backup-days="${CP_STORAGE_LIFECYCLE_LOGS_BACKUP_DAYS:-31}"




