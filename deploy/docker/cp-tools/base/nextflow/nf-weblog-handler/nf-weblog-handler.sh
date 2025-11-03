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

function check_api_response_status {
    local response_json="$1"
    local response_status=$(echo "$response_json" | jq -r ".status")
    local result=0
    if [ "$response_status" == "ERROR" ] || [[ "$response_status" == "40"* ]]; then
        result=1
    fi
    return $result
}

function call_api {
    local api_endpoint="$1"
    local jwt_token="$2"
    local payload="$3"
    local is_file="$4"

    local response=""
    if [ "$is_file" ]; then
        response=$(curl -X POST -k -s -H "Authorization: Bearer $jwt_token" -F "file=@$payload" "${api_endpoint}")
    else
        if [ "$payload" ]; then
            response=$(curl -X POST -k -s -H 'Content-Type: application/json' -H "Authorization: Bearer $jwt_token" -d "$payload" "${api_endpoint}")
        else
            response=$(curl -X GET -k -s -H "Authorization: Bearer $jwt_token" "${api_endpoint}")
        fi
    fi
    echo "$response"
    check_api_response_status "$response"
    return $?
}

function parse_options {
    _UNKNOWN=()
    while [[ $# -gt 0 ]]
    do
    key="$1"

    case $key in
        --start|start)
        export CP_NF_WEBLOG_HANDLER_START=1
        shift # past argument
        ;;
        --stop|stop)
        export CP_NF_WEBLOG_HANDLER_STOP=1
        shift # past argument
        ;;
        --check|check)
        export CP_NF_WEBLOG_HANDLER_CHECK=1
        shift # past argument
        ;;
        -f|--force)
        export CP_NF_WEBLOG_HANDLER_FORCE_OPERATION=1
        shift # past argument
        ;;
        -p|--port)
        export CP_NF_WEBLOG_HANDLER_PORT="$2"
        shift # past argument
        shift # past value
        ;;
        -v|--verbose)
        export CP_NF_WEBLOG_HANDLER_VERBOSE=1
        shift # past argument
        ;;
        -bs|--sync-batch-size)
        export CP_NF_WEBLOG_HANDLER_SYNC_BATCH_SIZE=$2
        shift # past argument
        shift # past value
        ;;
        -bt|--sync-batch-timeout)
        export CP_NF_WEBLOG_HANDLER_SYNC_BATCH_TIMEOUT=$2
        shift # past argument
        shift # past value
        ;;
        -erd|--enable-runtime-data)
        export CP_NF_ENABLE_RUNTIME_DATA_SYNC=1
        shift # past argument
        ;;
        --wait-runtime-data)
        export CP_NF_WAIT_RUNTIME_DATA_SYNC=1
        shift # past argument
        ;;
        -tf|--trace-file-to-sync)
        export CP_NF_TRACE_FILE_TO_SYNC=$2
        shift # past argument
        shift # past value
        ;;
        *)    # unknown option
        _UNKNOWN+=("$1") # save it in an array for later
        shift # past argument
        ;;
    esac
    done

    if [ -n "${_UNKNOWN[*]}" ]; then
        echo "[WARN] There are unknown options: ${_UNKNOWN[*]}"
    fi
}

function expand() { eval "echo \"$1\""; }

