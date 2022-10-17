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

if [ -z "$OWNER" ]
then
    pipe_log_fail "Owner of the job is not defined. Exiting"
    exit 1
fi

if [ -z "$APPLICATION_NAME" ]
then
    if [ -z "$FOLDER_APPLICATION_PATH" ]; then
      echo "Dash application name is not defined. Exiting"
      exit 1
    fi
    export APPLICATION_NAME=$(basename $(dirname $FOLDER_APPLICATION_PATH))
fi

if [ -z "$DEFAULT_STORAGE_USER" ]
then
    echo "[ERROR] Dash application owner name is not defined. Run owner will be used."
fi

if [ "$DEFAULT_STORAGE_USER" ]; then
    export APP_PATH="/cloud-home/${DEFAULT_STORAGE_USER}/DashApps/${APPLICATION_NAME}"
else
    export APP_PATH=${OWNER_HOME}/DashApps/${APPLICATION_NAME}
fi
if [ ! -d "$APP_PATH" ]; then
    pipe_log_fail "Dash application ($APP_PATH) is not mounted to the instance"
    exit 1
fi
if [ ! -f "$APP_PATH/app.py" ]; then
    pipe_log_fail "Dash application file ($APP_PATH/app.py) is not found in the instance"
    exit 1
fi

export LOG_DIR_PATH="/cloud-home/${OWNER}/DashApps/log"
if [ ! -d "$LOG_DIR_PATH" ]; then
    mkdir -p $LOG_DIR_PATH
fi

cpu=$(nproc --all)
export worker_processes=$((cpu * 2 + 1))
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
    pipe_log_fail "Conda environment was not found in the instance's $ANACONDA_HOME path"
    exit 1
fi

if [ -z "$APP_ENVIRONMENT" ]; then
    if [ -f "$APP_PATH/gateway.spec" ]; then
        APP_ENVIRONMENT=$( cat "$APP_PATH/gateway.spec" | jq -r '.app_environment' )
    fi
    if [ -z "$APP_ENVIRONMENT" ]; then
        echo "[WARN] Dash application environment (anaconda env) is not defined. Default 'base' env will be used."
    fi
fi
export APP_ENVIRONMENT=${APP_ENVIRONMENT:-base}

base_pathname=$(echo $RUN_PRETTY_URL | cut -d';' -f 2)
export DASH_URL_BASE_PATHNAME=${DASH_URL_BASE_PATHNAME:-"/$base_pathname/"}

# Execute delayed data storage mount
function do_mount_delayed_storages() {
    if [ "$CP_CAP_FORCE_DELAYED_MOUNT" == "true" ]; then
        _limit_mounts_bkp="$CP_CAP_LIMIT_MOUNTS"
        unset CP_CAP_LIMIT_MOUNTS

        MOUNT_DATA_STORAGES_TASK_NAME="MountDataStoragesDelayed"
        DATA_STORAGE_MOUNT_ROOT="/cloud-data"
        mount_storages $DATA_STORAGE_MOUNT_ROOT $TMP_DIR $MOUNT_DATA_STORAGES_TASK_NAME

        mkdir -p $DATA_STORAGE_MOUNT_ROOT
        if [ -L $R_USER_HOME/cloud-data ]; then
            unlink $R_USER_HOME/cloud-data
        fi
        [ -d $DATA_STORAGE_MOUNT_ROOT ] && ln -s -f $DATA_STORAGE_MOUNT_ROOT $R_USER_HOME/cloud-data || echo "$DATA_STORAGE_MOUNT_ROOT not found, no buckets will be available"

        export CP_CAP_LIMIT_MOUNTS="$_limit_mounts_bkp"
        unset _limit_mounts_bkp
    fi
}

if [ "$cluster_role" == "worker" ]; then
    # Mount "Delayed" storages in before the SGE is configred for the workers
    # as the job can be submitted, which requires those storages
    do_mount_delayed_storages
else
    # Start all the services for a worker

    # Start activity tracker
    if [ "$PROCESS_ACTIVITY_TRACKING_ENABLED" == "true" ]; then
        rm -f /var/log/activity-tracker.log
        nohup bash /activity-tracker.sh >> /var/log/activity-tracker.log 2>&1 &
    fi

    export OWNER=$( stat -c '%U' ${OWNER_HOME} )
    source $ANACONDA_HOME/etc/profile.d/conda.sh
    conda activate ${APP_ENVIRONMENT}
    cd $APP_PATH
    nohup gunicorn -b 0.0.0.0:$DASH_PORT -u $OWNER -w $worker_processes app:server --log-level 'debug' >> $LOG_DIR_PATH/gunicorn.log 2>&1 &

    pipe_log_success "Gateway Apps has been started" "InitializeApp"
fi

# Run the SGE installer
if [ "$cluster_role" ]; then
    _CAP_INIT_SCRIPT="$CP_CAP_SCRIPTS_DIR/$cluster_role"
    if [ -f "$_CAP_INIT_SCRIPT" ]; then
        echo "--> Executing $_CAP_INIT_SCRIPT"
        chmod +x "$_CAP_INIT_SCRIPT"
        # Run as a job and await - hack to workaround accident job stop due to PID1 issue
        eval "$_CAP_INIT_SCRIPT &"
        _CAP_INIT_PID=$!
        wait $_CAP_INIT_PID

        _CAP_INIT_SCRIPT_RESULT=$?
        if [ $_CAP_INIT_SCRIPT_RESULT -ne 0 ];
        then
                echo "[ERROR] $_CAP_INIT_SCRIPT failed with $_CAP_INIT_SCRIPT_RESULT. Exiting"
                exit "$_CAP_INIT_SCRIPT_RESULT"
        else
                # Reload env vars, in case they were updated within cap init scripts
                source "$CP_ENV_FILE_TO_SOURCE"
                echo "--> Done $_CAP_INIT_SCRIPT"
        fi

        if [ "$cluster_role" = "master" ] && [ "$CP_CAP_AUTOSCALE" = "true" ] &&  [ "$CP_CAP_SGE" = "true" ]; then
                nohup $CP_PYTHON2_PATH $COMMON_REPO_DIR/scripts/autoscale_sge.py 1>/dev/null 2>$LOG_DIR/.nohup.sge.autoscale.log &
        fi
    fi
fi

if [ "$cluster_role" == "master" ]; then
    do_mount_delayed_storages
fi

sleep infinity
