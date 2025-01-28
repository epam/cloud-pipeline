#!/bin/bash

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

export CP_NF_WEBLOG_HANDLER_PORT=8080
export CP_NF_WEBLOG_HANDLER_VERBOSE=0
export CP_NF_WEBLOG_HANDLER_SYNC_BATCH_SIZE=10
export CP_NF_WEBLOG_HANDLER_SYNC_BATCH_TIMEOUT=60
export CP_NF_WEBLOG_HANDLER_PID_FILE=${CP_NF_WEBLOG_HANDLER_PID_FILE:-/var/run/nf_weblog_handler.pid}
export CP_NF_WEBLOG_HANDLER_LOG_FILE=${CP_NF_WEBLOG_HANDLER_LOG_FILE:-/var/log/nf_weblog_handler.log}
export CP_NF_WEBLOG_HANDLER_LOCATION=${CP_NF_WEBLOG_HANDLER_LOCATION:-/opt/nf-weblog-handler}

parse_options "$@"

if [ "$CP_NF_WEBLOG_HANDLER_START" == 1 ] && [ "$CP_NF_WEBLOG_HANDLER_STOP" == 1 ]; then
    echo "[ERROR] Options --start --stop can't be used at the same time."
    exit 14
fi

if [ -z "$CP_NF_WEBLOG_HANDLER_START" ] && [ -z "$CP_NF_WEBLOG_HANDLER_STOP" ] && [ -z "$CP_NF_WEBLOG_HANDLER_CHECK" ]; then
    echo "[ERROR] Options one of the options: --start/--stop/--check should be provided."
    exit 14
fi

if [ "$CP_NF_WEBLOG_HANDLER_START" == 1 ]; then
    echo "Enabling Nextflow weblog handler..."

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
        exit 0
    else
        echo "[ERROR] problem with running Nextflow weblog handler. Please review the logs at $CP_NF_WEBLOG_HANDLER_LOG_FILE"
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
            exit 0
        else
            echo "Can't find Nextflow event handler process by PID: ${_process_pid} ..."
            exit 14
        fi
    fi

    echo "Can't find Nextflow event handler PID file ${CP_NF_WEBLOG_HANDLER_PID_FILE} ..."
    exit 14
fi

