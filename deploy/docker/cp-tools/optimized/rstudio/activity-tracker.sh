#!/usr/bin/env bash
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

R_USER_HOME="${R_USER_HOME:-$OWNER_HOME}"
LAST_ACTIVITY_FILE="/tmp/last_activity"
RSESSSION_EXEC_STATUS_FILE="${R_USER_HOME}/.rstudio/sessions/active/session-*/properites/executing"
RSTUDIO_HISTORY_FILE="${R_USER_HOME}/.rstudio/history_database"
RSTUDIO_EDITOR_ACTIVITY_FOLDER="${R_USER_HOME}/.rstudio/sources/s-*"
CPU_UTIL_LIMIT=${PROCESS_ACTIVITY_TRACKING_CPU_LIM:-0.0}
MEM_UTIL_LIMIT=${PROCESS_ACTIVITY_TRACKING_MEM_LIM:-1.0}
INACTIVITY_TIMEOUT_SEC=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_TIMEOUT_SEC:-43200}
INACTIVITY_NOTIFICATION_DELAY_SEC=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_NOTIFICATION_DELAY_SEC:-1800}
INACTIVITY_NOTIFICATION_PERIOD_SEC=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_NOTIFICATION_PERIOD_SEC:-60}
ACTIVITY_CHECKING_PERIOD=${PROCESS_ACTIVITY_TRACKING_PERIOD_SEC:-5}
RSTUDIO_ACTIVITY_TASK="RStudio Activity"
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
    pipe_log_info "User activity detected recently: inactivity timer is reset" "$RSTUDIO_ACTIVITY_TASK"
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

function consider_rsession_active() {
  rsession_exec_status=0
  if [ -f $RSESSSION_EXEC_STATUS_FILE ]; then
    rsession_exec_status=$(cat $RSESSSION_EXEC_STATUS_FILE)
    if [ $rsession_exec_status -gt 0 ]; then
      return 0
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

function analyze_rsession_cmd_history() {
  last_rstudio_cmd_epoch_sec=0
  if [ -f $RSTUDIO_HISTORY_FILE ] && [ "$(ls -s $RSTUDIO_HISTORY_FILE | awk '{print $1}')" -ne 0 ]; then
    last_rstudio_cmd="$(tail -n 1 $RSTUDIO_HISTORY_FILE)"
    last_rstudio_cmd_timestamp=$(echo "$last_rstudio_cmd" | cut -f1 -d':')
    if [ "$last_rstudio_cmd" ] && [ "$last_rstudio_cmd_timestamp" ] && [[ $last_rstudio_cmd_timestamp =~ ^[0-9]+$ ]]; then
      last_rstudio_cmd_epoch_sec=$(($last_rstudio_cmd_timestamp/1000))
    fi
  fi
  if [ $last_rstudio_cmd_epoch_sec -ne 0 ]; then
    last_saved_activity=$(get_last_saved_activity)
    if [ $last_rstudio_cmd_epoch_sec -gt $last_saved_activity ]; then
      echo "$last_rstudio_cmd_epoch_sec" > $LAST_ACTIVITY_FILE;
      return 0
    fi
  fi
  return 1
}

function analyze_rsession_editor_activity() {
    if [ ! -d $RSTUDIO_EDITOR_ACTIVITY_FOLDER ]; then
      if [ $HAS_ACTIVE_EDITOR_TABS -eq 1 ]; then
        update_last_activity_time
        HAS_ACTIVE_EDITOR_TABS=0
        return 0
      fi
      return 1
    fi
    editor_tabs_activity_log_files=$(ls -d  $RSTUDIO_EDITOR_ACTIVITY_FOLDER/* | grep -v 'contents$\|lock_file$')
    if [ ! -z "$editor_tabs_activity_log_files" ]; then
      HAS_ACTIVE_EDITOR_TABS=1
      last_ineraction_with_editor_epoch_sec=$(echo "$editor_tabs_activity_log_files" | xargs stat -c '%Y' | sort | tail -n1)
      last_saved_activity=$(get_last_saved_activity)
      if [ $last_ineraction_with_editor_epoch_sec -gt $last_saved_activity ]; then
        echo "$last_ineraction_with_editor_epoch_sec" > $LAST_ACTIVITY_FILE;
        return 0
      fi
    else
      if [ $HAS_ACTIVE_EDITOR_TABS -eq 1 ]; then
        update_last_activity_time
        HAS_ACTIVE_EDITOR_TABS=0
        return 0
      fi
    fi
    return 1
}

function track_idle_timeout() {
  last_activity_epoch_sec=$(get_last_saved_activity)
  current_dt_epoch_sec=$(date +%s)
  idle_period_sec="$(($current_dt_epoch_sec-$last_activity_epoch_sec))"
  inactivity_notification_timeout="$(($INACTIVITY_TIMEOUT_SEC-$INACTIVITY_NOTIFICATION_DELAY_SEC+$NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC))"

  if [ $idle_period_sec -gt $INACTIVITY_TIMEOUT_SEC ]; then
    pipe_log_fail "Idle timeout exceeded, stopping the run" "$RSTUDIO_ACTIVITY_TASK"
    rstudio-server suspend-all
    rstudio-server stop
    pipe stop -y ${RUN_ID}
    exit 0
  elif [ $idle_period_sec -gt $inactivity_notification_timeout ]; then
    minutes_till_exceeding=$((($INACTIVITY_NOTIFICATION_DELAY_SEC-$NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC)/60))
    if [ $minutes_till_exceeding -lt 1 ]; then
      message_suffix="within a minute"
    else
      message_suffix="approximately in $minutes_till_exceeding minutes"
    fi
    pipe_log_warn "No user activity detected for a long period of time, run will be stopped $message_suffix" "$RSTUDIO_ACTIVITY_TASK"
    NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC=$(($NEXT_INCATIVITY_NOTIFICATION_DELAY_SEC+$INACTIVITY_NOTIFICATION_PERIOD_SEC))
    NEAR_TIMEOUT_EXCEEDING=1
  fi
}

function analyze_shiny_apps_activity_logs() {
  last_activity_from_shiny_apps=$(find /tmp -name 'Rtmp*' -type d -exec sh -c ' stat -c "%Y" "{}"' \; | sort | tail -n1)
  if [ ! -z "$last_activity_from_shiny_apps" ]; then
    last_saved_activity=$(get_last_saved_activity)
    if [ $last_activity_from_shiny_apps -gt $last_saved_activity ]; then
      echo "$last_activity_from_shiny_apps" > $LAST_ACTIVITY_FILE;
      return 0
    fi
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
      if [[ "${TRACKED_PROCESSES[@]}" =~ "$process_name" ]] || [ "$process_name" == "rsession" ]; then
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
    if [ $process_name == "rsession" ]; then
      consider_rsession_active
      if [ $? -eq 0 ]; then
        log_activity "Updating last activity time: rsession has currently executing process"
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
  analyze_rsession_cmd_history
  if [ $? -eq 0 ]; then
    log_activity "New command detected in RStudio history"
    return
  fi
  analyze_rsession_editor_activity
  if [ $? -eq 0 ]; then
    log_activity "User activity in editor detected"
    return
  fi
  analyze_shiny_apps_activity_logs
  if [ $? -eq 0 ]; then
    log_activity "Shiny application activity detected"
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
