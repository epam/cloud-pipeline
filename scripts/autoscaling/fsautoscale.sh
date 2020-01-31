#!/bin/bash

# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

function call_api() {
  _API="$1"
  _API_TOKEN="$2"
  _API_METHOD="$3"
  _HTTP_METHOD="$4"
  _HTTP_BODY="$5"
  if [[ "$_HTTP_BODY" ]]
  then
    curl -f -s -k -X "$_HTTP_METHOD" \
      --header 'Accept: application/json' \
      --header 'Authorization: Bearer '"$_API_TOKEN" \
      --header 'Content-Type: application/json' \
      --data "$_HTTP_BODY" \
      "$_API$_API_METHOD"
  else
    curl -f -s -k -X "$_HTTP_METHOD" \
      --header 'Accept: application/json' \
      --header 'Authorization: Bearer '"$_API_TOKEN" \
      --header 'Content-Type: application/json' \
      "$_API$_API_METHOD"
  fi
}

function pipe_api_log() {
  _MESSAGE="$1"
  _STATUS="$2"
  if [[ "$RUN_ID" ]] && [[ "$LOG_TASK" ]]
  then
    if [[ "$_STATUS" == "$ERROR_LOG_LEVEL" ]]
    then
      STATUS="FAILURE"
    else
      STATUS="RUNNING"
    fi
    call_api "$_API" "$_API_TOKEN" "run/$RUN_ID/log" "POST" '{
        "date": "'"$(get_current_date)"'",
        "logText": "'"$_MESSAGE"'",
        "runId": '"$RUN_ID"',
        "status": "'"$STATUS"'",
        "taskName": "'"$LOG_TASK"'"
      }'
  fi
}

function get_current_date() {
  date '+%Y-%m-%d %H:%M:%S.%N' | cut -b1-23
}

function pipe_log_debug() {
  _MESSAGE="$1"
  pipe_log "$_MESSAGE" "$DEBUG_LOG_LEVEL"
}

function pipe_log_info() {
  _MESSAGE="$1"
  pipe_log "$_MESSAGE" "$INFO_LOG_LEVEL"
}

function pipe_log_error() {
  _MESSAGE="$1"
  pipe_log "$_MESSAGE" "$ERROR_LOG_LEVEL"
}

function pipe_log() {
  _MESSAGE="$1"
  _STATUS="$2"
  echo "$(get_current_date): [$_STATUS] $_MESSAGE"
  if [[ "$DEBUG" ]] || [[ "$_STATUS" != "$DEBUG_LOG_LEVEL" ]]
  then
    pipe_api_log "$_MESSAGE" "$_STATUS"
  fi
}

function is_filesystem_scalable() {
  _MOUNT_POINT="$1"
  _SCALABLE_FILESYSTEM_TYPE="$2"
  FILESYSTEM_TYPE=$(df -hT "$_MOUNT_POINT" | grep -v Filesystem | awk '{ print $2 }')
  [[ "$FILESYSTEM_TYPE" == "$_SCALABLE_FILESYSTEM_TYPE" ]]
  return "$?"
}

function get_current_run_id() {
  _API="$1"
  _API_TOKEN="$2"
  _NODE="$3"
  call_api "$_API" "$_API_TOKEN" "cluster/node/$_NODE/load" "GET" |
    jq -r ".payload.labels.runid" |
    grep -v "^null$"
}

function is_true() {
  _BOOLEAN="$1"
  LOWER_BOOLEAN=$(echo "$_BOOLEAN" | tr "[:upper:]" "[:lower:]")
  [[ "$LOWER_BOOLEAN" == "true" ]]
  return "$?"
}

function get_current_disk_usage() {
  _MOUNT_POINT="$1"
  df -BG "$_MOUNT_POINT" | grep -v Filesystem | awk '{print $5}' | cut -d"%" -f1 -
}