function enable_nf_runtime_data_sync() {
    SYNC_RUN_RUNTIME_DATA_TASK="SyncRunRuntimeData"

    _DEFAULT_NUMBER_OF_THREADS=$(( $(nproc) / 2 + 1 ))
    export CP_SYNC_TO_STORAGE_THREADS=${CP_SYNC_TO_STORAGE_THREADS:-$_DEFAULT_NUMBER_OF_THREADS}
    CP_NF_WORKDIR=$(expand "${CP_NF_WORKDIR:-${ANALYSIS_DIR}/work}")
    CP_NF_TRACE_FILE=$(expand "${CP_NF_TRACE_FILE_DIR:-$CP_NF_WORKDIR}/trace.txt")

    # If wasn't defined by user, define with default as trace.txt file
    if [ -z "${CP_NF_TASK_LOOKUP_FILE_PATH}" ]; then
        export CP_NF_TASK_LOOKUP_FILE_PATH="$CP_NF_TRACE_FILE"
    fi

    pipe_log_info "[INFO] Configuring run data sync process..." "$SYNC_RUN_RUNTIME_DATA_TASK"
    run_sync_data_pref_response=$(call_api "$API/preferences/launch.run.sync.runtime.data" "$API_TOKEN")
    if [ $? -ne 0 ]; then
        pipe_log_error "[ERROR] Cannot retrieve 'launch.run.sync.runtime.data' preference, synchronization of nextflow runtime data will not be configured." "$SYNC_RUN_RUNTIME_DATA_TASK"
    fi

    # Configure synchronization for nf trace.txt file
    run_sync_data=$(echo "$run_sync_data_pref_response" | jq '.payload.value' -r)
    export CP_SYNC_TO_STORAGE_TIMEOUT_SEC=$(echo "$run_sync_data" | jq '.syncTimeout // 60' -r)

    nf_trace_sync_config_entry=$(echo "$run_sync_data" | jq '.data.NF_TRACE // ""' -r)
    nf_trace_run_folder_sync_path=$(echo "$nf_trace_sync_config_entry" | jq '.runFolderPathPrefix // ""' -r)
    nf_trace_data_sync_path=$(echo "$nf_trace_sync_config_entry" | jq '.dataPathPrefix // ""' -r)
    if [ -n "$nf_trace_run_folder_sync_path" ]; then
        nf_trace_file_sync_path="${nf_trace_run_folder_sync_path#/}/${RUN_ID}/trace.txt"
        if [ -n "$nf_trace_data_sync_path" ]; then
            nf_trace_file_sync_path="${nf_trace_run_folder_sync_path#/}/${RUN_ID}/${nf_trace_data_sync_path#/}/trace.txt"
        fi
    fi

    sync_to_storage start
    if [ -n "$nf_trace_file_sync_path" ]; then
        pipe_log_info "[INFO] Starting nextflow trace file sync process." "$SYNC_RUN_RUNTIME_DATA_TASK"
        sync_to_storage add "${CP_NF_TRACE_FILE}" "$nf_trace_file_sync_path"
    fi

    # Configure synchronization for nf task workdirs
    pipe_log_info "[INFO] Starting nextflow task workdir sync process." "$SYNC_RUN_RUNTIME_DATA_TASK"
    nf_task_sync_config_entry=$(echo "$run_sync_data" | jq '.data.NF_TASK // ""' -r)
    nf_task_run_folder_sync_path=$(echo "$nf_task_sync_config_entry" | jq '.runFolderPathPrefix // ""' -r)
    nf_task_data_sync_path=$(echo "$nf_task_sync_config_entry" | jq '.dataPathPrefix // ""' -r)

    if [ -n "$nf_task_sync_config_entry" ] && [ -n "$nf_task_run_folder_sync_path" ]; then
        nf_task_file_sync_path="${nf_task_run_folder_sync_path#/}/${RUN_ID}"
        if [ -n "$nf_task_data_sync_path" ]; then
            nf_task_file_sync_path="${nf_task_run_folder_sync_path#/}/${RUN_ID}/${nf_task_data_sync_path#/}"
        fi

        nf_sync_to_storage_processed_task_file="/tmp/sync_to_storage_processed_nf_task"
        touch $nf_sync_to_storage_processed_task_file
        while true; do
            sleep "$(( CP_SYNC_TO_STORAGE_TIMEOUT_SEC / 2 ))"

            if [ -f "${CP_NF_TASK_LOOKUP_FILE_PATH}" ] ; then
                tail -n +2 "${CP_NF_TASK_LOOKUP_FILE_PATH}" | while read -r task
                do
                    task_hash=$(echo "$task" | awk '{ print $2 }')
                    if [ -n "$task_hash" ] && ! grep -q -x -F "$task_hash" $nf_sync_to_storage_processed_task_file ; then
                        task_workdir=$(realpath ${CP_NF_WORKDIR}/$task_hash*)
                        if [ -d "$task_workdir" ]; then
                             [ -f "$task_workdir/.command.out" ] && sync_to_storage add "$task_workdir/.command.out" "$nf_task_file_sync_path/$task_hash/.command.out"
                             [ -f "$task_workdir/.command.err" ] && sync_to_storage add "$task_workdir/.command.err" "$nf_task_file_sync_path/$task_hash/.command.err"
                             [ -f "$task_workdir/.command.log" ] && sync_to_storage add "$task_workdir/.command.log" "$nf_task_file_sync_path/$task_hash/.command.log"
                             [ -f "$task_workdir/.command.sh" ] && sync_to_storage add "$task_workdir/.command.sh" "$nf_task_file_sync_path/$task_hash/.command.sh"
                             [ -f "$task_workdir/.command.run" ] && sync_to_storage add "$task_workdir/.command.run" "$nf_task_file_sync_path/$task_hash/.command.run"
                             [ -f "$task_workdir/.command.trace" ] && sync_to_storage add "$task_workdir/.command.trace" "$nf_task_file_sync_path/$task_hash/.command.trace"
                             [ -f "$task_workdir/.command.begin" ] && sync_to_storage add "$task_workdir/.command.begin" "$nf_task_file_sync_path/$task_hash/.command.begin"
                             [ -f "$task_workdir/.exitcode" ] && sync_to_storage add "$task_workdir/.exitcode" "$nf_task_file_sync_path/$task_hash/.exitcode"
                             if echo "$task" | grep -q -E "COMPLETE|FAILED|CACHED|ABORTED" ; then
                                 sync_to_storage remove "$task_workdir/.command.out"
                                 sync_to_storage remove "$task_workdir/.command.err"
                                 sync_to_storage remove "$task_workdir/.command.log"
                                 sync_to_storage remove "$task_workdir/.command.sh"
                                 sync_to_storage remove "$task_workdir/.command.trace"
                                 sync_to_storage remove "$task_workdir/.command.run"
                                 sync_to_storage remove "$task_workdir/.command.begin"
                                 sync_to_storage remove "$task_workdir/.exitcode"
                                 echo "$task_hash" >> "$nf_sync_to_storage_processed_task_file"
                             fi
                        fi
                    fi
                done
            else
                pipe_log_warn "[WARN] There is no ${CP_NF_TASK_LOOKUP_FILE_PATH} at the moment, skip runtime data sync iteration." "$SYNC_RUN_RUNTIME_DATA_TASK"
            fi
        done
    else
        pipe_log_warn "[ERROR] NF_TASK entry for 'launch.run.sync.runtime.data' is not configured. Task runtime data files won't be synced!" "$SYNC_RUN_RUNTIME_DATA_TASK"
    fi
}

