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

if [ ! -f /backup.env ]; then
    exit 2
fi
set -o allexport
source /backup.env &> /dev/null
set +o allexport

if [ -z "$CP_BKP_SERVICE_NAME" ]; then
    echo "[ERROR] Service name is not set via CP_BKP_SERVICE_NAME"
    exit 1
fi
if [ -z "$CP_BKP_SERVICE_WD" ]; then
    echo "[ERROR] Backup directory is not set via CP_BKP_SERVICE_WD"
    exit 1
fi
export CP_BKP_LOG="${CP_BKP_LOG:-/opt/bkp-worker/logs/backup-${CP_BKP_SERVICE_NAME}.log}"

mkdir -p "$(dirname $CP_BKP_LOG)"
exec >> "$CP_BKP_LOG"
exec 2>&1

echo
echo "####################################"
date
echo "------------------------------------"

echo "Getting system storage name"
if [ -z "$CP_PREF_STORAGE_SYSTEM_STORAGE_NAME" ]; then
    echo "Trying to get a system storage name from the API preference"

    CP_SYSTEM_STORAGE_NAME=$(curl -k -s \
                                --header "Authorization: Bearer $CP_API_JWT_ADMIN" \
                                "https://$CP_API_SRV_INTERNAL_HOST:$CP_API_SRV_INTERNAL_PORT/pipeline/restapi/preferences/storage.system.storage.name" | \
                            jq -r '.payload.value // "NA"')

    if [ "$CP_SYSTEM_STORAGE_NAME" == "NA" ]; then
        echo "Unable to get system storage name from the API preference"
        exit 1
    fi
else 
    echo "System storage name is defined via CP_PREF_STORAGE_SYSTEM_STORAGE_NAME variable"
    CP_SYSTEM_STORAGE_NAME=$CP_PREF_STORAGE_SYSTEM_STORAGE_NAME
fi

echo "Starting backup"
SOURCE_BKP_SCRIPT="/backupers/${CP_BKP_SERVICE_NAME}-bkp.sh"
if [ ! -f "$SOURCE_BKP_SCRIPT" ]; then
    echo "[ERROR] Cannot find the backup script for ${CP_BKP_SERVICE_NAME}"
    exit 1
fi

SOURCE_BKP_SCRIPT_NAME="$(basename $SOURCE_BKP_SCRIPT)"
TARGET_BKP_SCRIPT_NAME="/tmp/$SOURCE_BKP_SCRIPT_NAME"

TARGET_PODS_NAMES=($(/kubectl get po | grep "^$CP_BKP_SERVICE_NAME" | cut -f1 -d' '))
TARGET_PODS_COUNT=${#TARGET_PODS_NAMES[@]}
if (( $TARGET_PODS_COUNT == 0 )); then
    echo "[ERROR] Cannot find the pod of the $CP_BKP_SERVICE_NAME deployment"
    exit 1
fi

# Copy backup script to the target pod
TARGET_POD="${TARGET_PODS_NAMES[0]}"
/kubectl cp "$SOURCE_BKP_SCRIPT" "$TARGET_POD:$TARGET_BKP_SCRIPT_NAME"
if [ $? -ne 0 ]; then
    echo "[ERROR] Cannot copy the backup script from $SOURCE_BKP_SCRIPT to $TARGET_POD:$TARGET_BKP_SCRIPT_NAME"
    exit 1
fi

# Do the backup
rm -rf $CP_BKP_SERVICE_WD/cp-bkp-*
bash -c "/kubectl exec -i $TARGET_POD -- bash $TARGET_BKP_SCRIPT_NAME"
if [ $? -ne 0 ]; then
    echo "[ERROR] An error occured while executing the backup script at $TARGET_POD:$TARGET_BKP_SCRIPT_NAME"
    exit 1
fi

TARGET_BACKUP_FILES=($(ls $CP_BKP_SERVICE_WD))
if (( ${#TARGET_BACKUP_FILES[@]} == 0 )); then
    echo "[ERROR] No backup files were found in $CP_BKP_SERVICE_WD"
    exit 1
fi

# Copy the backup files to the system object storage
TARGET_STORAGE_LOCATION="$CP_PREF_STORAGE_SCHEMA://$CP_SYSTEM_STORAGE_NAME/backups/$CP_BKP_SERVICE_NAME/"
TARGET_STORAGE_LOCATION_DATE="${TARGET_STORAGE_LOCATION}$(date +%Y-%m-%d)/"
/pipe storage cp "$CP_BKP_SERVICE_WD/" \
                        "$TARGET_STORAGE_LOCATION_DATE" \
                        --recursive \
                        --force  \
                        --quiet
if [ $? -ne 0 ]; then
    echo "[ERROR] An error occured while transferring backups from $CP_BKP_SERVICE_WD to $TARGET_STORAGE_LOCATION_DATE"
    exit 1
fi
rm -rf $CP_BKP_SERVICE_WD/cp-bkp-*

# Remove the outdated backup (keep last CP_BKP_FILES_COUNT)
export CP_BKP_FILES_COUNT="${CP_BKP_FILES_COUNT:-10}"
TARGET_STORAGE_BKP_FILES=($(/pipe storage ls $TARGET_STORAGE_LOCATION))
TARGET_STORAGE_BKP_FILES_COUNT=${#TARGET_STORAGE_BKP_FILES[@]}
echo "Cleaning the outdated backups from $TARGET_STORAGE_LOCATION ($TARGET_STORAGE_BKP_FILES_COUNT exists, $CP_BKP_FILES_COUNT shall be kept)"
while (( TARGET_STORAGE_BKP_FILES_COUNT > CP_BKP_FILES_COUNT )); do
    TARGET_STORAGE_BKP_OUTDATED=${TARGET_STORAGE_BKP_FILES[0]}
    TARGET_STORAGE_BKP_FILES=(${TARGET_STORAGE_BKP_FILES[@]:1})
    TARGET_STORAGE_BKP_FILES_COUNT=${#TARGET_STORAGE_BKP_FILES[@]}
    /pipe storage rm -r -y "${TARGET_STORAGE_LOCATION}${TARGET_STORAGE_BKP_OUTDATED}"
done