function get_total_disk_size() {
  _MOUNT_POINT="$1"
  df -BG "$_MOUNT_POINT" | grep -v Filesystem | awk '{print $2} ' | cut -d"G" -f1
}

function get_required_disk_size() {
  _DISK="$1"
  _DELTA="$2"
  echo "$_DISK+$_DISK*$_DELTA" | bc | cut -d"." -f1
}

function get_additional_disk_size() {
  _TOTAL_SIZE="$1"
  _REQUIRED_SIZE="$2"
  _MIN_SIZE="$3"
  _MAX_SIZE="$4"
  SIZE=$((_REQUIRED_SIZE - _TOTAL_SIZE))
  SIZE=$((SIZE > _MIN_SIZE ? SIZE : _MIN_SIZE))
  SIZE=$((SIZE < _MAX_SIZE ? SIZE : _MAX_SIZE))
  echo "$SIZE"
}

function attach_new_disk() {
  _API="$1"
  _API_TOKEN="$2"
  _RUN_ID="$3"
  _SIZE="$4"
  call_api "$_API" "$_API_TOKEN" "run/$_RUN_ID/disk/attach" "POST" '{"size": "'"$_SIZE"'"}'
}

function get_matching_devices() {
  _SIZE="$1"
  lsblk -sdrpnb -o NAME,TYPE,SIZE,MOUNTPOINT | awk '$2 == "disk" && $3 / (1024 ^ 3) == "'"$_SIZE"'" && $4 == "" { print $1 }'
}

function get_mounted_devices() {
  _MOUNT_POINT="$1"
  btrfs filesystem show "$_MOUNT_POINT" | awk '$1 == "devid" { print $8 }'
}

function get_unused_device() {
  _MOUNT_POINT="$1"
  _SIZE="$2"
  MATCHING_DEVICES=$(get_matching_devices "$_SIZE")
  IFS=$'\n' read -rd '' -a MATCHING_DEVICES_ARRAY <<<"$MATCHING_DEVICES"
  MOUNTED_DEVICES=$(get_mounted_devices "$_MOUNT_POINT")
  IFS=$'\n' read -rd '' -a MOUNTED_DEVICES_ARRAY <<<"$MOUNTED_DEVICES"
  UNUSED_DEVICES=()
  for MATCHING_DEVICE in "${MATCHING_DEVICES_ARRAY[@]}"
  do
    UNUSED_MATCHING_DEVICE="$MATCHING_DEVICE"
    for USED_DEVICE in "${MOUNTED_DEVICES_ARRAY[@]}"
    do
      if [[ "$MATCHING_DEVICE" == "$USED_DEVICE" ]]
      then
        UNUSED_MATCHING_DEVICE=""
      fi
    done
    if [[ "$UNUSED_MATCHING_DEVICE" ]]
    then
      UNUSED_DEVICES+=("$UNUSED_MATCHING_DEVICE")
    fi
  done
  if [[ "${#UNUSED_DEVICES[@]}" -gt "0" ]]
  then
    if [[ "${#UNUSED_DEVICES[@]}" -gt "1" ]]
    then
      pipe_log_debug "More than one matching unused devices with the required size ${_SIZE}G were found ${UNUSED_DEVICES[*]}. Only the first one will be used."
    fi
    echo "${UNUSED_DEVICES[0]}"
  fi
}

function get_new_device() {
  _MOUNT_POINT="$1"
  _SIZE="$2"
  _TIMEOUT="$3"
  DISK_AVAILABILITY_CHECK_REPEAT=0
  while [[ "$DISK_AVAILABILITY_CHECK_REPEAT" -lt "$_TIMEOUT" ]]
  do
    UNUSED_DISK_DEVICE=$(get_unused_device "$_MOUNT_POINT" "$_SIZE")
    [[ "$UNUSED_DISK_DEVICE" ]] && break
    DISK_AVAILABILITY_CHECK_REPEAT=$((DISK_AVAILABILITY_CHECK_REPEAT + 1))
    sleep 1
  done
  echo "$UNUSED_DISK_DEVICE"
}

