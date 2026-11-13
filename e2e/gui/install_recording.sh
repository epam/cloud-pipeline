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

if [ "$_RECORDING" != "true" ]; then
    echo "Screen recording tool will not be installed. If it is required - rebuild with \"--build-arg RECORDING=true\""
    exit 0
fi

# vnc2flv requires Python 2, which is not available on Rocky 9: ffmpeg (EPEL) grabs the X display instead (see run.sh)
dnf install -y ffmpeg-free
dnf clean all
