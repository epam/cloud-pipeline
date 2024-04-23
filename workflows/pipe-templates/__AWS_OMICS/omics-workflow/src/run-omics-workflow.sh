#!/bin/bash

_TASK_NAME="AWSOmicsWorkflow"

if [ "${engine}" != "NEXTFLOW" ] && [ "${engine}" != "WDL" ] && [ "${engine}" != "CWL" ]; then
    pipe_log ERROR "Parameter engine is set to: '${engine}', allowed options are: 'NEXTFLOW', 'CWL', 'WDL'" $_TASK_NAME
    exit 1
fi

which aws &> /dev/null
if [ $? -ne 0 ]; then
    pipe_log ERROR "Can't find aws utility. Are you using library/aws-omics docker image?" $_TASK_NAME
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
    pipe_log ERROR "Couldn't get Omics Service Role information from Cloud Region with id ${CLOUD_REGION_ID}" $_TASK_NAME
    exit 1
else
  pipe_log INFO "Omics Service Role is configured by region setting as: $CP_OMICS_SERICE_ROLE, Assuming..." $_TASK_NAME
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
    pipe_log ERROR "Couldn't get AWS Private ECR information from Cloud Region with id ${CLOUD_REGION_ID}" $_TASK_NAME
    exit 1
else
  pipe_log INFO "AWS Private ECR for Omics workflow is configured by region setting as: $CP_PRIVATE_ECR" $_TASK_NAME
fi

pipe_log INFO "Synchronizing docker images in private ECR repo." $_TASK_NAME
if [ ! -f /opt/omcis-utils/container_syncronizer.sh ]; then
    pipe_log ERROR "Script /opt/omcis-utils/container_syncronizer.sh wasn't found. Are you using library/aws-omics docker image?" $_TASK_NAME
    exit 1
fi
ECR_SYNC_CMD="bash ./omics-utils/container_syncronizer.sh --public_registry_properties /opt/omics-utils/public_registry_properties.json --ecr ${CP_PRIVATE_ECR}"
if [ -f $SCRIPTS_DIR/src/image-build-manifest.json ]; then
    ECR_SYNC_CMD="$ECR_SYNC_CMD --image_build_config $SCRIPTS_DIR/src/image-build-manifest.json"
fi

if [ -f $SCRIPTS_DIR/src/image-pull-manifest.json ]; then
    ECR_SYNC_CMD="$ECR_SYNC_CMD --image_pull_config $SCRIPTS_DIR/src/image-pull-manifest.json"
fi

pipe_exec "$ECR_SYNC_CMD" $_TASK_NAME

