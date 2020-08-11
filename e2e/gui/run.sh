#!/bin/bash

# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

set -e

_RECORDING="$1"
_COMMAND="$2"
_STAND_NAME="$3"
_PASSWORD_FILE="$4"

_COMMAND > out.txt &

if [ "$_RECORDING" == "true" ]; then
    CURRENT_DATE_TIME=`date +"%Y-%m-%d_%T"`
    /usr/local/bin/flvrec.py -o "${_STAND_NAME}_$CURRENT_DATE_TIME" -P "${_PASSWORD_FILE}" localhost:1
fi
