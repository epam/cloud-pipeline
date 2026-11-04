#!/bin/bash

# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

CURRENT_DATE=$(date +"%Y-%m-%d")
STAND_NAME=$(grep -i "e2e.ui.root.address=https://" "$USER_HOME_DIR/e2e/gui/default.conf" | sed -n 's/.*https:\x2F\x2F*//p' | awk -F. '{print $1}')

if [ "$_RECORDING" == "true" ]; then
    ffmpeg -loglevel error -f x11grab -framerate 5 -video_size "$VNC_RESOLUTION" -i "$DISPLAY" \
           -c:v libvpx -deadline realtime -cpu-used 8 -b:v 1M "${STAND_NAME}_${CURRENT_DATE}.webm" < /dev/null &
    _FFMPEG_PID=$!
    # Stop the recording with SIGINT, so that ffmpeg finalizes the video, also when the tests fail.
    # Keep the exit code of the tests: under "set -e", a failing command in the trap would replace it,
    # so the trap ignores the errors of kill and wait (e.g. when ffmpeg has already stopped)
    trap '_RC=$?; kill -INT "$_FFMPEG_PID" 2>/dev/null || true; wait "$_FFMPEG_PID" 2>/dev/null || true; exit $_RC' EXIT
fi

./gradlew clean test
