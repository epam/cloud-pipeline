#!/bin/bash

# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

# Cron gives this job almost no environment of its own - $SYNC_HOME, $SYNC_LOG_DIR and
# $CP_PYTHON_PATH below are only available once env.sh (dumped by "init" at container start) is
# sourced, same as sync-nfs.sh already does.
set -o allexport
source /opt/sync/env.sh
set +o allexport

watcher_script_path="$SYNC_HOME/watch_mount_shares.py"
# "ps -C python | grep ..." (the previous form) assumed the watcher always runs under a process
# literally named "python" - no longer true once CP_PYTHON_PATH can resolve to python3.12. pgrep -f
# matches the full command line regardless of interpreter name, and (unlike "ps | grep") never
# matches its own invocation.
pgrep -f "$watcher_script_path" >/dev/null
if [ $? -ne 0 ]; then
    echo "No active observer process found, starting a new one..."
    nohup "${CP_PYTHON_PATH:-python}" -u "$watcher_script_path" 1>/dev/null 2>$SYNC_LOG_DIR/.nohup.nfswatcher.log &
fi
rm -rf /var/run/nfs-watcher.lock
