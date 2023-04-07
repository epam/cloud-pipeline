#!/bin/bash

configure_cloud_credentials() {

  pipe_log_info "Configuring cloud credentials" $INIT_TASK_NAME
  pipe_log_info "Provider is defined as $CLOUD_PROVIDER" $INIT_TASK_NAME

  if [ "$CLOUD_PROVIDER" == "AWS" ]; then
    export AWS_DEFAULT_REGION="$CLOUD_REGION"
    export AWS_ACCESS_KEY_ID=`eval echo "$"{CP_ACCOUNT_ID_${CLOUD_REGION_ID}}`
    export AWS_SECRET_ACCESS_KEY=`eval echo "$"{CP_ACCOUNT_KEY_${CLOUD_REGION_ID}}`
    export AWS_SESSION_TOKEN=`eval echo "$"{CP_ACCOUNT_TOKEN_${CLOUD_REGION_ID}}`
  elif [ "$CLOUD_PROVIDER" == "AZURE" ]; then
    # Untested
    export AZURE_STORAGE_ACCOUNT=`eval echo "$"{CP_ACCOUNT_ID_${CLOUD_REGION_ID}}`
    export AZURE_STORAGE_ACCESS_KEY=`eval echo "$"{CP_ACCOUNT_KEY_${CLOUD_REGION_ID}}`
  elif [ "$CLOUD_PROVIDER" == "GCP" ]; then
    # Untested
    export GOOGLE_APPLICATION_CREDENTIALS="root/.gcp_credentials.json"
    echo `eval echo "$"{CP_CREDENTIALS_FILE_CONTENT_${CLOUD_REGION_ID}}` > $GOOGLE_APPLICATION_CREDENTIALS
  else
    pipe_log_warn "Cloud provider wasn't provided or unsupported: $CLOUD_PROVIDER. Skipping credentials configuration." $INIT_TASK_NAME
    return
  fi
  pipe_log_info "$CLOUD_PROVIDER credentials successfully configured." $INIT_TASK_NAME
}

mount_system_fs() {

  if [ -z "$CP_SYSTEM_JOB_SYSTEM_FS" ]; then
    pipe_log_warn "CP_SYSTEM_JOB_SYSTEM_FS wasn't provided. Skipping mounting system FS." $INIT_TASK_NAME
    return
  fi

  if [ "$CLOUD_PROVIDER" == "AWS" ]; then
    export CP_SYS_FS_MOUNT_LOCATION="/opt/cp_sys_fs"
    mkdir -p $CP_SYS_FS_MOUNT_LOCATION
    mount -t lustre -o noatime,flock $CP_SYSTEM_JOB_SYSTEM_FS $CP_SYS_FS_MOUNT_LOCATION
    if [ $? -eq 0 ]; then
      pipe_log_info "System FS: $CP_SYSTEM_JOB_SYSTEM_FS successfully mounted to: $CP_SYS_FS_MOUNT_LOCATION. And location is available in CP_SYS_FS_MOUNT_LOCATION." $INIT_TASK_NAME
    else
      pipe_log_warn "Problems with mounting System FS $CP_SYSTEM_JOB_SYSTEM_FS. Skipping." $INIT_TASK_NAME
    fi
  else
    pipe_log_warn "Cloud provider wasn't provided or unsupported: $CLOUD_PROVIDER. Skipping mounting system FS." $INIT_TASK_NAME
  fi

}

export INIT_TASK_NAME="ConfigureSystemJob"
export MAIN_TASK_NAME="${CP_SYSTEM_JOBS_OUTPUT_TASK:-SystemJob}"

configure_cloud_credentials
mount_system_fs

# Just granting appropriate permissions
chmod -R +x $SCRIPTS_DIR/$CP_SYSTEM_SCRIPTS_LOCATION/

# Validation of required params
if [ -z "$CP_SYSTEM_SCRIPTS_LOCATION" ]; then
  pipe_log_fail "CP_SYSTEM_SCRIPTS_LOCATION wasn't provided, exiting." $INIT_TASK_NAME
fi

if [ -z "$CP_SYSTEM_JOB" ]; then
  pipe_log_fail "CP_SYSTEM_JOB wasn't provided, exiting." $INIT_TASK_NAME
fi

MIRRORED_JOBS_PARAMS=`echo $CP_SYSTEM_JOB_PARAMS | sed 's|"|\\\"|g'`

# Execution of the command itself
if [ -z "$CP_SYSTEM_JOBS_RESULTS" ]; then
  pipe_log_warn "CP_SYSTEM_JOBS_RESULTS wasn't provided, running command with local output only." $INIT_TASK_NAME
  pipe_exec "$SCRIPTS_DIR/$CP_SYSTEM_SCRIPTS_LOCATION/$CP_SYSTEM_JOB $MIRRORED_JOBS_PARAMS" $MAIN_TASK_NAME
else
  pipe_exec "$SCRIPTS_DIR/$CP_SYSTEM_SCRIPTS_LOCATION/$CP_SYSTEM_JOB $MIRRORED_JOBS_PARAMS  | tee $ANALYSIS_DIR/${RUN_ID}.$CP_SYSTEM_JOB.result" $MAIN_TASK_NAME
fi