function append_disk_device() {
  _MOUNT_POINT="$1"
  _DEVICE="$2"
  btrfs device add "$_DEVICE" "$_MOUNT_POINT"
}

function get_system_preferences() {
  _API="$1"
  _API_TOKEN="$2"
  call_api "$_API" "$_API_TOKEN" "preferences" "GET" |
    jq -r '.payload[] | .name + "=" + .value' |
    grep -v "^null$"
}

function resolve_system_preference() {
  _PREFERENCES="$1"
  _PREFERENCE="$2"
  _DEFAULT_VALUE="$3"

  NAME_AND_VALUE=$(echo "$_PREFERENCES" | grep "$_PREFERENCE=")
  VALUE="${NAME_AND_VALUE#$_PREFERENCE=}"
  if [[ "$VALUE" ]]
  then
    echo "$VALUE"
  else
    echo "$_DEFAULT_VALUE"
  fi
}

function get_fractional_part() {
  _FLOAT="$1"
  echo "$_FLOAT" | cut -d"." -f2
}

ERROR_LOG_LEVEL="ERROR"
INFO_LOG_LEVEL="INFO"
DEBUG_LOG_LEVEL="DEBUG"

while [[ "$#" -gt "0" ]]
do
  case "$1" in
  -u | --api-url)
    API="$2"
    shift
    shift
    ;;
  -t | --api-token)
    API_TOKEN="$2"
    shift
    shift
    ;;
  -n | --node-name)
    NODE="$2"
    shift
    shift
    ;;
  -m | --mount-point)
    MOUNT_POINT="$2"
    shift
    shift
    ;;
  -d | --monitoring-delay)
    MONITORING_DELAY="$2"
    shift
    shift
    ;;
  -e | --debug)
    DEBUG="true"
    shift
    ;;
  *)
    pipe_log_debug "Unexpected argument $1 will be skipped."
    shift
    ;;
  esac
done

if [[ -z "$API" ]] || [[ -z "$API_TOKEN" ]] || [[ -z "$NODE" ]] || [[ -z "$MOUNT_POINT" ]]
then
  pipe_log_error "Some of the required arguments are missing."
  exit 1
fi

LOG_TASK="${LOG_TASK:-FilesystemAutoscaling}"
MONITORING_DELAY="${MONITORING_DELAY:-10}"
DISK_AVAILABILITY_TIMEOUT="${DISK_AVAILABILITY_TIMEOUT:-10}"
SCALABLE_FILESYSTEM_TYPE="${SCALABLE_FILESYSTEM_TYPE:-btrfs}"

AUTOSCALE_PREFERENCE="${AUTOSCALE_PREFERENCE:-cluster.instance.hdd.scale.enabled}"
AUTOSCALE_PREFERENCE_DEFAULT="${AUTOSCALE_PREFERENCE_DEFAULT:-false}"
MONITORING_DELAY_PREFERENCE="${MONITORING_DELAY_PREFERENCE:-cluster.instance.hdd.scale.monitoring.delay}"
MONITORING_DELAY_PREFERENCE_DEFAULT="${MONITORING_DELAY_PREFERENCE_DEFAULT:-10}"
THRESHOLD_PREFERENCE="${THRESHOLD_PREFERENCE:-cluster.instance.hdd.scale.threshold.ratio}"
THRESHOLD_PREFERENCE_DEFAULT="${THRESHOLD_PREFERENCE_DEFAULT:-0.75}"
DELTA_PREFERENCE="${DELTA_PREFERENCE:-cluster.instance.hdd.scale.delta.ratio}"
DELTA_PREFERENCE_DEFAULT="${DELTA_PREFERENCE_DEFAULT:-0.5}"
MAX_DEVICE_NUMBER_PREFERENCE="${MAX_DEVICE_NUMBER_PREFERENCE:-cluster.instance.hdd.scale.max.devices}"
MAX_DEVICE_NUMBER_PREFERENCE_DEFAULT="${MAX_DEVICE_NUMBER_PREFERENCE_DEFAULT:-40}"
MAX_FS_SIZE_PREFERENCE="${MAX_FS_SIZE_PREFERENCE:-cluster.instance.hdd.scale.max.size}"
MAX_FS_SIZE_PREFERENCE_DEFAULT="${MAX_FS_SIZE_PREFERENCE_DEFAULT:-16384}"
MIN_DISK_SIZE_PREFERENCE="${MIN_DISK_SIZE_PREFERENCE:-cluster.instance.hdd.scale.disk.min.size}"
MIN_DISK_SIZE_PREFERENCE_DEFAULT="${MIN_DISK_SIZE_PREFERENCE_DEFAULT:-10}"
MAX_DISK_SIZE_PREFERENCE="${MAX_DISK_SIZE_PREFERENCE:-cluster.instance.hdd.scale.disk.max.size}"
MAX_DISK_SIZE_PREFERENCE_DEFAULT="${MAX_DISK_SIZE_PREFERENCE_DEFAULT:-16384}"

