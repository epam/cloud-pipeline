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

watcher_script_path="$SYNC_HOME/watch_mount_shares.py"
ps -C python --no-headers -o args | grep "$watcher_script_path"
if [ $? -ne 0 ]; then
    echo "No active observer process found, starting a new one..."
    nohup python -u "$watcher_script_path" 1>/dev/null 2>$SYNC_LOG_DIR/.nohup.nfswatcher.log &
fi
rm -rf /var/run/nfs-watcher.lock
