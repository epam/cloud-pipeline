#!/bin/bash

function parse_options {
    while [[ $# -gt 0 ]]; do
        key="$1"
        case $key in
            --engine)
            export ENGINE=$2
            shift # past argument
            shift # past value
            ;;
            --workflow_name)
            export WORKFLOW_NAME=$2
            shift # past argument
            shift # past value
            ;;
            --logging_task)
            export LOG_TASK_NAME=$2
            shift # past argument
            shift # past value
            ;;
        esac
    done
}

function build_and_run_workflow() {
    preflight_checks

    obtain_omics_service_role
    assume_omics_service_role

    prepare_workflow_project

    obtain_private_ecr_uri_run_from_region
    sync_images_in_private_ecr

    pipe_log_info "Packaging workflow in a zip distribution." "${LOG_TASK_NAME}"
    package_omics_workflow "$SCRIPTS_DIR/src/workflow" "$WORKFLOW_NAME"
    pipe_log_info "Preparing parameters-template.json and parameters.json for a workflow, based on pipeline run parameters." "${LOG_TASK_NAME}"
    prepare_workflow_parameters "$WORKFLOW_PARAMETERS_TEMPLATE" "$WORKFLOW_PARAMETERS"
    pipe_log_info "Registering workflow and running omics workflow." "${LOG_TASK_NAME}"
    run_omics_workflow "$WORKFLOW_NAME" "$WORKFLOW_DEFINITION_ZIP" "$WORKFLOW_PARAMETERS_TEMPLATE" "$WORKFLOW_PARAMETERS"

    watch_and_log_omics_workflow_run "$WORKFLOW_RUN_ID"
}

function preflight_checks() {
   if [ "${ENGINE}" != "NEXTFLOW" ]; then
       pipe_log_fail "Parameter ENGINE is set to: '${ENGINE}', allowed options are: 'NEXTFLOW'" "${LOG_TASK_NAME}"
       exit 1
   fi

   if [ ! -d /opt/omics/utils/ ]; then
        pipe_log_fail "Omcis Worklow helper scripts in /opt/omics/utils weren't found. Are you using library/aws-omics-workflow docker image?" "${LOG_TASK_NAME}"
        exit 1
    fi

   if [ "$CP_CAP_DIND_CONTAINER" != "true" ]; then
       pipe_log_fail "DinD capability isn' set. Please configure it for the pipeline." "${LOG_TASK_NAME}"
       exit 1
   fi

   if [ ! -d "$SCRIPTS_DIR/src/workflow" ]; then
       pipe_log_fail "There is no workflow folder in $SCRIPTS_DIR/src." "${LOG_TASK_NAME}"
       exit 1
   fi

   if [ ! -f "$SCRIPTS_DIR/src/workflow/nextflow.config" ]; then
       pipe_log_fail "There is no main nextflow.config file in $SCRIPTS_DIR/src/workflow/" "${LOG_TASK_NAME}"
       exit 1
  fi

   if [[ ! "${OUTPUT_DIR}" =~ "s3://"* ]]; then
       pipe_log_fail "Please specify pipeline parameter 'OUTPUT_DIR' as an s3 path: s3://<bucket-name>/bucket-prefix/" "${LOG_TASK_NAME}"
       exit 1
   elif [[ ! "${OUTPUT_DIR}" =~ "s3://"*"/" ]]; then
       OUTPUT_DIR="${OUTPUT_DIR}/"
   fi

   which aws &> /dev/null
   if [ $? -ne 0 ]; then
       pipe_log_fail "Can't find aws utility. Are you using library/aws-omics-workflow docker image?" "${LOG_TASK_NAME}"
       exit 1
   fi
}