if ! is_filesystem_scalable "$MOUNT_POINT" "$SCALABLE_FILESYSTEM_TYPE"
then
  pipe_log_error "Mount point filesystem $MOUNT_POINT cannot be autoscaled."
  exit 1
fi

pipe_log_debug "Starting filesystem $MOUNT_POINT autoscaling process for node $NODE..."
while true
do
  sleep "$MONITORING_DELAY"
  PREFERENCES=$(get_system_preferences "$API" "$API_TOKEN")
  MONITORING_DELAY=$(resolve_system_preference "$PREFERENCES" "$MONITORING_DELAY_PREFERENCE" "$MONITORING_DELAY_PREFERENCE_DEFAULT")
  AUTOSCALING_ENABLED=$(resolve_system_preference "$PREFERENCES" "$AUTOSCALE_PREFERENCE" "$AUTOSCALE_PREFERENCE_DEFAULT")
  THRESHOLD_RATIO=$(resolve_system_preference "$PREFERENCES" "$THRESHOLD_PREFERENCE" "$THRESHOLD_PREFERENCE_DEFAULT")
  THRESHOLD=$(get_fractional_part "$THRESHOLD_RATIO")
  DELTA=$(resolve_system_preference "$PREFERENCES" "$DELTA_PREFERENCE" "$DELTA_PREFERENCE_DEFAULT")
  MAX_DEVICE_NUMBER=$(resolve_system_preference "$PREFERENCES" "$MAX_DEVICE_NUMBER_PREFERENCE" "$MAX_DEVICE_NUMBER_PREFERENCE_DEFAULT")
  MAX_FS_SIZE=$(resolve_system_preference "$PREFERENCES" "$MAX_FS_SIZE_PREFERENCE" "$MAX_FS_SIZE_PREFERENCE_DEFAULT")
  MIN_DISK_SIZE=$(resolve_system_preference "$PREFERENCES" "$MIN_DISK_SIZE_PREFERENCE" "$MIN_DISK_SIZE_PREFERENCE_DEFAULT")
  MAX_DISK_SIZE=$(resolve_system_preference "$PREFERENCES" "$MAX_DISK_SIZE_PREFERENCE" "$MAX_DISK_SIZE_PREFERENCE_DEFAULT")
  if is_true "$AUTOSCALING_ENABLED"
  then
    pipe_log_debug "Filesystem autoscaling capability is enabled."
    CURRENT_USAGE=$(get_current_disk_usage "$MOUNT_POINT")
    if [[ "$CURRENT_USAGE" -ge "$THRESHOLD" ]]
    then
      pipe_log_debug "Filesystem $MOUNT_POINT usage overflows the configured threshold $CURRENT_USAGE% >= $THRESHOLD% and it's capacity will be expanded by the delta of $DELTA."
      MOUNTED_DEVICE_NUMBER=$(get_mounted_devices "$MOUNT_POINT" | wc -l)
      if [[ "$MOUNTED_DEVICE_NUMBER" -ge "$MAX_DEVICE_NUMBER" ]]
      then
        pipe_log_debug "Allowed number of $MAX_DEVICE_NUMBER devices has been already reached for the filesystem $MOUNT_POINT."
        continue
      fi
      TOTAL_SIZE=$(get_total_disk_size "$MOUNT_POINT")
      REQUIRED_SIZE=$(get_required_disk_size "$TOTAL_SIZE" "$DELTA")
      if [[ "$REQUIRED_SIZE" -ge "$MAX_FS_SIZE" ]]
      then
        pipe_log_debug "Filesystem $MOUNT_POINT requested size has reached its max allowed size of ${MAX_FS_SIZE}G."
        REQUIRED_SIZE="$MAX_FS_SIZE"
      fi
      if [[ "$REQUIRED_SIZE" -le "$TOTAL_SIZE" ]]
      then
        pipe_log_debug "Filesystem $MOUNT_POINT cannot be autoscaled even further."
        continue
      fi
      ADDITIONAL_DISK_SIZE=$(get_additional_disk_size "$TOTAL_SIZE" "$REQUIRED_SIZE" "$MIN_DISK_SIZE" "$MAX_DISK_SIZE")
      RESULTING_SIZE=$((TOTAL_SIZE + ADDITIONAL_DISK_SIZE))
      pipe_log_debug "Scaling filesystem $MOUNT_POINT ${TOTAL_SIZE}G + ${ADDITIONAL_DISK_SIZE}G = ${RESULTING_SIZE}G..."
      RUN_ID=$(get_current_run_id "$API" "$API_TOKEN" "$NODE")
      if [[ -z "$RUN_ID" ]]
      then
        pipe_log_debug "No run is assigned to the node. Filesystem won't be autoscaled."
        continue
      fi
      if attach_new_disk "$API" "$API_TOKEN" "$RUN_ID" "$ADDITIONAL_DISK_SIZE"
      then
        pipe_log_debug "New disk ${ADDITIONAL_DISK_SIZE}G was attached to the node."
      else
        pipe_log_error "New disk ${ADDITIONAL_DISK_SIZE}G wasn't attached to the node because of the underlying error."
        continue
      fi
      pipe_log_debug "Waiting for the new disk ${ADDITIONAL_DISK_SIZE}G to be available."
      NEW_DEVICE=$(get_new_device "$MOUNT_POINT" "$ADDITIONAL_DISK_SIZE" "$DISK_AVAILABILITY_TIMEOUT")
      if [[ "$NEW_DEVICE" ]]
      then
        pipe_log_debug "New device $NEW_DEVICE associated with the new disk ${ADDITIONAL_DISK_SIZE}G was found."
      else
        pipe_log_error "New device associated with the new disk ${ADDITIONAL_DISK_SIZE}G wasn't found. It may just not be available yet."
        continue
      fi
      if append_disk_device "$MOUNT_POINT" "$NEW_DEVICE"
      then
        pipe_log_debug "New device $NEW_DEVICE was added to the filesystem $MOUNT_POINT."
      else
        pipe_log_error "New device $NEW_DEVICE wasn't added to the filesystem $MOUNT_POINT because of the underlying error."
        continue
      fi
      pipe_log_info "Filesystem $MOUNT_POINT was autoscaled ${TOTAL_SIZE}G + ${ADDITIONAL_DISK_SIZE}G = ${RESULTING_SIZE}G."
    else
      pipe_log_debug "Filesystem $MOUNT_POINT usage satisfies the configured threshold $CURRENT_USAGE% < $THRESHOLD%."
    fi
  else
    pipe_log_debug "Filesystem autoscaling capability is disabled."
  fi
done
