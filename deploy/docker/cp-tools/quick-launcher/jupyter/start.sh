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
export jupyter_port=${NOTEBOOK_SERVE_PORT:-8888}
export ANACONDA_HOME="/opt/local/anaconda"

function start_jupyter() {
    # Construct notebook http URL, that will match EDGE's notation (e.g. /jupyter-gitlab-4800-8888-0 or /pipeline-4801-8888-0)
    export pipeline_name=$(hostname | awk '{print tolower($0)}')
    export endpoint_number=0
    export notebook_dir=${NOTEBOOK_SERVE_DIR:-"/home/${OWNER}"}
    export base_url="/$pipeline_name-$jupyter_port-$endpoint_number"

    # "disable_check_xsrf" option was added, as "'_xsrf' argument missing from post"
    # might be thrown from time to time, see https://stackoverflow.com/questions/55014094/jupyter-notebook-not-saving-xsrf-argument-missing-from-post
    jupyter notebook --ip '0.0.0.0' \
                     --port $jupyter_port \
                     --no-browser \
                     --NotebookApp.token='' \
                     --NotebookApp.notebook_dir=$notebook_dir \
                     --NotebookApp.base_url=$base_url \
                     --NotebookApp.disable_check_xsrf=True \
                     --allow-root
}

function start_jupyter_app() {
    if [ -z "$IPYNB_NAME" ]; then
        echo "[ERROR] IPython Notebook name (.ipynb) is not defined. Exiting"
        exit 1
    fi

    notebook_dir_name=$(basename $APP_PATH)
    export notebook_dir=$(dirname $APP_PATH)
    export ipynb_url="notebooks/$notebook_dir_name/$IPYNB_NAME"
    export jupyter_server_inactivity_timeout_sec=${JUPYTER_SERVER_INACTIVITY_TIMEOUT_SEC:-120}
    export inactivity_timeout_sec=${PROCESS_ACTIVITY_TRACKING_INACTIVITY_TIMEOUT_SEC:-43200}
    export jupyter_server_cull_interval_sec=${JUPYTER_SERVER_CULL_INTERVAL_SEC:-60}
    base_pathname=$(echo $RUN_PRETTY_URL | cut -d';' -f 2)
    export base_url=${JUPYTER_BASE_URL:-"/$base_pathname/"}

    # Start activity tracker
    if [ "$PROCESS_ACTIVITY_TRACKING_ENABLED" == "true" ]; then
        rm -f /var/log/activity-tracker.log
        nohup bash /activity-tracker.sh >> /var/log/activity-tracker.log 2>&1 &
    fi

    # "disable_check_xsrf" option was added, as "'_xsrf' argument missing from post"
    # might be thrown from time to time, see https://stackoverflow.com/questions/55014094/jupyter-notebook-not-saving-xsrf-argument-missing-from-post
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
    echo "[INFO] Jupyter Notebook logs can be found at $LOG_DIR_PATH path"
    sleep infinity
}

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
    echo "[WARN] FOLDER_APPLICATION_STORAGE is not set, can't find path to ipynb"
fi
export FOLDER_STORAGE_MOUNT_POINT=${folder_app_storage_mountpoint%/}
if [ "$FOLDER_APPLICATION_PATH" ]; then
    export APP_PATH=$FOLDER_STORAGE_MOUNT_POINT/$(dirname $FOLDER_APPLICATION_PATH)
    if [ ! -d "$APP_PATH" ]; then
        echo "[ERROR] Jupyter notebook app folder was not found in $APP_PATH path. Check FOLDER_APPLICATION_PATH env ($FOLDER_APPLICATION_PATH)"
        exit 1
    fi
fi

user_default_storage_id=$(curl -s -k \
                                -X GET \
                                -H "Authorization: Bearer $API_TOKEN" \
                                "$API/whoami" \
                            | jq -r '.payload.defaultStorageId')