export CP_NF_WEBLOG_HANDLER_PORT=8080
export CP_NF_WEBLOG_HANDLER_VERBOSE=0
export CP_NF_WEBLOG_HANDLER_SYNC_BATCH_SIZE=10
export CP_NF_WEBLOG_HANDLER_SYNC_BATCH_TIMEOUT=60
export CP_NF_WEBLOG_HANDLER_PID_FILE=${CP_NF_WEBLOG_HANDLER_PID_FILE:-/var/run/nf_weblog_handler.pid}
export CP_NF_WEBLOG_HANDLER_LOG_FILE=${CP_NF_WEBLOG_HANDLER_LOG_FILE:-/var/log/nf_weblog_handler.log}
export CP_NF_RUNTIME_DATA_SYNC_PID_FILE=${CP_NF_RUNTIME_DATA_SYNC_PID_FILE:-/var/run/nf_runtime_data_sync.pid}
export CP_NF_RUNTIME_DATA_SYNC_LOG_FILE=${CP_NF_RUNTIME_DATA_SYNC_LOG_FILE:-/var/run/nf_runtime_data_sync.log}
export CP_NF_WEBLOG_HANDLER_LOCATION=${CP_NF_WEBLOG_HANDLER_LOCATION:-/opt/nf-weblog-handler}

