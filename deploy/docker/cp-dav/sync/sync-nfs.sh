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

set -o allexport
source /opt/sync/env.sh
set +o allexport

SYNC_LOG_DIR="${SYNC_LOG_DIR:-/var/log/dav/sync}"
mkdir -p "$SYNC_LOG_DIR"

if [ -z "$API" ]; then
    echo "[ERROR] API is not defined" >> $SYNC_LOG_DIR/sync-nfs.log
    exit 1
fi

if [ -z "$API_TOKEN" ]; then
    echo "[ERROR] API_TOKEN is not defined" >> $SYNC_LOG_DIR/sync-nfs.log
    exit 1
fi

if [ -z "$CP_DAV_SERVE_DIR" ]; then
    echo "[ERROR] CP_DAV_SERVE_DIR is not defined" >> $SYNC_LOG_DIR/sync-nfs.log
    exit 1
fi

if [ -z "$CP_DAV_MOUNT_POINT" ]; then
    echo "[ERROR] CP_DAV_MOUNT_POINT is not defined" >> $SYNC_LOG_DIR/sync-nfs.log
    exit 1
fi

bash $SYNC_HOME/nfs-roles-management/syncmounts.sh "$API" "$API_TOKEN" "$CP_DAV_MOUNT_POINT" >> $SYNC_LOG_DIR/sync-nfs.log 2>&1

if [ $? -ne 0 ]; then
    echo "[ERROR] syncmounts.sh failed, syncnfs.py will NOT be run" >> $SYNC_LOG_DIR/sync-nfs.log
    exit 1
fi

python $SYNC_HOME/nfs-roles-management/syncnfs.py sync \
                                --api=$API \
                                --key=$API_TOKEN \
                                --users-root=$CP_DAV_SERVE_DIR \
                                --nfs-root=$CP_DAV_MOUNT_POINT >> $SYNC_LOG_DIR/sync-nfs.log 2>&1

# need to remove it here because blobfuse will inherit parent lock and next cron execution will be deadlocked
rm -rf /var/run/sync-nfs.lock
