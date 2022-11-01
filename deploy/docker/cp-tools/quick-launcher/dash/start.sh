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

if [ -z "$OWNER" ]; then
    echo "[ERROR] Owner of the job is not defined. Exiting"
    exit 1
fi

if [ -z "$APPLICATION_NAME" ]; then
    if [ -z "$FOLDER_APPLICATION_PATH" ]; then
      echo "[ERROR] Dash application name is not defined. Exiting"
      exit 1
    fi
    export APPLICATION_NAME=$(basename $(dirname $FOLDER_APPLICATION_PATH))
fi

if [ -z "$DEFAULT_STORAGE_USER" ]; then
    echo "[WARN] Dash application owner name is not defined. Run owner will be used."
fi

DEFAULT_STORAGE_USER="${DEFAULT_STORAGE_USER:-$OWNER}"
export APP_PATH="/cloud-home/${DEFAULT_STORAGE_USER}/DashApps/${APPLICATION_NAME}"

if [ ! -d "$APP_PATH" ]; then
    echo "[ERROR] Dash application ($APP_PATH) is not mounted to the instance"
    exit 1
fi

DASH_APP_ENTRYPOINT="${DASH_APP_ENTRYPOINT:-app.py}"
if [ ! -f "$APP_PATH/$DASH_APP_ENTRYPOINT" ]; then
    echo "[ERROR] Dash application file ($APP_PATH/$DASH_APP_ENTRYPOINT) is not found in the instance"
    exit 1
fi

user_default_storage_id=$(curl -s -k \
                                -X GET \
                                -H "Authorization: Bearer $API_TOKEN" \
                                "$API/whoami" \
                            | jq -r '.payload.defaultStorageId')
if [ "$user_default_storage_id" ] && [ "$user_default_storage_id" != null ]; then
    user_default_storage_mountpoint=$(curl -s -k \
                                    -X GET \
                                    -H "Authorization: Bearer $API_TOKEN" \
                                    "$API/datastorage/$user_default_storage_id/load" \
                                | jq -r '.payload.mountPoint')
    if [ "$user_default_storage_mountpoint" ] && [ "$user_default_storage_mountpoint" != null ]; then
        if [ -d "$user_default_storage_mountpoint" ]; then
            export LOG_DIR_PATH="${user_default_storage_mountpoint}/DashApps/log"
        else
            echo "[ERROR] User default storage ($user_default_storage_id) is not mounted to the instance at $user_default_storage_mountpoint"
        fi
    else
        echo "[ERROR] User default storage ($user_default_storage_id) mountpoint can not be found"
    fi
else
    echo "[ERROR] User default storage was not found. '/home/${OWNER}/DashApps/log' directory will be used for logs"
    export LOG_DIR_PATH="/home/${OWNER}/DashApps/log"
fi
mkdir -p $LOG_DIR_PATH

export DASH_CPU_NUM="${DASH_CPU_NUM:-$(nproc --all)}"
export worker_processes=$((DASH_CPU_NUM * 2 + 1))
export DASH_PORT=${DASH_PORT:-8050}

# Get Anaconda System Lib path
if [ "$ANACONDA_SYS_LIB" ]; then
    conda_sys_lib_mountpoint=$(curl -s -k \
                                -X GET \
                                -H "Authorization: Bearer $API_TOKEN" \
                                "$API/datastorage/$ANACONDA_SYS_LIB/load" \
                            | jq -r '.payload.mountPoint')
    if [ "$conda_sys_lib_mountpoint" ] && [ "$conda_sys_lib_mountpoint" != null ]; then
        if [ ! -d "$conda_sys_lib_mountpoint" ]; then
            echo "[ERROR] ANACONDA_SYS_LIB ($ANACONDA_SYS_LIB) is not mounted to the instance at $conda_sys_lib_mountpoint"
        fi
    else
        echo "[ERROR] ANACONDA_SYS_LIB ($ANACONDA_SYS_LIB) can not be found"
    fi
else
    echo "[ERROR] ANACONDA_SYS_LIB is not set, no external library is mounted"
fi

export ANACONDA_HOME=${conda_sys_lib_mountpoint:-/opt/local/anaconda}
if [ ! -f "$ANACONDA_HOME/etc/profile.d/conda.sh" ]; then
    echo "[ERROR] Conda environment was not found in the instance's $ANACONDA_HOME path"
    exit 1
fi

if [ -z "$APP_ENVIRONMENT" ]; then
    if [ -f "$APP_PATH/gateway.spec" ]; then
        APP_ENVIRONMENT=$( cat "$APP_PATH/gateway.spec" | jq -r '.app_environment' )
    fi
    if [ -z "$APP_ENVIRONMENT" ]; then
        echo "[WARN] Dash application environment (anaconda env) is not defined. Default 'base' env will be used."
        export APP_ENVIRONMENT="base"
    fi
fi

base_pathname=$(echo $RUN_PRETTY_URL | cut -d';' -f 2)
export DASH_URL_BASE_PATHNAME=${DASH_URL_BASE_PATHNAME:-"/$base_pathname/"}

# Start activity tracker
if [ "$PROCESS_ACTIVITY_TRACKING_ENABLED" == "true" ]; then
    rm -f /var/log/activity-tracker.log
    nohup bash /activity-tracker.sh >> /var/log/activity-tracker.log 2>&1 &
fi

DASH_WSGI_APP="${DASH_WSGI_APP:-app:server}"
DASH_LOG_LEVEL="${DASH_LOG_LEVEL:-debug}"
source $ANACONDA_HOME/etc/profile.d/conda.sh
conda activate ${APP_ENVIRONMENT}
cd $APP_PATH
nohup gunicorn  -b 0.0.0.0:${DASH_PORT} \
                -u $OWNER \
                -w $worker_processes "$DASH_WSGI_APP" \
                --log-level $DASH_LOG_LEVEL >> $LOG_DIR_PATH/gunicorn_${RUN_ID}.log 2>&1 &

pipe_log_success "Gateway App has been started" "InitializeApp"

sleep infinity
