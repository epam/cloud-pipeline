#!/usr/bin/env bash
# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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
GUNICORN_LOG_FILE="$LOG_DIR_PATH/gunicorn_${RUN_ID}.log"
CPU_UTIL_LIMIT=${PROCESS_ACTIVITY_TRACKING_CPU_LIM:-0.0}
MEM_UTIL_LIMIT=${PROCESS_ACTIVITY_TRACKING_MEM_LIM:-1.0}
INACTIVITY_TIMEOUT_SEC=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_TIMEOUT_SEC:-43200}
INACTIVITY_NOTIFICATION_DELAY_SEC=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_NOTIFICATION_DELAY_SEC:-1800}
INACTIVITY_NOTIFICATION_PERIOD_SEC=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_NOTIFICATION_PERIOD_SEC:-60}
ACTIVITY_CHECKING_PERIOD=${PROCESS_ACTIVITY_TRACKING_PERIOD_SEC:-5}
DASH_ACTIVITY_TASK="Dash Activity"
TRACKED_PROCESSES=(`echo ${PROCESS_ACTIVITY_TRACKING_LIST:-bash,R} | tr ',' ' '`)
HAS_ACTIVE_EDITOR_TABS=0
NEAR_TIMEOUT_EXCEEDING=0
NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC=0;
SGE_TARGET_JOB_STATE_REGEX=${PROCESS_ACTIVITY_TRACKING_SGE_TARGET_JOB_STATE_REGEX:-r|qw}
declare -a pids_history

check_sge_cluster_mode
SGE_CLUSTER_MODE=$?

function log_activity() {
  echo "[$(date '+%Y-%m-%d/%H:%M:%S%:z')] $1"
  if [ $NEAR_TIMEOUT_EXCEEDING -eq 1 ]; then
    pipe_log_info "User activity detected recently: inactivity timer is reset" "$DASH_ACTIVITY_TASK"
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

function consider_gunicorn_server_active() {
  if [ -f $GUNICORN_LOG_FILE ]; then
      last_activity_from_gunicorn_server=$(stat -c "%Y" $GUNICORN_LOG_FILE | sort | tail -n1)
      if [ ! -z "$last_activity_from_gunicorn_server" ]; then
        last_saved_activity=$(get_last_saved_activity)
        if [ $last_activity_from_gunicorn_server -gt $last_saved_activity ]; then
          echo "$last_activity_from_gunicorn_server" > $LAST_ACTIVITY_FILE;
          return 0
        fi
      fi
  fi
  return 1
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
    pipe_log_fail "Idle timeout exceeded, stopping the run" "$DASH_ACTIVITY_TASK"
    pipe stop -y ${RUN_ID}
    exit 0
  elif [ $idle_period_sec -gt $inactivity_notification_timeout ]; then
    minutes_till_exceeding=$((($INACTIVITY_NOTIFICATION_DELAY_SEC-$NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC)/60))
    if [ $minutes_till_exceeding -lt 1 ]; then
      message_suffix="within a minute"
    else
      message_suffix="approximately in $minutes_till_exceeding minutes"
    fi
    pipe_log_warn "No user activity detected for a long period of time, run will be stopped $message_suffix" "$DASH_ACTIVITY_TASK"
    NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC=$(($NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC+$INACTIVITY_NOTIFICATION_PERIOD_SEC))
    NEAR_TIMEOUT_EXCEEDING=1
  fi
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
      if [[ "${TRACKED_PROCESSES[@]}" =~ "$process_name" ]] || [ "$process_name" == "gunicorn" ]; then
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
    if [ $process_name == "gunicorn" ]; then
      consider_gunicorn_server_active
      if [ $? -eq 0 ]; then
        log_activity "Updating last activity time: gunicorn server has currently executing process"
        update_last_activity_time
        return
      fi
      continue
    fi

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

process_owner=$( stat -c '%U' ${OWNER_HOME} )
while true; do
    analyze_user_activity $process_owner
    sleep $ACTIVITY_CHECKING_PERIOD
done
