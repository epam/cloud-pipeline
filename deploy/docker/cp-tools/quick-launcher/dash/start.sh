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

if [ -z "$FOLDER_APPLICATION_PATH" ]; then
    echo "[ERROR] FOLDER_APPLICATION_PATH is not defined. Exiting"
    exit 1
fi

if [ -z "$APPLICATION_NAME" ]; then
    export APPLICATION_NAME=$(basename $(dirname $FOLDER_APPLICATION_PATH))
fi

export DEFAULT_STORAGE_USER=$(echo "$FOLDER_APPLICATION_PATH" | cut -d "/" -f1)
if [ -z "$DEFAULT_STORAGE_USER" ] || [ "$DEFAULT_STORAGE_USER" == null ]; then
    echo "[WARN] Dash application owner name was not determined from 'FOLDER_APPLICATION_PATH': $FOLDER_APPLICATION_PATH. Run owner will be used."
    export DEFAULT_STORAGE_USER=$OWNER
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
            echo "[WARN] ANACONDA_SYS_LIB ($ANACONDA_SYS_LIB) is not mounted to the instance at $conda_sys_lib_mountpoint"
        fi
    else
        echo "[WARN] ANACONDA_SYS_LIB ($ANACONDA_SYS_LIB) can not be found"
    fi
else
    echo "[WARN] ANACONDA_SYS_LIB is not set, no external library is mounted"
fi
if [ "$ANACONDA_SYS_LIB_PREFIX" ] && [ "$ANACONDA_SYS_LIB_PREFIX" != null ] && [ "$conda_sys_lib_mountpoint" != null ]; then
    conda_sys_lib_mountpoint="$conda_sys_lib_mountpoint/${ANACONDA_SYS_LIB_PREFIX#/}"
fi

if [ "$FOLDER_APPLICATION_STORAGE" ]; then
    folder_app_storage_mountpoint=$(curl -s -k \
                                -X GET \
                                -H "Authorization: Bearer $API_TOKEN" \
                                "$API/datastorage/$FOLDER_APPLICATION_STORAGE/load" \
                            | jq -r '.payload.mountPoint')
    if [ "$folder_app_storage_mountpoint" ] && [ "$folder_app_storage_mountpoint" != null ]; then
        if [ ! -d "$folder_app_storage_mountpoint" ]; then
            echo "[WARN] FOLDER_APPLICATION_STORAGE ($FOLDER_APPLICATION_STORAGE) is not mounted to the instance at $folder_app_storage_mountpoint"
        fi
    else
        echo "[WARN] FOLDER_APPLICATION_STORAGE ($FOLDER_APPLICATION_STORAGE) can not be found"
    fi
else
    echo "[WARN] FOLDER_APPLICATION_STORAGE is not set"
fi
export FOLDER_STORAGE_MOUNT_POINT=${folder_app_storage_mountpoint%/}
if [ "$FOLDER_APPLICATION_PATH" ]; then
    export APP_PATH=$FOLDER_STORAGE_MOUNT_POINT/$(dirname $FOLDER_APPLICATION_PATH)
    if [ ! -d "$APP_PATH" ]; then
        echo "[ERROR] Dash application ($APP_PATH) is not mounted to the instance. Check FOLDER_APPLICATION_PATH env ($FOLDER_APPLICATION_PATH)"
        exit 1
    fi
fi

DASH_APP_ENTRYPOINT="${DASH_APP_ENTRYPOINT:-app.py}"
if [ ! -f "$APP_PATH/$DASH_APP_ENTRYPOINT" ]; then
    echo "[ERROR] Dash application file ($APP_PATH/$DASH_APP_ENTRYPOINT) is not found in the instance"
    exit 1
fi

if [ -z "$RUN_PRETTY_URL" ]; then
    pipeline_name=$(hostname | awk '{print tolower($0)}')
    base_pathname="$pipeline_name-$DASH_PORT-0"
else
    base_pathname=$(echo $RUN_PRETTY_URL | cut -d';' -f 2)
fi
export DASH_URL_BASE_PATHNAME=${DASH_URL_BASE_PATHNAME:-"/$base_pathname/"}

# Start activity tracker
if [ "$PROCESS_ACTIVITY_TRACKING_ENABLED" == "true" ]; then
    rm -f /var/log/activity-tracker.log
    nohup bash /activity-tracker.sh >> /var/log/activity-tracker.log 2>&1 &
fi

