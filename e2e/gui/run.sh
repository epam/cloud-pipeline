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

PASSWORD_FILE=$USER_HOME_DIR/e2e/gui/password.txt
CURRENT_DATE=$(date +"%Y-%m-%d")
STAND_NAME=$(grep -i "e2e.ui.root.address=https://" /$USER_HOME_DIR/e2e/gui/default.conf | sed -n 's/.*https:\x2F\x2F*//p' | awk -F. '{print $1}')

if [ "$_RECORDING" == "true" ]; then
    (./gradlew clean test --tests com.epam.pipeline.autotests.ObjectMetadataFolderTest > out.txt && docker stop ui_test1) & \
    /usr/local/bin/flvrec.py -o "${STAND_NAME}_${CURRENT_DATE_TIME}" -P "${PASSWORD_FILE}" localhost:1
else
    ./gradlew clean test --tests com.epam.pipeline.autotests.ObjectMetadataFolderTest > out.txt
fi
