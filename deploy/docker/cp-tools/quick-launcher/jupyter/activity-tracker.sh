#!/usr/bin/env bash
# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

LAST_ACTIVITY_FILE="/tmp/last_activity"
CPU_UTIL_LIMIT=${PROCESS_ACTIVITY_TRACKING_CPU_LIM:-0.0}
MEM_UTIL_LIMIT=${PROCESS_ACTIVITY_TRACKING_MEM_LIM:-1.0}
INACTIVITY_TIMEOUT_SEC=${JUPYTER_SERVER_INACTIVITY_TIMEOUT_SEC:-120}
INACTIVITY_NOTIFICATION_DELAY_SEC=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_NOTIFICATION_DELAY_SEC:-1800}
INACTIVITY_NOTIFICATION_PERIOD_SEC=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_NOTIFICATION_PERIOD_SEC:-60}
ACTIVITY_CHECKING_PERIOD=${PROCESS_ACTIVITY_TRACKING_PERIOD_SEC:-5}
JUPYTER_ACTIVITY_TASK="Jupyter Activity"
TRACKED_PROCESSES=(`echo ${PROCESS_ACTIVITY_TRACKING_LIST:-bash,R} | tr ',' ' '`)
NEAR_TIMEOUT_EXCEEDING=0
NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC=0;
SGE_TARGET_JOB_STATE_REGEX=${PROCESS_ACTIVITY_TRACKING_SGE_TARGET_JOB_STATE_REGEX:-r|qw}
declare -a pids_history

check_sge_cluster_mode
SGE_CLUSTER_MODE=$?

function log_activity() {
  echo "[$(date '+%Y-%m-%d/%H:%M:%S%:z')] $1"
  if [ $NEAR_TIMEOUT_EXCEEDING -eq 1 ]; then
    pipe_log_info "User activity detected recently: inactivity timer is reset" "$JUPYTER_ACTIVITY_TASK"
    NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC=0
    NEAR_TIMEOUT_EXCEEDING=0
  fi
}

function check_sge_cluster_mode() {
  if [ "$CP_CAP_SGE" == "true" ];then
      which qstat >/dev/null 2>&1
      if [ $? -eq 0 ]; then
        pipe_log_info "SGE environment detected"
        return 1
      fi
  fi
  return 0
}

function get_last_saved_activity() {
  cat $LAST_ACTIVITY_FILE
}

function update_last_activity_time() {
  date +%s > $LAST_ACTIVITY_FILE
}

function consider_process_active() {
  cpu_util=$1
  mem_util=$2
  if [ $(echo "$cpu_util>$CPU_UTIL_LIMIT"| bc) -ne 0 ] || [ $(echo "$mem_util>$MEM_UTIL_LIMIT"| bc) -ne 0 ]; then
    echo "{CPU:$cpu_util, MEM:$mem_util}"
    return 0
  fi
  return 1
}

function track_idle_timeout() {
  last_activity_epoch_sec=$(get_last_saved_activity)
  current_dt_epoch_sec=$(date +%s)
  idle_period_sec="$(($current_dt_epoch_sec-$last_activity_epoch_sec))"
  inactivity_notification_timeout="$(($INACTIVITY_TIMEOUT_SEC-$INACTIVITY_NOTIFICATION_DELAY_SEC+$NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC))"

  if [ $idle_period_sec -gt $INACTIVITY_TIMEOUT_SEC ]; then
    pipe_log_fail "No user activity detected for a long period of time, run will be stopped within a minute" "$JUPYTER_ACTIVITY_TASK"
    pipe stop -y ${RUN_ID}
    exit 0
  elif [ $idle_period_sec -gt $inactivity_notification_timeout ]; then
    NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC=$(($NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC+$INACTIVITY_NOTIFICATION_PERIOD_SEC))
    NEAR_TIMEOUT_EXCEEDING=1
  fi
}

