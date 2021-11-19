#!/bin/bash

# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

function check_installed {
      local _COMMAND_TO_CHECK=$1
      command -v "$_COMMAND_TO_CHECK" >/dev/null 2>&1
      return $?
}

CLEANUP_USER=$1

IFS="@" read -r -a owner_info <<< "$OWNER"

find /root -type l -delete
find /home/${owner_info[0]} -type l -delete

if [[ "$CLEANUP_USER" = true || "$CLEANUP_USER" = TRUE ]]; then
    if check_installed "userdel"; then
        userdel -r -f ${owner_info[0]}
    elif check_installed "deluser"; then
        deluser --remove-home ${owner_info[0]}
    fi
fi

rm -rf /root/.ssh/
rm -rf /code-repository
rm -rf /common
rm -rf /cloud-data
rm -rf /root/.pipe/

PARAM_TYPE_SUFFIX="_PARAM_TYPE"
ENV_TO_CLEAN_COUNT=$(env | grep ${PARAM_TYPE_SUFFIX} | wc -l)

ENVS_TO_UNSET=""

if [[ $ENV_TO_CLEAN_COUNT -ne 0 ]]; then
    while IFS= read -r line
    do
        PARAM_TYPE_NAME=$(awk -F "=" '{print $1}' <<< $line)
        PARAM_NAME=${PARAM_TYPE_NAME%$PARAM_TYPE_SUFFIX}
        ENVS_TO_UNSET="$ENVS_TO_UNSET $PARAM_TYPE_NAME= $PARAM_NAME= "
    done <<< "$(env | grep ${PARAM_TYPE_SUFFIX})"
fi

echo $ENVS_TO_UNSET
