#!/bin/bash

function preflight_checks() {
   if [ "${engine}" != "NEXTFLOW" ]; then
       pipe_log_fail "Parameter engine is set to: '${engine}', allowed options are: 'NEXTFLOW'" $_TASK_NAME
       exit 1
   fi

   if [[ ! "${output_path}" =~ "s3://"* ]]; then
       pipe_log_fail "Please specify pipeline parameter 'output_path' as an s3 path: s3://<bucket-name>/bucket-prefix/" $_TASK_NAME
       exit 1
   elif [[ ! "${output_path}" =~ "s3://"*"/" ]]; then
       export output_path="${output_path}/"
   fi

   which aws &> /dev/null
   if [ $? -ne 0 ]; then
       pipe_log_fail "Can't find aws utility. Are you using library/aws-omics docker image?" $_TASK_NAME
       exit 1
   fi
}

function obtain_omics_service_role() {
    export CP_OMICS_SERICE_ROLE=$(curl -X GET \
                                      --insecure \
                                      -s \
                                      --max-time 30 \
                                      --header "Accept: application/json" \
                                      --header "Authorization: Bearer $API_TOKEN" \
                                      "$API/cloud/region/${CLOUD_REGION_ID}" \
                                  | jq -r '.payload.omicsServiceRole // empty')

    if [ "${CP_OMICS_SERICE_ROLE}" == "empty" ]; then
        pipe_log_fail "Couldn't get Omics Service Role information from Cloud Region with id ${CLOUD_REGION_ID}" $_TASK_NAME
        exit 1
    fi
}

function assume_omics_service_role() {
    pipe_log_info "Omics Service Role is configured by region setting as: $CP_OMICS_SERICE_ROLE, Assuming..." $_TASK_NAME

    unset AWS_ACCESS_KEY_ID
    unset AWS_SECRET_ACCESS_KEY
    unset AWS_SESSION_TOKEN

    AWS_TEMP_CREDS=$(aws sts assume-role --role-arn "$CP_OMICS_SERICE_ROLE" --role-session-name "CP_AWS_OMICS_WORKFLOW_${RUN_ID}" --duration-seconds "${CP_OMICS_ROLE_ASSUME_SESSION_DURATION:-3600}")
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during obtaining temporary credentials for Omics Service role." $_TASK_NAME
        exit 1
    fi

    export AWS_ACCESS_KEY_ID=$(echo "$AWS_TEMP_CREDS" | jq -r .Credentials.AccessKeyId)
    export AWS_SECRET_ACCESS_KEY=$(echo "$AWS_TEMP_CREDS" | jq -r .Credentials.SecretAccessKey)
    export AWS_SESSION_TOKEN=$(echo "$AWS_TEMP_CREDS" | jq -r .Credentials.SessionToken)
}

function obtain_private_ecr_uri_run_from_region() {
    export CP_PRIVATE_ECR=$(curl -X GET \
                          --insecure \
                          -s \
                          --max-time 30 \
                          --header "Accept: application/json" \
                          --header "Authorization: Bearer $API_TOKEN" \
                          "$API/cloud/region/${CLOUD_REGION_ID}" \
                      | jq -r '.payload.omicsEcrUrl // empty')

    if [ "${_CP_PRIVATE_ECR}" == "empty" ]; then
      pipe_log_fail "Couldn't get AWS Private ECR information from Cloud Region with id ${CLOUD_REGION_ID}" $_TASK_NAME
      exit 1
    fi
}


function sync_images_in_private_ecr() {
    _ECR_SYNC_TASK_NAME="AWSOmicsECRSyncImages"
    pipe_log_info "Synchronizing docker images in private ECR repo." $_TASK_NAME
    if [ ! -f /opt/omics/utils/container_syncronizer.sh ]; then
        pipe_log_fail "Script /opt/omics/utils/container_syncronizer.sh wasn't found. Are you using library/aws-omics docker image?" $_TASK_NAME
        exit 1
    fi
    pipe_exec \
      "bash /opt/omics/utils/container_syncronizer.sh --ecr ${CP_PRIVATE_ECR} --workflow_source $SCRIPTS_DIR/src" \
      "${_ECR_SYNC_TASK_NAME}"

    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during Synchronization docker images in private ECR." $_TASK_NAME
        exit 1
    else
      pipe_log_success "Successfully synchronized docker images in private ECR repo." "${_ECR_SYNC_TASK_NAME}"
      pipe_log_info "Successfully synchronized docker images in private ECR repo." "${$_TASK_NAME}"
    fi
}

