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

function get_current_disk_usage_percent() {
  _MOUNT_POINT="$1"
  df -BG "$_MOUNT_POINT" | grep -v Filesystem | awk '{print $5}' | cut -d"%" -f1 -
}

function get_current_disk_usage_gb() {
  _MOUNT_POINT="$1"
  df -BG "$_MOUNT_POINT" | grep -v Filesystem | awk '{print $3}' | cut -d"G" -f1 -
}

function get_total_disk_size_gb() {
  _MOUNT_POINT="$1"
  df -BG "$_MOUNT_POINT" | grep -v Filesystem | awk '{print $2} ' | cut -d"G" -f1
}

function is_jq_null() {
    _VAL="$1"
    if [ -z "$_VAL" ] || [ "$_VAL" == "null" ]; then
        return 0
    else
        return 1
    fi
}

function send_notification() {
    _TO="$1"
    _STORAGE_NAME="$2"
    _STORAGE_ID="$3"
    _STORAGE_USED="$4"
    _STORAGE_THRESHOLD="$5"
    _STORAGE_TOTAL="$6"

    if is_jq_null "$_TO"; then
        echo "[WARN] List of recipients is not set for data storage with ID $_STORAGE_ID, please use \"usage-threshold-to\" attribute to specify that. Email won't be sent"
        return 1
    fi

    _SUBJ="[Administration] Storage $_STORAGE_NAME has reached a $_STORAGE_THRESHOLD threshold"
    _BODY="Please be informed, that a data storage <b>$_STORAGE_NAME</b> has reached a threashold:<br/>* Current usage: $_STORAGE_USED<br/>* Threshold: $_STORAGE_THRESHOLD<br/>* Total volume: $_STORAGE_TOTAL"
    
    IFS=';' read -ra _TO_ARR <<< "$_TO"
    for _TO_USER in "${_TO_ARR[@]}"; do
        echo "[INFO] Sending notification to: $_TO_USER"
        curl -s -k -X POST -H 'Content-Type: application/json' \
            -H "Authorization: Bearer $API_TOKEN" \
            -d "{ \"subject\": \"$_SUBJ\", \"body\": \"$_BODY\", \"toUser\": \"$_TO_USER\"}" \
            "$API/notification/message" &> /dev/null
    done
}

if [ -z "$CP_CAP_LIMIT_MOUNTS" ]; then
    echo '[ERROR] No mounts are set for monitoring. Use CP_CAP_LIMIT_MOUNTS parameter to specify them. Exiting'
    exit 1
fi

IFS=',' read -ra _MOUNT_ID_ARR <<< "$CP_CAP_LIMIT_MOUNTS"
for _MOUNT_ID in "${_MOUNT_ID_ARR[@]}"; do
    echo
    _MOUNT_POINT_JSON=$(curl -s -k -H "Authorization: Bearer $API_TOKEN" "$API/datastorage/find?id=$_MOUNT_ID")
    _MOUNT_POINT=$(echo "$_MOUNT_POINT_JSON" | jq -r '.payload.mountPoint')
    _MOUNT_POINT=$(eval echo $_MOUNT_POINT)
    _MOUNT_NAME=$(echo "$_MOUNT_POINT_JSON" | jq -r '.payload.name')
    if is_jq_null "$_MOUNT_POINT"; then
        echo "[WARN] Skipping storage with ID $_MOUNT_ID as it does not have a mount point. Such storages are not supported by the monitoring script"
        continue
    fi
    if [ ! -d "$_MOUNT_POINT" ]; then
        echo "[WARN] Skipping storage with ID $_MOUNT_ID as it's mount directory does not exist at $_MOUNT_POINT"
        continue
    fi

    echo "[INFO] Processing storage with ID $_MOUNT_ID mounted to $_MOUNT_POINT"
    _MOUNT_TOTAL_GB=$(get_total_disk_size_gb $_MOUNT_POINT)
    _MOUNT_USED_GB=$(get_current_disk_usage_gb $_MOUNT_POINT)
    _MOUNT_USED_PERCENT=$(get_current_disk_usage_percent $_MOUNT_POINT)
    echo "[INFO] -- Total:  ${_MOUNT_TOTAL_GB}GB"
    echo "[INFO] -- Used:   ${_MOUNT_USED_GB}GB"
    echo "[INFO]            ${_MOUNT_USED_PERCENT}%"

    _MOUNT_META_JSON=$(curl -s -k -H "Authorization: Bearer $API_TOKEN" "$API/metadata/load" \
                        -H "Content-Type: application/json" \
                        -d "[ { \"entityId\": $_MOUNT_ID, \"entityClass\": \"DATA_STORAGE\" } ]")
    _MOUNT_THRESHOLD_GB=$(echo $_MOUNT_META_JSON | jq -r '.payload[] | .data["usage-threshold-gb"].value' 2>/dev/null)
    _MOUNT_THRESHOLD_PECENT=$(echo $_MOUNT_META_JSON | jq -r '.payload[] | .data["usage-threshold-percent"].value' 2>/dev/null)
    _MOUNT_THRESHOLD_TO=$(echo $_MOUNT_META_JSON | jq -r '.payload[] | .data["usage-threshold-to"].value' 2>/dev/null)

    if ! is_jq_null "$_MOUNT_THRESHOLD_GB"; then
        echo "[INFO] Using threshold in GB: $_MOUNT_THRESHOLD_GB"
        if (( $_MOUNT_USED_GB > $_MOUNT_THRESHOLD_GB )); then
            echo "[WARN] Threshold (${_MOUNT_THRESHOLD_GB}GB) exceeded for storage with ID $_MOUNT_ID (${_MOUNT_USED_GB}GB)"
            send_notification   "$_MOUNT_THRESHOLD_TO" \
                                "$_MOUNT_NAME" \
                                "$_MOUNT_ID" \
                                "${_MOUNT_USED_GB}GB" \
                                "${_MOUNT_THRESHOLD_GB}GB" \
                                "${_MOUNT_TOTAL_GB}GB"
        else
            echo "[INFO] Threshold is not reached for storage with ID $_MOUNT_ID"
        fi
    elif ! is_jq_null "$_MOUNT_THRESHOLD_PECENT"; then
        echo "[INFO] Using threshold in %%: $_MOUNT_THRESHOLD_PECENT"
        if (( $_MOUNT_USED_PERCENT > $_MOUNT_THRESHOLD_PECENT )); then
            echo "[WARN] Threshold (${_MOUNT_THRESHOLD_PECENT}%) exceeded for storage with ID $_MOUNT_ID (${_MOUNT_USED_PERCENT}%), sending notification"
            send_notification   "$_MOUNT_THRESHOLD_TO" \
                                "$_MOUNT_NAME" \
                                "$_MOUNT_ID" \
                                "${_MOUNT_USED_PERCENT}%" \
                                "${_MOUNT_THRESHOLD_PECENT}%" \
                                "${_MOUNT_TOTAL_GB}GB"
        else
            echo "[INFO] Threshold is not reached for storage with ID $_MOUNT_ID"
        fi
    else
        echo "[WARN] No thresholds specified for storage with ID $_MOUNT_ID, skipping volume verification"
        continue
    fi
done

