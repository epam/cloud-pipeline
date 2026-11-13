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

# Runs the GUI tests inside the runner container (the tests sources with default.conf are mounted to the working dir)
#   $1 - "true" to record the screen into <stand>_<date>.webm
# Environment:
#   E2E_GRADLE_OFFLINE - "true" (default) to use the Gradle distribution and dependencies cached in the image
#   http_proxy/https_proxy/no_proxy - passed to Gradle, if E2E_GRADLE_OFFLINE is not "true"

set -e

_RECORDING="$1"
_CONF="${E2E_CONF_FILE:-default.conf}"

CURRENT_DATE=$(date +"%Y-%m-%d")
STAND_NAME=$(grep -i "^e2e.ui.root.address=https://" "$_CONF" | sed -n 's/.*https:\x2F\x2F*//p' | awk -F. '{print $1}')

_GRADLE_ARGS="--no-daemon"
if [ "${E2E_GRADLE_OFFLINE:-true}" == "true" ]; then
    _GRADLE_ARGS="$_GRADLE_ARGS --offline"
else
    bash /dockerstartup/gradle_proxy_props.sh
fi

if [ "$_RECORDING" == "true" ]; then
    ffmpeg -loglevel error -f x11grab -framerate 5 -video_size "$VNC_RESOLUTION" -i "$DISPLAY" \
           -c:v libvpx -b:v 1M "${STAND_NAME:-e2e}_${CURRENT_DATE}.webm" < /dev/null &
    _FFMPEG_PID=$!
    # Stop the recording gracefully (SIGINT) so the video file is finalized, even if tests fail
    trap '_rc=$?; kill -INT $_FFMPEG_PID 2>/dev/null; wait $_FFMPEG_PID 2>/dev/null || true; exit $_rc' EXIT
fi

# Gradle 3.4 can't parse the Java 11 classes of a transitive dependency (webdrivermanager) and logs a
# "Malformed class file" warning per class when compiling: these lines are dropped, the exit code is kept
set -o pipefail
./gradlew $_GRADLE_ARGS clean test 2>&1 | grep --line-buffered -v '^Malformed class file \[io/github/bonigarcia/wdm/'