parse_options "$@"

if [ "$CP_NF_WEBLOG_HANDLER_START" == 1 ] && [ "$CP_NF_WEBLOG_HANDLER_STOP" == 1 ]; then
    echo "[ERROR] Options --start --stop can't be used at the same time."
    exit 14
fi

if [ -z "$CP_NF_WEBLOG_HANDLER_START" ] && [ -z "$CP_NF_WEBLOG_HANDLER_STOP" ] && [ -z "$CP_NF_WEBLOG_HANDLER_CHECK" ] && [ -z "$CP_NF_ENABLE_RUNTIME_DATA_SYNC" ] && [ -z "$CP_NF_WAIT_RUNTIME_DATA_SYNC" ]; then
    echo "[ERROR] One of the options: --start/--stop/--check/--enable-runtime-data/--wait-runtime-data should be provided."
    exit 14
fi

if [ "$CP_NF_WEBLOG_HANDLER_START" == 1 ]; then
    echo "Enabling Nextflow weblog handler..."

    if [ -n "${CP_NF_TASK_LOOKUP_FILE}" ]; then
        CP_NF_TASK_LOOKUP_FILE_PATH=$(expand "${CP_NF_TRACE_FILE_DIR:-$CP_NF_WORKDIR}/${CP_NF_TASK_LOOKUP_FILE}")
        echo "Configuring CP_NF_TASK_LOOKUP_FILE_PATH as: $CP_NF_TASK_LOOKUP_FILE_PATH."
        # Will be used in nf-weblog-handler to populate this path
        export CP_NF_TASK_LOOKUP_FILE_PATH
    fi

    if [ -f "$CP_NF_WEBLOG_HANDLER_PID_FILE" ]; then
        _process_pid=$(cat "$CP_NF_WEBLOG_HANDLER_PID_FILE")
        if ps -p "$_process_pid" > /dev/null; then
            echo "Nextflow weblog handler already running with pid ${_process_pid} ..."
            if [ "$CP_NF_WEBLOG_HANDLER_FORCE_OPERATION" == 1 ]; then
                echo "Killing ${_process_pid} process before continue ..."
                kill "${_process_pid}"
                rm -f "$CP_NF_WEBLOG_HANDLER_PID_FILE"
            else
                echo "If you want forcefully rerun process, specify -f/--force flag. Exiting."
                exit 14
            fi
        fi
    fi

    python2 "$CP_NF_WEBLOG_HANDLER_LOCATION/app.py"  &> "$CP_NF_WEBLOG_HANDLER_LOG_FILE" &

    echo "$!" > "$CP_NF_WEBLOG_HANDLER_PID_FILE"
    _process_pid=$(cat "$CP_NF_WEBLOG_HANDLER_PID_FILE")
    # Waiting for process to start up
    sleep 3
    if ps -p "$_process_pid" > /dev/null; then
        echo "Nextflow weblog handler has been started with PID: ${_process_pid}, review logs in $CP_NF_WEBLOG_HANDLER_LOG_FILE"
    else
        echo "[ERROR] problem with running Nextflow weblog handler. Please review the logs at $CP_NF_WEBLOG_HANDLER_LOG_FILE"
        exit 14
    fi
fi

if [ "$CP_NF_ENABLE_RUNTIME_DATA_SYNC" == 1 ]; then

    if [ -f "$CP_NF_RUNTIME_DATA_SYNC_PID_FILE" ]; then
        _process_pid=$(cat "$CP_NF_RUNTIME_DATA_SYNC_PID_FILE")
        if ps -p "$_process_pid" > /dev/null; then
            echo "Nextflow runtime data sync already running with pid ${_process_pid} ..."
            if [ "$CP_NF_WEBLOG_HANDLER_FORCE_OPERATION" == 1 ]; then
                echo "Killing ${_process_pid} process before continue ..."
                kill "${_process_pid}"
                rm -f "$CP_NF_WEBLOG_HANDLER_PID_FILE"
            else
                echo "If you want forcefully rerun process, specify -f/--force flag. Exiting."
                exit 14
            fi
        fi
    fi

    export -f enable_nf_runtime_data_sync
    export -f call_api
    export -f check_api_response_status
    nohup bash -c enable_nf_runtime_data_sync &> "$CP_NF_RUNTIME_DATA_SYNC_LOG_FILE" &
    echo "$!" > "$CP_NF_RUNTIME_DATA_SYNC_PID_FILE"
