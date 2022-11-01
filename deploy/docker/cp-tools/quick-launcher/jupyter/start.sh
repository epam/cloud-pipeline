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

if [ -z "$OWNER" ]; then
    echo "[ERROR] Owner of the job is not defined. Exiting"
    exit 1
fi

if [ -z "$IPYNB_NAME" ]; then
    echo "[ERROR] IPython Notebook name (.ipynb) is not defined. Exiting"
    exit 1
fi

if [ "$FOLDER_APPLICATION_STORAGE" ]; then
    folder_app_storage_mountpoint=$(curl -s -k \
                                -X GET \
                                -H "Authorization: Bearer $API_TOKEN" \
                                "$API/datastorage/$FOLDER_APPLICATION_STORAGE/load" \
                            | jq -r '.payload.mountPoint')
    if [ "$folder_app_storage_mountpoint" ] && [ "$folder_app_storage_mountpoint" != null ]; then
        if [ ! -d "$folder_app_storage_mountpoint" ]; then
            echo "[ERROR] FOLDER_APPLICATION_STORAGE ($FOLDER_APPLICATION_STORAGE) is not mounted to the instance at $folder_app_storage_mountpoint"
        fi
    else
        echo "[ERROR] FOLDER_APPLICATION_STORAGE ($FOLDER_APPLICATION_STORAGE) can not be found"
    fi
else
    echo "[ERROR] FOLDER_APPLICATION_STORAGE is not set, can't find path to ipynb"
fi

if [ "$FOLDER_APPLICATION_PATH" ]; then
    FOLDER_APPLICATION_DIR=$(dirname $FOLDER_APPLICATION_PATH)
    export FOLDER_APPLICATION_PATH=${folder_app_storage_mountpoint%/}/${FOLDER_APPLICATION_DIR#/}
fi
if [ ! -d "$FOLDER_APPLICATION_PATH" ]; then
    echo "[ERROR] FOLDER_APPLICATION_PATH ($FOLDER_APPLICATION_PATH) is not found in the instance"
    exit 1
fi

NOTEBOOK_DIR_NAME=$(basename $FOLDER_APPLICATION_PATH)
export notebook_dir=$(dirname $FOLDER_APPLICATION_PATH)
export ipynb_url="notebooks/$NOTEBOOK_DIR_NAME/$IPYNB_NAME"
export jupyter_port=${NOTEBOOK_SERVE_PORT:-8888}
export jupyter_server_inactivity_timeout_sec=${JUPYTER_SERVER_INACTIVITY_TIMEOUT_SEC:-120}
export inactivity_timeout_sec=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_TIMEOUT_SEC:-43200}
export jupyter_server_cull_interval_sec=${JUPYTER_SERVER_CULL_INTERVAL_SEC:-60}
base_pathname=$(echo $RUN_PRETTY_URL | cut -d';' -f 2)
export base_url=${JUPYTER_BASE_URL:-"/$base_pathname/"}

# Get Anaconda System Lib path
if [ "$py_sys_lib" ]; then
    conda_sys_lib_mountpoint=$(curl -s -k \
                                -X GET \
                                -H "Authorization: Bearer $API_TOKEN" \
                                "$API/datastorage/$py_sys_lib/load" \
                            | jq -r '.payload.mountPoint')
    if [ "$conda_sys_lib_mountpoint" ] && [ "$conda_sys_lib_mountpoint" != null ]; then
        if [ ! -d "$conda_sys_lib_mountpoint" ]; then
            echo "[ERROR] py_sys_lib ($py_sys_lib) is not mounted to the instance at $conda_sys_lib_mountpoint"
        fi
    else
        echo "[ERROR] py_sys_lib ($py_sys_lib) can not be found"
    fi
else
    echo "[ERROR] py_sys_lib is not set, no external library is mounted"
fi

export ANACONDA_HOME=${conda_sys_lib_mountpoint:-/opt/local/anaconda}
if [ ! -f "$ANACONDA_HOME/etc/profile.d/conda.sh" ]; then
    echo "[ERROR] Conda environment was not found in the instance's $ANACONDA_HOME path"
    exit 1
fi

if [ -z "$APP_ENVIRONMENT" ]; then
    if [ -f "$FOLDER_APPLICATION_PATH/gateway.spec" ]; then
        export APP_ENVIRONMENT=$( cat "$FOLDER_APPLICATION_PATH/gateway.spec" | jq -r '.app_environment' )
    fi
    if [ -z "$APP_ENVIRONMENT" ]; then
        echo "[WARN] Jupyter notebook environment (anaconda env) is not defined. Default 'base' env will be used."
        export APP_ENVIRONMENT="base"
    fi
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
            export LOG_DIR_PATH="${user_default_storage_mountpoint}/JupyterNotebooks/log"
        else
            echo "[ERROR] User default storage ($user_default_storage_id) is not mounted to the instance at $user_default_storage_mountpoint"
        fi
    else
        echo "[ERROR] User default storage ($user_default_storage_id) mountpoint can not be found"
    fi
else
    echo "[ERROR] User default storage was not found. '/home/${OWNER}/JupyterNotebooks/log' directory will be used for logs"
    export LOG_DIR_PATH="/home/${OWNER}/JupyterNotebooks/log"
fi
mkdir -p $LOG_DIR_PATH

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
    # Mount "Delayed" storages in before the SGE is configured for the workers
    # as the job can be submitted, which requires those storages
    do_mount_delayed_storages
else
    # Start all the services for a worker

    # Start activity tracker
    if [ "$PROCESS_ACTIVITY_TRACKING_ENABLED" == "true" ]; then
        rm -f /var/log/activity-tracker.log
        nohup bash /activity-tracker.sh >> /var/log/activity-tracker.log 2>&1 &
    fi

    # "disable_check_xsrf" option was added, as "'_xsrf' argument missing from post"
    # might be thrown from time to time, see https://stackoverflow.com/questions/55014094/jupyter-notebook-not-saving-xsrf-argument-missing-from-post

    source $ANACONDA_HOME/etc/profile.d/conda.sh
    conda activate ${APP_ENVIRONMENT}

    nohup jupyter-notebook --ip '0.0.0.0' \
                     --port $jupyter_port \
                     --no-browser \
                     --NotebookApp.token='' \
                     --NotebookApp.notebook_dir=$notebook_dir \
                     --NotebookApp.base_url=$base_url \
                     --NotebookApp.disable_check_xsrf=True \
                     --NotebookApp.default_url=$ipynb_url \
                     --NotebookApp.shutdown_no_activity_timeout=$jupyter_server_inactivity_timeout_sec \
                     --MappingKernelManager.cull_idle_timeout=$inactivity_timeout_sec \
                     --MappingKernelManager.cull_interval=$jupyter_server_cull_interval_sec \
                     --MappingKernelManager.cull_connected=true \
                     --allow-root >> $LOG_DIR_PATH/jupyter_notebook_${RUN_ID}.log 2>&1 &

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