function prepare_workflow_project() {
    local _workflow_dir="$SCRIPTS_DIR/src/workflow"

    if ! cat "$_workflow_dir/nextflow.config" | grep -E "includeConfig.*omics.config" &> /dev/null ; then
        pipe_log_warn "Can't find an includeConfig statement for any omics.config. Assuming there is no omics configuration. Will try to auto-configure." "${LOG_TASK_NAME}"

        cd $_workflow_dir
        _NEXTFLOW_OMICS_CONFIG_PATH=conf/omics.config
        _IMAGE_PULL_MANIFAST_FILE=container_image_manifest.json

        rm -rf $_NEXTFLOW_OMICS_CONFIG_PATH $_IMAGE_PULL_MANIFAST_FILE && \
        mkdir -p "$(dirname $_NEXTFLOW_OMICS_CONFIG_PATH)"

        python3 /opt/omics/amazon/inspect_nf.py . \
                -n /opt/omics/utils/public_registry_properties.json \
                --output-config-file "$_NEXTFLOW_OMICS_CONFIG_PATH" \
                --output-manifest-file "$_IMAGE_PULL_MANIFAST_FILE" \
                --region "${CLOUD_REGION}"

        if [ $? -ne 0 ] || [ ! -f $_NEXTFLOW_OMICS_CONFIG_PATH ]; then
            pipe_log_fail "Command inspect_nf.py failed! Can't create omics.config file with. Exiting" "${LOG_TASK_NAME}"
            exit 1
        fi
        pipe_log_info "Successfully generated $_NEXTFLOW_OMICS_CONFIG_PATH and $_IMAGE_PULL_MANIFAST_FILE in $_workflow_dir" "${LOG_TASK_NAME}"
        echo "includeConfig '$_NEXTFLOW_OMICS_CONFIG_PATH'" >> "$SCRIPTS_DIR/src/workflow/nextflow.config"
        pipe_log_info "Updated $SCRIPTS_DIR/src/workflow/nextflow.config" "${LOG_TASK_NAME}"
        cd - &> /dev/null
    else
        pipe_log_info "Found an includeConfig statement for an omics.config. Assuming that omics configuration already provided." "${LOG_TASK_NAME}"
    fi
}

function call_api() {
    local _api_method=$1
    local _jq_data_extractor=$2
    local _max_retries=30
    local _exit_code=1
    local try_count=0

    while [ $_exit_code != 0 ] && [ $try_count -lt $_max_retries ]; do
        _result=$(curl -X GET \
                          --insecure \
                          -s \
                          --max-time 30 \
                          --header "Accept: application/json" \
                          --header "Authorization: Bearer $API_TOKEN" \
                          "${API}${_api_method}" \
                      | jq -r "${_jq_data_extractor}")
        _exit_code=$?
        try_count=$(( try_count + 1 ))
    done

    if [ $_exit_code -ne 0 ]; then
        pipe_log_fail "Couldn't get response from API on: ${_api_method} after $_max_retries retries" "${LOG_TASK_NAME}"
        exit 1
    fi
    echo "$_result"
}

function obtain_omics_service_role() {
    export CP_OMICS_SERICE_ROLE=$(call_api "/cloud/region/${CLOUD_REGION_ID}" '.payload.omicsServiceRole // empty')
    if [ "${CP_OMICS_SERICE_ROLE}" == "empty" ]; then
        pipe_log_fail "Couldn't get Omics Service Role information from Cloud Region with id ${CLOUD_REGION_ID}" "${LOG_TASK_NAME}"
        exit 1
    fi
}

function obtain_private_ecr_uri_run_from_region() {
    export CP_PRIVATE_ECR=$(call_api "/cloud/region/${CLOUD_REGION_ID}" '.payload.omicsEcrUrl // empty')
    if [ "${CP_PRIVATE_ECR}" == "empty" ]; then
      pipe_log_fail "Couldn't get AWS Private ECR information from Cloud Region with id ${CLOUD_REGION_ID}" "${LOG_TASK_NAME}"
      exit 1
    fi
}

function assume_omics_service_role() {
    pipe_log_info "Omics Service Role is configured by region setting as: $CP_OMICS_SERICE_ROLE, Assuming..." "${LOG_TASK_NAME}"

    unset AWS_ACCESS_KEY_ID
    unset AWS_SECRET_ACCESS_KEY
    unset AWS_SESSION_TOKEN

    AWS_TEMP_CREDS=$(aws sts assume-role --role-arn "$CP_OMICS_SERICE_ROLE" --role-session-name "CP_AWS_OMICS_WORKFLOW_${RUN_ID}" --duration-seconds "${CP_OMICS_ROLE_ASSUME_SESSION_DURATION:-3600}")
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during obtaining temporary credentials for Omics Service role." "${LOG_TASK_NAME}"
        exit 1
    fi

    export AWS_ACCESS_KEY_ID=$(echo "$AWS_TEMP_CREDS" | jq -r .Credentials.AccessKeyId)
    export AWS_SECRET_ACCESS_KEY=$(echo "$AWS_TEMP_CREDS" | jq -r .Credentials.SecretAccessKey)
    export AWS_SESSION_TOKEN=$(echo "$AWS_TEMP_CREDS" | jq -r .Credentials.SessionToken)
}

