#!/bin/bash

SYNC_LOG_FILE="${SYNC_LOG_FILE_NAME:-/opt/git-sync/logs/syncgit.log}"
SYNC_LOG_TIMEOUT_SEC="${SYNC_LOG_TIMEOUT_SEC:-3600}"

_current_time=$(date +%s)
_sync_change_time=$(stat $SYNC_LOG_FILE -c %Y)
_time_diff=$(expr $_current_time - $_sync_change_time)

if [ ! -f "$SYNC_LOG_FILE" ]; then
    echo "[ERROR] Log file $SYNC_LOG_FILE does not exist, Git-Sync process is considered dead"
    exit 1
fi

if [ $_time_diff -gt $SYNC_LOG_TIMEOUT_SEC ]; then
   echo "[ERROR] Log file $SYNC_LOG_FILE was not updated for $SYNC_LOG_TIMEOUT_SEC seconds, Git-Sync process is considered dead"
   exit 1
fi