if [ -z "$user_default_storage_id" ] || [ "$user_default_storage_id" == null ]; then
    echo "[ERROR] User default storage was not found. '/home/${OWNER}' home directory will be used for work"
    export USER_STORAGE_MOUNT_POINT="/home/${OWNER}"
    export CONDA_ENVS_PATH="${ANACONDA_HOME}/envs"
    export JUPYTER_PATH="${USER_STORAGE_MOUNT_POINT}/share/jupyter"
    export LOG_DIR_PATH="${USER_STORAGE_MOUNT_POINT}/JupyterNotebooks/log"
    mkdir -p $LOG_DIR_PATH
else
    user_default_storage_mountpoint=$(curl -s -k \
                                    -X GET \
                                    -H "Authorization: Bearer $API_TOKEN" \
                                    "$API/datastorage/$user_default_storage_id/load" \
                                    | jq -r '.payload.mountPoint')
    if [ -z "$user_default_storage_mountpoint" ] || \
       [ "$user_default_storage_mountpoint" == null ] || \
       [ ! -d "$user_default_storage_mountpoint" ]; then
        echo "[ERROR] User default storage ($user_default_storage_id) mountpoint can not be found"
        export USER_STORAGE_MOUNT_POINT="/home/${OWNER}"
    fi
    export USER_STORAGE_MOUNT_POINT=$user_default_storage_mountpoint

    if [ "$FOLDER_APPLICATION_PATH" ]; then
        if [ -d "$APP_PATH/.conda/envs" ]; then
            export CONDA_ENVS_PATH="$APP_PATH/.conda/envs"
            export JUPYTER_PATH="$APP_PATH/.conda/share/jupyter"
        else
            STORAGE_MOUNT_POINT=$(echo "$USER_STORAGE_MOUNT_POINT" | cut -d "/" -f2)
            export DEFAULT_STORAGE_USER=$(echo "$FOLDER_APPLICATION_PATH" | cut -d "/" -f1)
            export CONDA_ENVS_PATH="/$STORAGE_MOUNT_POINT/$DEFAULT_STORAGE_USER/.conda/envs"
            export JUPYTER_PATH="/$STORAGE_MOUNT_POINT/$DEFAULT_STORAGE_USER/.conda/share/jupyter"
            if [ ! -d $CONDA_ENVS_PATH ] || [ ! -d $JUPYTER_PATH ]; then
                echo "[ERROR] Jupyter notebook CONDA_ENVS_PATH ($CONDA_ENVS_PATH) env and/or JUPYTER_PATH ($JUPYTER_PATH) were not found. Check FOLDER_APPLICATION_PATH env ($FOLDER_APPLICATION_PATH)"
                exit 1
            fi
        fi
    else
        export CONDA_ENVS_PATH="$user_default_storage_mountpoint/.conda/envs"
        export JUPYTER_PATH="$user_default_storage_mountpoint/.conda/share/jupyter"
    fi
fi
echo "[INFO] CONDA_ENVS_PATH=$CONDA_ENVS_PATH and JUPYTER_PATH=$JUPYTER_PATH were set for Jupyter Notebook"

if [ -z "$APP_ENVIRONMENT" ]; then
    if [ -f "$APP_PATH/gateway.spec" ]; then
        export APP_ENVIRONMENT=$( cat "$APP_PATH/gateway.spec" | jq -r '.app_environment' )
    elif [ -d "$APP_PATH/.conda/envs" ]; then
        export ENV_PATH=$(find "$APP_PATH/.conda/envs" -maxdepth 1 -type d | tail -n1)
        export APP_ENVIRONMENT=$(basename $ENV_PATH)
    fi
    if [ -z "$APP_ENVIRONMENT" ] || [ "$APP_ENVIRONMENT" == null ]; then
        export APP_ENVIRONMENT="base"
    fi
fi
source $ANACONDA_HOME/etc/profile.d/conda.sh
conda activate ${APP_ENVIRONMENT}

if [ "$FOLDER_APPLICATION_PATH" ]; then
    if [ -z "$LOG_DIR_PATH" ]; then
        export LOG_DIR_PATH="${USER_STORAGE_MOUNT_POINT}/JupyterNotebooks/log"
        mkdir -p $LOG_DIR_PATH
    fi
    start_jupyter_app
else
    start_jupyter
fi