fi

if [ "$CP_NF_WAIT_RUNTIME_DATA_SYNC" == 1 ]; then
    CP_NF_RUNTIME_DATA_SYNC_PERIOD=${CP_NF_RUNTIME_DATA_SYNC_PERIOD:-300}
    CP_NF_RUNTIME_DATA_SYNC_BACKOFF_SEC=10
    if [ "$CP_NF_WEBLOG_HANDLER_ENABLED" == "1" ] && sync_to_storage check &> /dev/null ; then
        echo "Nextflow process finished..."

        echo "Sending flash request to the nf weblog handler..."
        curl -s -X POST "http://localhost:$CP_NF_WEBLOG_HANDLER_PORT/nextflow/event/flush" &> /dev/null

        if [ -n "$CP_NF_TRACE_FILE_TO_SYNC" ]; then
          echo "Creating events from trace file and send..."
          curl -s -X GET "http://localhost:$CP_NF_WEBLOG_HANDLER_PORT/nextflow/event/tracefile?path=$CP_NF_TRACE_FILE_TO_SYNC" &> /dev/null
        fi

        echo "Waiting for synchronization of nextflow task runtime files... "
        _wait_count=0
        while [ "$_wait_count" -lt "$((CP_NF_RUNTIME_DATA_SYNC_PERIOD / CP_NF_RUNTIME_DATA_SYNC_BACKOFF_SEC))" ]; do
          _wait_count=$((_wait_count + 1))
          _files_to_sync=$(sync_to_storage count)

          # "$_files_to_sync" -eq "1" because there always will be trace file in sync
          if [ "$?" -eq 0 ] && [ "$_files_to_sync" -eq "1" ]; then
              echo "Synchronization of nextflow task runtime files finished."
              exit 0
          fi
          sleep "$CP_NF_RUNTIME_DATA_SYNC_BACKOFF_SEC"
        done
        echo "[WARN] Synchronization of nextflow task runtime files can't finished in configured time, not all files would be available in statistic report. Exiting!"
        exit 14
    else
        echo "[WARN] There is no Nextflow runtime data sync process running..."
        exit 14
    fi
fi

if [ "$CP_NF_WEBLOG_HANDLER_STOP" == 1 ]; then
    echo "Stopping Nextflow weblog handler..."

    if [ -f "$CP_NF_WEBLOG_HANDLER_PID_FILE" ]; then
        _process_pid=$(cat "$CP_NF_WEBLOG_HANDLER_PID_FILE")
        if ps -p "$_process_pid" > /dev/null; then
            echo "Killing ${_process_pid} process ..."
            kill "${_process_pid}"
            rm -f "$CP_NF_WEBLOG_HANDLER_PID_FILE"
            exit 0
        else
            echo "Can't find process ${_process_pid} ..."
            exit 14
        fi
    fi

    echo "Can't find PID file ${CP_NF_WEBLOG_HANDLER_PID_FILE} ..."
    exit 14
fi

if [ "$CP_NF_WEBLOG_HANDLER_CHECK" == 1 ]; then
    echo "Checking Nextflow weblog handler status ..."

    if [ -f "$CP_NF_WEBLOG_HANDLER_PID_FILE" ]; then
        _process_pid=$(cat "$CP_NF_WEBLOG_HANDLER_PID_FILE")
        if ps -p "$_process_pid" > /dev/null; then
            echo "Nextflow event handler alive with PID: ${_process_pid} ..."
        else
            echo "Can't find Nextflow event handler process by PID: ${_process_pid} ..."
            exit 14
        fi
    else
        echo "Can't find Nextflow event handler PID file ${CP_NF_WEBLOG_HANDLER_PID_FILE} ..."
        exit 14
    fi
fi