if [ -z "$APP_ENVIRONMENT" ]; then
    if [ -f "$APP_PATH/gateway.spec" ]; then
        APP_ENVIRONMENT=$( cat "$APP_PATH/gateway.spec" | jq -r '.app_environment' )
    fi
    if [ -d "$APP_PATH/.conda/envs" ]; then
        export ENV_PATH=$(find "$APP_PATH/.conda/envs" -maxdepth 1 -type d | tail -n1)
        export APP_ENVIRONMENT=$(basename $ENV_PATH)
    fi
    if [ -z "$APP_ENVIRONMENT" ] || [ "$APP_ENVIRONMENT" == null ]; then
        echo "[WARN] Dash application environment (anaconda env) is not defined. Default 'base' env will be used."
        export APP_ENVIRONMENT="base"
    fi
fi

DEFAULT_ANACONDA_HOME="/opt/local/anaconda"
if [ -d "$APP_PATH/.conda/envs" ]; then
    export ANACONDA_HOME="$APP_PATH/.conda/envs/$APP_ENVIRONMENT"
elif [ "$conda_sys_lib_mountpoint" ] && [ "$conda_sys_lib_mountpoint" != null ]; then
    export ANACONDA_HOME=${conda_sys_lib_mountpoint}
elif [ -d "$FOLDER_STORAGE_MOUNT_POINT/$DEFAULT_STORAGE_USER/.conda/envs" ]; then
    export ANACONDA_HOME="$FOLDER_STORAGE_MOUNT_POINT/$DEFAULT_STORAGE_USER/.conda/envs/$APP_ENVIRONMENT"
else
    export ANACONDA_HOME=${ANACONDA_HOME:-$DEFAULT_ANACONDA_HOME}
fi
if [ ! -d "$ANACONDA_HOME" ]; then
    echo "[ERROR] Conda environment was not found in the instance's $ANACONDA_HOME path"
    exit 1
fi
if [ -d "$APP_PATH/.conda/envs" ] && [ -d "${ANACONDA_HOME}" ]; then
    export CONDA_ENVS_PATH="$APP_PATH/.conda/envs"
    source $DEFAULT_ANACONDA_HOME/etc/profile.d/conda.sh
    conda activate ${ANACONDA_HOME}
elif [ -d "$FOLDER_STORAGE_MOUNT_POINT/$DEFAULT_STORAGE_USER/.conda/envs/" ] && [ -d "${ANACONDA_HOME}" ]; then
    export CONDA_ENVS_PATH="$FOLDER_STORAGE_MOUNT_POINT/$DEFAULT_STORAGE_USER/.conda/envs"
    source $DEFAULT_ANACONDA_HOME/etc/profile.d/conda.sh
    conda activate ${ANACONDA_HOME}
else
    source $ANACONDA_HOME/etc/profile.d/conda.sh
    conda activate ${APP_ENVIRONMENT}
fi
echo "[INFO] ANACONDA_HOME is $ANACONDA_HOME. APP_ENVIRONMENT is $APP_ENVIRONMENT."

DASH_WSGI_APP="${DASH_WSGI_APP:-app:server}"
DASH_LOG_LEVEL="${DASH_LOG_LEVEL:-debug}"
cd $APP_PATH
nohup $ANACONDA_HOME/bin/gunicorn -b 0.0.0.0:${DASH_PORT} \
                -u $OWNER \
                -w $worker_processes "$DASH_WSGI_APP" \
                --log-level $DASH_LOG_LEVEL >> $LOG_DIR_PATH/gunicorn_${RUN_ID}.log 2>&1 &

pipe_log_info "Waiting for the Gateway App to start" "InitializeApp"

# Wait for 5 min, by default
DASH_STARTUP_TIMEOUT=${DASH_STARTUP_TIMEOUT:-3}
DASH_STARTUP_ATTEPTS=${DASH_STARTUP_ATTEPTS:-100}
DASH_STARTUP_CURRENT_ATTEMPT=1
DASH_STARTED=1
while (( $DASH_STARTUP_CURRENT_ATTEMPT < $DASH_STARTUP_ATTEPTS )); do
    DASH_STARTUP_CURRENT_ATTEMPT=$((DASH_STARTUP_CURRENT_ATTEMPT+1))
    curl --silent \
        --connect-timeout $DASH_STARTUP_TIMEOUT \
        --max-time $DASH_STARTUP_TIMEOUT \
        "http://127.0.0.1:$DASH_PORT"
    if [ $? -eq 0 ]; then
        pipe_log_success "Gateway App has been started" "InitializeApp"
        DASH_STARTED=0
        break
    fi
    sleep $DASH_STARTUP_TIMEOUT
done

if [ $DASH_STARTED -ne 0 ]; then
    pipe_log_fail "Gateway App timed out while waiting for the startup" "InitializeApp"
    exit 1
fi

sleep infinity
