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
# See the License for the specific language governing permissions and
# limitations under the License.

PASSWORD_FILE=$USER_HOME_DIR/e2e/gui/password.txt
CURRENT_DATE=$(date +"%Y-%m-%d")
STAND_NAME=$(grep -i "e2e.ui.root.address=https://" /$USER_HOME_DIR/e2e/gui/default.conf | sed -n 's/.*https:\x2F\x2F*//p' | awk -F. '{print $1}')
RECORDING_LOG="/var/log/recording.log"
ITERATION=1

function sig_handler {
    echo "Received SIGTERM, stopping current recording and starting a new one."
    if [ $APP_PID -ne 0 ]; then
        kill -SIGTERM "$APP_PID" 2>/dev/null
        wait "$APP_PID"
    fi
    ITERATION=$((ITERATION + 1))
    recording &
}

function recording() {
    output_file="${STAND_NAME}_${CURRENT_DATE}_${ITERATION}.flv"
    echo "Starting recording: $output_file"
    nohup /tmp/vnc2flv-20100207/tools/flvrec.py -d -o "${output_file}" -P "${PASSWORD_FILE}" localhost:1 &> $RECORDING_LOG &
    RECORD_PID=$!
    echo "Started recording with pid: $RECORD_PID"
    wait $RECORD_PID
    RECORD_EXIT_STATUS=$?
    if [ $RECORD_EXIT_STATUS -eq 0 ]; then
        echo "Recording $output_file completed successfully."
        exit 0
    else
        ITERATION=$((ITERATION + 1))
        echo "Recording $output_file failed."
    fi
}

trap 'sig_handler' SIGTERM SIGINT
while true; do
    recording
done &

tail -F $RECORDING_LOG &
wait