function prepare_workflow_parameters() {
    _PARAMS_TO_EXCLUDE="engine,output_path,RESYNC_IMAGES"
    PARAM_TYPE_SUFFIX="_PARAM_TYPE"
    _PARAMETERS_TEMPLATE="\"ecr_registry\": { \"description\": \"Private ECR Registry\" }"
    _PARAMETERS="\"ecr_registry\": \"${CP_PRIVATE_ECR}\""
    while IFS= read -r line; do
        PARAM_TYPE_NAME=$(awk -F "=" '{print $1}' <<< $line)
        PARAM_NAME=${PARAM_TYPE_NAME%$PARAM_TYPE_SUFFIX}
        if echo ",$_PARAMS_TO_EXCLUDE," | grep ",$PARAM_NAME," &> /dev/null || [[ "$PARAM_NAME" =~ "CP_"* ]]; then
           pipe_log_info "Skipping parameter $PARAM_NAME, it won't be added to omics workflow parameters configuration." $_TASK_NAME
           continue
        fi
        _PARAMETERS_TEMPLATE="${_PARAMETERS_TEMPLATE}, \"$PARAM_NAME\": { \"description\": \"$PARAM_NAME\" }"
        _PARAMETERS="${_PARAMETERS}, \"$PARAM_NAME\": \"${!PARAM_NAME}\""
    done <<< "$(env | grep ${PARAM_TYPE_SUFFIX})"

    echo "{ ${_PARAMETERS_TEMPLATE} }" | jq > $1
    echo "{ ${_PARAMETERS} }" | jq > $2
}

function package_omics_workflow() {
    _WORKFLOW_DEFINITION_ZIP="/tmp/$2.zip"
    if [ -d "$1" ]; then
        cd "$1" &&  zip -9 -r "$_WORKFLOW_DEFINITION_ZIP" . &> /dev/null && cd - &> /dev/null
    else
        pipe_log_fail "Couldn't find workflow directory by path $1"
        exit 1
    fi
    export WORKFLOW_DEFINITION_ZIP=$_WORKFLOW_DEFINITION_ZIP
}

function cleanup_omics_workflow() {
    pipe_log_info "Removing HealthOmics Workflow on cleanup." $_TASK_NAME
    assume_omics_service_role
    aws omics delete-workflow --id $WORKFLOW_ID
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during cleanup of HealthOmics Workflow, id: $WORKFLOW_ID." $_TASK_NAME
    fi
}

function run_omics_workflow() {
    _workflow_name="$1"
    _workflow_zip="$2"
    _workflow_parameters_template="$3"
    _workflow_parameters="$4"

    _workflow_id=$(aws omics create-workflow \
        --engine $engine --name "$_workflow_name"  --definition-zip "fileb://$_workflow_zip" \
        --parameter-template "file://$_workflow_parameters_template" \
        --query 'id' --output text)
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during HealthOmics Workflow registration." $_TASK_NAME
        exit 1
    else
        trap cleanup_omics_workflow EXIT
    fi

    aws omics wait workflow-active --id "${_workflow_id}"
    if [ $? -ne 0 ]; then
        pipe_log_fail "There was a problem during awaiting HealthOmics Workflow to be available." $_TASK_NAME
        exit 1
    fi

    pipe_log_info "Starting workflow $_workflow_name with id $_workflow_id" $_TASK_NAME
    _workflow_run_id=$(aws omics start-run \
        --role-arn "${CP_OMICS_SERICE_ROLE}" \
        --workflow-id "${_workflow_id}" \
        --name "$_workflow_name" \
        --output-uri ${output_path} \
        --parameters "file://$_workflow_parameters" \
        --query 'id' --output text)
    pipe_log_info "Workflow run with id: $_workflow_run_id started." $_TASK_NAME
    export WORKFLOW_ID="$_workflow_id"
    export WORKFLOW_RUN_ID="$_workflow_run_id"
}