function sync_images_in_private_ecr() {
    local _ecr_sync_task_name="AWSOmicsECRSyncImages"
    
    pipe_log_info "Synchronizing docker images in private ECR repo." "${LOG_TASK_NAME}"
    if [ ! -f /opt/omics/utils/omics_container_syncronizer.sh ]; then
        pipe_log_fail "Script /opt/omics/utils/container_syncronizer.sh wasn't found." "${LOG_TASK_NAME}"
        exit 1
    fi
    pipe_exec \
      "bash /opt/omics/utils/omics_container_syncronizer.sh --ecr ${CP_PRIVATE_ECR} --workflow_source $SCRIPTS_DIR/src" \
      "${_ecr_sync_task_name}"

    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during Synchronization docker images in private ECR." "${LOG_TASK_NAME}"
        exit 1
    else
      pipe_log_success "Successfully synchronized docker images in private ECR repo." "${_ecr_sync_task_name}"
      pipe_log_info "Successfully synchronized docker images in private ECR repo." "${LOG_TASK_NAME}"
    fi
}

function prepare_workflow_parameters() {
    local _params_to_exclude="ENGINE,OUTPUT_DIR,RESYNC_IMAGES"
    local _param_type_suffix="_PARAM_TYPE"
    local _parameters_template="\"ecr_registry\": { \"description\": \"Private ECR Registry\" }"
    local _parameters="\"ecr_registry\": \"${CP_PRIVATE_ECR}\""

    while IFS= read -r line; do
        PARAM_TYPE_NAME=$(awk -F "=" '{print $1}' <<< $line)
        PARAM_NAME=${PARAM_TYPE_NAME%$_param_type_suffix}
        if echo ",$_params_to_exclude," | grep ",$PARAM_NAME," &> /dev/null || [[ "$PARAM_NAME" =~ "CP_"* ]]; then
           pipe_log_info "Skipping parameter $PARAM_NAME, it won't be added to omics workflow parameters configuration." "${LOG_TASK_NAME}"
           continue
        fi
        _parameters_template="${_parameters_template}, \"$PARAM_NAME\": { \"description\": \"$PARAM_NAME\" }"
        _parameters="${_parameters}, \"$PARAM_NAME\": \"${!PARAM_NAME}\""
    done <<< "$(env | grep ${_param_type_suffix})"

    echo "{ ${_parameters_template} }" | jq > $1
    echo "{ ${_parameters} }" | jq > $2
}

function package_omics_workflow() {
    local _workflow_definition_zip="/tmp/$2.zip"
    if [ -d "$1" ]; then
        cd "$1" &&  zip -9 -r "$_workflow_definition_zip" . &> /dev/null && cd - &> /dev/null
    else
        pipe_log_fail "Couldn't find workflow directory by path $1"
        exit 1
    fi
    export WORKFLOW_DEFINITION_ZIP=$_workflow_definition_zip
}

function cleanup_omics_workflow() {
    pipe_log_info "Removing HealthOmics Workflow on cleanup." "${LOG_TASK_NAME}"
    assume_omics_service_role
    aws omics delete-workflow --id "$WORKFLOW_ID"
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during cleanup of HealthOmics Workflow, id: $WORKFLOW_ID." "${LOG_TASK_NAME}"
    fi
}

