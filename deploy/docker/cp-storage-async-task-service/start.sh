#!/bin/bash
# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

# Thinner than cp-storage-lifecycle-service/start.sh on purpose: that script maps CP_API_JWT_ADMIN
# onto API_TOKEN and builds the API url, while this service reads all three variables itself and exits
# 22 with a message naming them - see sats/app.py.
#
# The directories are made here too, because a volume mounted over either arrives empty.
mkdir -p "${CP_SATS_LOG_DIR:-/opt/cp-sats/logs}" "${CP_SATS_WORK_DIR:-/opt/cp-sats/work}"

# `exec` makes Python PID 1, so the SIGTERM kubernetes sends reaches the handler in sats/app.py and
# the orchestrator stops gracefully. Without it, bash would get the signal and not pass it on.
#
# `-m sats.app` and not the file path: the modules use relative imports. The liveness probe matches
# this command line.
exec "${CP_SATS_VENV:-/opt/cp-sats/venv}/bin/python" -m sats.app
