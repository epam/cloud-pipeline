#!/bin/bash
if [ -z "$OWNER" ]
then
    pipe_log_fail "Owner of the job is not defined. Exiting"
    exit 1
fi

function remount_rstudio_cache_to_local {
    # If home dir is in a readonly state (e.g. sensitive run) - RStudio will fail to operate (e.g. open files)
    # So we mount ~/.local/share/rstudio from a local filesystem
    _is_sensitive=$(curl -sk -H "Authorization: Bearer $API_TOKEN" "$API/run/$RUN_ID" | jq '.payload.sensitive')
    if [ "$_is_sensitive" == "true" ]; then
        _rs_config_dir="${R_USER_HOME}/.local/share/rstudio"
        echo "[WARN] This is a sensitive run, will mount $_rs_config_dir as a temp directory"
        CP_RGATEWAY_BIND_RS_DIR="${CP_RGATEWAY_BIND_RS_DIR:-/tmp/cp_rstudio}"
        mkdir -p "$CP_RGATEWAY_BIND_RS_DIR"
        mount -B "$CP_RGATEWAY_BIND_RS_DIR" "$_rs_config_dir"
        if [ $? -ne 0 ]; then
            echo "[WARN] Unable to mount $CP_RGATEWAY_BIND_RS_DIR to $_rs_config_dir , RStudio may behave abnormally"
        fi
    fi
}

R_HOME=$(R RHOME)
export R_JOB_OWNER="$OWNER"
if [ "$ANONYMOUS_USER_ORIGINAL_NAME" ]; then
    export R_JOB_OWNER="$ANONYMOUS_USER_ORIGINAL_NAME"
fi
# Add rstudio permissions to the OWNER and create a home dir for this account
groupadd rstudio
groupadd staff
usermod -a -G rstudio "$OWNER"
usermod -a -G staff "$OWNER"
usermod -a -G wheel "$OWNER"
chmod g+wx $R_HOME/library
export R_VERSION=$(Rscript -e "cat(strsplit(version[['version.string']], ' ')[[1]][3])")
export R_USER_HOME=/cloud-home/${OWNER}
export R_LIBS_USER=$R_USER_HOME/R/x86_64-pc-linux-gnu-library/${R_VERSION}

# Get R System Lib path
if [ "$r_sys_lib" ]; then
    r_sys_lib_mountpoint=$(curl -s -k \
                                -X GET \
                                -H "Authorization: Bearer $API_TOKEN" \
                                "$API/datastorage/$r_sys_lib/load" \
                            | jq -r '.payload.mountPoint')
    if [ "$r_sys_lib_mountpoint" ] && \
        [ "$r_sys_lib_mountpoint" != null ]; then
        r_sys_lib_mountpoint="$r_sys_lib_mountpoint/$R_VERSION"
        if [ -d "$r_sys_lib_mountpoint" ]; then
            mkdir -p "$R_HOME/site-library"
            mount -B "$r_sys_lib_mountpoint" "$R_HOME/site-library"
        else
            echo "[ERROR] r_sys_lib ($r_sys_lib) is not mounted to the instance at $r_sys_lib_mountpoint"
        fi
    else
        echo "[ERROR] r_sys_lib ($r_sys_lib) can not be found"
    fi
else
    echo "[ERROR] r_sys_lib is not set, no external library is mounted"
fi

# Set home directory for the use to point to the mount (this is required by the rstudio, as it does not respect the $HOME)
usermod -d $R_USER_HOME $OWNER

# Configure env variables for R Session
R_ENV_FILE=$R_HOME/etc/Renviron
cat $CP_ENV_FILE_TO_SOURCE | sed '/^export/s/export//' >> $R_ENV_FILE
echo "R_LIBS_USER=$R_LIBS_USER" >> $R_ENV_FILE
echo "HOME=$R_USER_HOME" >> $R_ENV_FILE

# Configure RStudio variables
RSERVER_CONF_FILE=/etc/rstudio/rserver.conf
RSESSION_CONF_FILE=/etc/rstudio/rsession.conf
echo "r-libs-user=$R_LIBS_USER" >> $RSESSION_CONF_FILE

# Configure Java
if [ -z "$r_jdk_current" ] || [ -z "$r_jdk" ]; then
    echo "[ERROR] Java version is not specified ('r_jdk_current' or 'r_jdk' are not set). Skipping Java configuration for RStudio"
else
    rm -f $r_jdk_current && \
    ln -s $r_jdk $r_jdk_current
    if [ $? -ne 0 ]; then
        echo "[ERROR] Unable to symlink Java from '$r_jdk' to '$r_jdk_current'. Skipping Java configuration for RStudio"
        R CMD javareconf
    fi
fi

# Configure AWS credentials, if available
_aws_config_path=/root/.aws/config
if [ -f "$_aws_config_path" ]; then
    chmod +r "$_aws_config_path"
    export AWS_CONFIG_FILE="$_aws_config_path"
    echo "AWS_CONFIG_FILE=$_aws_config_path" >> $R_ENV_FILE
fi

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

if [ ! -z $CP_CAP_SHINY_VALIDATOR_MODE ]; then
    # Run only Shiny validator if specified
    $CP_PYTHON2_PATH $SHINY_VALIDATOR_HOME/validate_shiny_app.py &> $SHINY_VALIDATOR_HOME/validator.log &
elif [ "$cluster_role" == "worker" ]; then
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

    # Configure shiny server to use a correct shiny apps dirs
    export R_APPS_OWNER="${DEFAULT_STORAGE_USER:-$OWNER}"
    # Force shiny apps owner to upper case, as per MGLS-1802
    export R_APPS_OWNER_UPPER=$( echo "${R_APPS_OWNER}" | tr '[:lower:]' '[:upper:]' )
    envsubst '${R_APPS_OWNER} ${R_APPS_OWNER_UPPER} ${R_VERSION} ${R_JOB_OWNER} ${OWNER}' < /etc/shiny-server/shiny-server.conf.tmpl > /etc/shiny-server/shiny-server.conf

    # Configure nginx for SSO
    envsubst '${R_JOB_OWNER}' < /auto-fill-form-template.conf > /etc/nginx/sites-enabled/auto-fill-form.conf

    remount_rstudio_cache_to_local

    _PREV_HOME="$HOME"
    export HOME="$R_USER_HOME"
    rstudio-server start
    /usr/bin/shiny-server &> /var/log/shiny-server.log &
    export HOME="$_PREV_HOME"
    nginx
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

# Finally mount data storages for the master
# We do it after the SGE, as the master shall be available to the user ASAP
# but the data storages take time
if [ "$cluster_role" == "master" ]; then
    do_mount_delayed_storages
    remount_rstudio_cache_to_local
fi

sleep infinity