function analyze_jupyter_server_activity() {
  jupyter_server_pid=$(pgrep -f jupyter-notebook)
  if [ $jupyter_server_pid -gt 0 ]; then
    echo "Active Jupyter server with [$jupyter_server_pid] pid is detected"
    update_last_activity_time
    return 0
  fi
  return 1
}

function analyze_sge_activity() {
    active_target_jobs=$(qstat \
    | awk '{if (NR>2) {print $5}}' \
    | grep -E $SGE_TARGET_JOB_STATE_REGEX \
    | wc -l)
    if [ $active_target_jobs -gt 0 ];then
      log_activity "[$active_target_jobs] active target [$SGE_TARGET_JOB_STATE_REGEX] SGE tasks detected"
      return 0
    else
      return 1
    fi
}

function analyze_user_activity() {
  user=$1
  full_processes_table=$(ps --no-headers -U $user -o comm,pid,%cpu,%mem,ppid)
  zombie_processes=$(echo "$full_processes_table" | grep \<defunct\>)
  if [ ! -z "$zombie_processes" ]; then
    echo "$zombie_processes" | awk -v timestamp="$(date '+%Y-%m-%d/%H:%M:%S%:z')" '{ printf "[%s] Defunct process (%s,pid=%d,ppid=%d) detected\n", timestamp, $1, $3, $6 }'
  fi
  pids_and_processes=( $(echo "$full_processes_table" | grep -v \<defunct\>) )
  length=${#pids_and_processes[@]}
  declare -a tracked_processes_pids
  declare -a pids_snapshot
  new_process_detected=-1
  for (( i = 0; i < length; i=i+5 )); do
    process_name="${pids_and_processes[i]}"
    process_pid="${pids_and_processes[i+1]}"
    pids_snapshot+=($process_pid)
    if [ $new_process_detected -eq -1 ]; then
      if [[ ! "${pids_history[@]}" =~ "$process_pid" ]]; then
        new_process_detected=$i
      fi
      if [[ "${TRACKED_PROCESSES[@]}" =~ "$process_name" ]] || [ "$process_name" == "jupyter" ]; then
        tracked_processes_pids+=($process_pid)
      fi
    fi
  done

  unset pids_history
  for (( i = 0; i < ${#pids_snapshot[@]}; i=i+1 )); do
    pids_history+=(${pids_snapshot[i]})
  done
  if [ $new_process_detected -ne -1 ]; then
    log_activity "Newly spawned process (${pids_and_processes[$new_process_detected]}, pid=${pids_and_processes[$new_process_detected+1]}) detected"
    update_last_activity_time
    return
  fi

  for (( i = 0; i < length; i=i+5 )); do
    process_name="${pids_and_processes[i]}"
    process_pid="${pids_and_processes[i+1]}"
    cpu_util="${pids_and_processes[i+2]}"
    mem_util="${pids_and_processes[i+3]}"
    parent_pid="${pids_and_processes[i+4]}"
    if [[ "${TRACKED_PROCESSES[@]}" =~ "$process_name" ]] || [[ "${tracked_processes_pids[@]}" =~ "$parent_pid" ]]; then
      activity_summary=$(consider_process_active $cpu_util $mem_util)
      if [ $? -eq 0 ]; then
        log_activity "Updating last activity time: process ($process_name, pid=$process_pid) has utilization $activity_summary"
        update_last_activity_time
        return
      fi
    fi
  done
  analyze_jupyter_server_activity
  if [ $? -eq 0 ]; then
    log_activity "Jupyter server activity detected"
    return
  fi
  if [ $SGE_CLUSTER_MODE -eq 1 ];then
    analyze_sge_activity
    if [ $? -eq 0 ]; then
      update_last_activity_time
      return
    fi
  fi
  track_idle_timeout
}

update_last_activity_time
log_activity "Starting activity tracking"

process_owner=${OWNER}
while true; do
    analyze_user_activity $process_owner
    sleep $ACTIVITY_CHECKING_PERIOD
done