function watch_and_log_omics_workflow_run() {
    _WORKFLOW_RUN_ID=$1
    _WORKFLOW_NAME=$2
    _WORKFLOW_RUN_STATUS="RUNNING"
    _WAITING_TIME=0
    while [ "$_WORKFLOW_RUN_STATUS" != "COMPLETED" ] && [ "$_WORKFLOW_RUN_STATUS" != "FAILED" ] && [ "$_WORKFLOW_RUN_STATUS" != "CANCELLED" ]; do

        sleep 300
        _WAITING_TIME=$((_WAITING_TIME + 300))
        if [ $_WAITING_TIME -gt $TIME_TO_UPDATE_CREDS ]; then
            _WAITING_TIME=0
            pipe_log_info "Updating AWS temporary credentials..." "$_TASK_NAME"
            assume_omics_service_role
        fi

        _WORKFLOW_RUN_STATUS=$(aws omics get-run --id "${_WORKFLOW_RUN_ID}" --query 'status' --output text)
        _TASK_STATUS=$(aws omics list-run-tasks --id ${_WORKFLOW_RUN_ID})
        _TOTAL_TASKS=$(echo $_TASK_STATUS | grep -o "status" | wc -l)
        _RUNNING_TASKS=$(echo $_TASK_STATUS | grep -oE 'STARTING|RUNNING' | wc -l)
        pipe_log_info "Workflow run status: ${_WORKFLOW_RUN_STATUS}. Tasks (completed / total): $((_TOTAL_TASKS - _RUNNING_TASKS)) / ${_TOTAL_TASKS}" "$_TASK_NAME"
    done
    if [ "$_WORKFLOW_RUN_STATUS" == "FAILED" ] && [ "$_WORKFLOW_RUN_STATUS" == "CANCELLED" ]; then
        pipe_log_fail "Workflow run status: $_WORKFLOW_RUN_STATUS" "$_TASK_NAME"
        exit 1
    fi
    pipe_log_success "Workflow run status: $_WORKFLOW_RUN_STATUS" "$_TASK_NAME"
}

export _TASK_NAME="AWSOmicsWorkflow"
export TIME_TO_UPDATE_CREDS=2700
WORKFLOW_NAME="CPOmicsWorkflow-${RUN_ID}"
WORKFLOW_PARAMETERS_TEMPLATE="$SCRIPTS_DIR/src/parameters_template.json"
WORKFLOW_PARAMETERS="$SCRIPTS_DIR/src/parameters.json"

pipe_log_info "Start of the workflow: $WORKFLOW_NAME" "$_TASK_NAME"
preflight_checks

obtain_omics_service_role
assume_omics_service_role
obtain_private_ecr_uri_run_from_region
sync_images_in_private_ecr

pipe_log_info "Packaging workflow in a zip distribution." $_TASK_NAME
package_omics_workflow "$SCRIPTS_DIR/src/workflow" "$WORKFLOW_NAME"
pipe_log_info "Preparing parameters-template.json and parameters.json for a workflow, based on pipeline run parameters." $_TASK_NAME
prepare_workflow_parameters "$WORKFLOW_PARAMETERS_TEMPLATE" "$WORKFLOW_PARAMETERS"
pipe_log_info "Registering workflow and running omics workflow." $_TASK_NAME
run_omics_workflow "$WORKFLOW_NAME" "$WORKFLOW_DEFINITION_ZIP" "$WORKFLOW_PARAMETERS_TEMPLATE" "$WORKFLOW_PARAMETERS"

watch_and_log_omics_workflow_run "$WORKFLOW_RUN_ID" "$WORKFLOW_NAME"

pipe_log_success "Successfully run workflow $WORKFLOW_NAME, workflow_run_id: $WORKFLOW_RUN_ID" "$_TASK_NAME"