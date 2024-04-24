#!/bin/bash

_TASK_NAME="AWSOmicsWorkflow"

if [ "${engine}" != "NEXTFLOW" ] && [ "${engine}" != "WDL" ] && [ "${engine}" != "CWL" ]; then
    pipe_log_fail "Parameter engine is set to: '${engine}', allowed options are: 'NEXTFLOW', 'CWL', 'WDL'" $_TASK_NAME
    exit 1
fi

which aws &> /dev/null
if [ $? -ne 0 ]; then
    pipe_log_fail "Can't find aws utility. Are you using library/aws-omics docker image?" $_TASK_NAME
    exit 1
fi

CP_OMICS_SERICE_ROLE=$(curl -X GET \
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
else
  pipe_log_info "Omics Service Role is configured by region setting as: $CP_OMICS_SERICE_ROLE, Assuming..." $_TASK_NAME
  aws sts assume-role --role-arn "$CP_OMICS_SERICE_ROLE" \
      --role-session-name "CP_AWS_OMICS_WORKFLOW_${RUN_ID}" \
      --duration-seconds "${CP_OMICS_ROLE_ASSUME_SESSION_DURATION:-3600}"
fi

CP_PRIVATE_ECR=$(curl -X GET \
                      --insecure \
                      -s \
                      --max-time 30 \
                      --header "Accept: application/json" \
                      --header "Authorization: Bearer $API_TOKEN" \
                      "$API/cloud/region/${CLOUD_REGION_ID}" \
                  | jq -r '.payload.omicsEcrUrl // empty')

if [ "${CP_PRIVATE_ECR}" == "empty" ]; then
  pipe_log_fail "Couldn't get AWS Private ECR information from Cloud Region with id ${CLOUD_REGION_ID}" $_TASK_NAME
  exit 1
else
  pipe_log_info "AWS Private ECR for Omics workflow is configured by region setting as: $CP_PRIVATE_ECR" $_TASK_NAME
fi

pipe_log_info "Synchronizing docker images in private ECR repo." $_TASK_NAME
if [ ! -f /opt/omics/utils/container_syncronizer.sh ]; then
    pipe_log_fail "Script /opt/omics/utils/container_syncronizer.sh wasn't found. Are you using library/aws-omics docker image?" $_TASK_NAME
    exit 1
fi
ECR_SYNC_CMD="bash /opt/omics/utils/container_syncronizer.sh --public_registry_properties /opt/omics/utils/public_registry_properties.json --ecr ${CP_PRIVATE_ECR}"
if [ -f $SCRIPTS_DIR/src/image-build-manifest.json ]; then
    ECR_SYNC_CMD="$ECR_SYNC_CMD --image_build_config $SCRIPTS_DIR/src/image-build-manifest.json"
fi

if [ -f $SCRIPTS_DIR/src/image-pull-manifest.json ]; then
    ECR_SYNC_CMD="$ECR_SYNC_CMD --image_pull_config $SCRIPTS_DIR/src/image-pull-manifest.json"
fi

_SYNC_IMAGES_TASK_NAME="AWSOmicsECRSyncImages"
pipe_exec "$ECR_SYNC_CMD" $_SYNC_IMAGES_TASK_NAME
pipe_log_info "Successfully synchronized docker images in private ECR repo." $_TASK_NAME

pipe_log_info "Preparing parameters-template.json and parameters.json for a workflow, based on pipeline run parameters." $_TASK_NAME
PARAM_TYPE_SUFFIX="_PARAM_TYPE"
while IFS= read -r line; do
    PARAM_TYPE_NAME=$(awk -F "=" '{print $1}' <<< $line)
    PARAM_NAME=${PARAM_TYPE_NAME%$PARAM_TYPE_SUFFIX}
    if [ -z "$_PARAMETERS_TEMPLATE" ]; then
        _PARAMETERS_TEMPLATE="\"$PARAM_NAME\": { \"description\": \"value of $PARAM_NAME\" }"
    else
        _PARAMETERS_TEMPLATE="${_PARAMETERS_TEMPLATE}, \"$PARAM_NAME\": { \"description\": \"$PARAM_NAME\" }"
    fi

    if [ -z "$_PARAMETERS" ]; then
        _PARAMETERS="\"$PARAM_NAME\": \"${!PARAM_NAME}\""
    else
        _PARAMETERS="${_PARAMETERS}, \"$PARAM_NAME\": \"${!PARAM_NAME}\""
    fi
done <<< "$(env | grep ${PARAM_TYPE_SUFFIX})"

echo "{ ${_PARAMETERS_TEMPLATE} }" | jq > $SCRIPTS_DIR/src/parameters_template.json
echo "{ ${_PARAMETERS} }" | jq > $SCRIPTS_DIR/src/parameters.json

pipe_log_info "Packaging workflow in a zip distribution." $_TASK_NAME
_WORKFLOW_DEFINITION_ZIP="/tmp/cp_omics_workflow_${RUN_ID}.zip"
if [ -d $SCRIPTS_DIR/src/workflow ]; then
    zip -9 -r "$_WORKFLOW_DEFINITION_ZIP" $SCRIPTS_DIR/src/workflow
else
    pipe_log_fail "Couldn't find workflow directory in $SCRIPTS_DIR/src"
fi

pipe_log_info "Registering workflow." $_TASK_NAME
workflow_name="cp_omics_workflow_${RUN_ID}"
workflow_id=$(aws omics create-workflow \
    --engine $engine \
    --definition-zip $_WORKFLOW_DEFINITION_ZIP \
    --name "$workflow_name" \
    --parameter-template file://$SCRIPTS_DIR/src/parameters_template.json \
    --query 'id' \
    --output text
)
pipe_log_info "Waiting for the workflow to be available." $_TASK_NAME
aws omics wait workflow-active --id "${workflow_id}"

pipe_log_info "Receiving workflow metadata." $_TASK_NAME
aws omics get-workflow --id "${workflow_id}" > "/tmp/${workflow_name}.json"

aws omics start-run \
    --role-arn "${CP_OMICS_SERICE_ROLE}" \
    --workflow-id "$(jq -r '.id' ${workflow_name}.json)" \
    --name "$workflow_name" \
    --output-uri ${output_path} \
    --parameters file://$SCRIPTS_DIR/src/parameters.json