function run_omics_workflow() {
    local _workflow_name="$1"
    local _workflow_zip="$2"
    local _workflow_parameters_template="$3"
    local _workflow_parameters="$4"
    local _workflow_id
    local _workflow_run_id

    local _workflow_definition_uri="${OUTPUT_DIR}/${_workflow_name}.zip"
    aws s3 cp "$_workflow_zip" "$_workflow_definition_uri"
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during HealthOmics Workflow definition zip upload process." "${LOG_TASK_NAME}"
        exit 1
    fi

    _workflow_id=$(aws omics create-workflow \
                            --engine "${ENGINE}" --name "$_workflow_name"  --definition-uri "$_workflow_definition_uri" \
                            --parameter-template "file://$_workflow_parameters_template" \
                            --query 'id' --output text)
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during HealthOmics Workflow registration." "${LOG_TASK_NAME}"
        exit 1
    else
        trap cleanup_omics_workflow EXIT
    fi

    aws omics wait workflow-active --id "${_workflow_id}"
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during awaiting HealthOmics Workflow to be available." "${LOG_TASK_NAME}"
        exit 1
    fi

    pipe_log_info "Starting workflow $_workflow_name with id $_workflow_id" "${LOG_TASK_NAME}"
    _workflow_run_id=$(aws omics start-run \
                                --role-arn "${CP_OMICS_SERICE_ROLE}" \
                                --workflow-id "${_workflow_id}" \
                                --name "$_workflow_name" \
                                --output-uri "${OUTPUT_DIR}" \
                                --parameters "file://$_workflow_parameters" \
                                --query 'id' --output text)
    pipe_log_info "Workflow run with id: $_workflow_run_id started." "${LOG_TASK_NAME}"
    export WORKFLOW_ID="$_workflow_id"
    export WORKFLOW_RUN_ID="$_workflow_run_id"
}

function fail_run() {
    local _workflow_run_id=$1
    local _workflow_status=$2
    local _log_limit=500

    if [ "$_workflow_status" == "FAILED" ]; then
        for _task_id in $(aws omics list-run-tasks --id "$_workflow_run_id" | jq '.items[] | select(.status == "FAILED") | .taskId' -r); do
            local _workflow_task_name
            _workflow_task_name=$(aws omics get-run-task --id "$_workflow_run_id" --task-id "$_task_id" | jq -r ".name" | sed "s| |_|g")
            pipe_log_info "---- Only last 500 log messages are printed out ----" "$_workflow_task_name"
            pipe_exec "aws logs get-log-events --log-group-name /aws/omics/WorkflowLog --log-stream-name run/${_workflow_run_id}/task/${_task_id} --limit ${_log_limit} --no-start-from-head --output text | grep EVENTS" "$_workflow_task_name"
            pipe_log_fail "AWS Omics Workflow task failed." "$_workflow_task_name"
        done
    fi
    pipe_log_fail "Workflow run: ${_workflow_run_id} finished with status: $_workflow_status" "${LOG_TASK_NAME}"
}

function watch_and_log_omics_workflow_run() {
    local _workflow_run_id=$1
    local _workflow_run_status="RUNNING"
    local _waiting_time=0
    
    while [ "$_workflow_run_status" != "COMPLETED" ] && [ "$_workflow_run_status" != "FAILED" ] && [ "$_workflow_run_status" != "CANCELLED" ]; do
        local _task_status
        local _total_tasks
        local _running_tasks
    
        sleep 300
        _waiting_time=$((_waiting_time + 300))
        if [ $_waiting_time -gt "$TIME_TO_UPDATE_CREDS" ]; then
            _waiting_time=0
            pipe_log_info "Updating AWS temporary credentials..." "${LOG_TASK_NAME}"
            assume_omics_service_role
        fi

        _workflow_run_status=$(aws omics get-run --id "${_workflow_run_id}" --query 'status' --output text)
        _task_status=$(aws omics list-run-tasks --id "${_workflow_run_id}")
        _total_tasks=$(echo "$_task_status" | grep -o "status" | wc -l)
        _running_tasks=$(echo "$_task_status" | grep -oE 'STARTING|RUNNING' | wc -l)
        pipe_log_info "Workflow run: ${_workflow_run_id}, status: ${_workflow_run_status}. Tasks (completed / total): $((_total_tasks - _running_tasks)) / ${_total_tasks}" "${LOG_TASK_NAME}"
    done
    if [ "$_workflow_run_status" == "FAILED" ] || [ "$_workflow_run_status" == "CANCELLED" ]; then
        fail_run "$_workflow_run_id" "$_workflow_run_status"
        exit 1
    fi
    pipe_log_success "Workflow run: ${_workflow_run_id}, status: ${_workflow_run_status}" "${LOG_TASK_NAME}"
}


TIME_TO_UPDATE_CREDS=2700
WORKFLOW_PARAMETERS_TEMPLATE="$SCRIPTS_DIR/src/parameters_template.json"
WORKFLOW_PARAMETERS="$SCRIPTS_DIR/src/parameters.json"

parse_options "$@" && \
build_and_run_workflow
