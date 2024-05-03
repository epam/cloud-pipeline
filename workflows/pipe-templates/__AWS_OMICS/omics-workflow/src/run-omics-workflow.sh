#!/bin/bash

function verify_environment() {
   if [ ! -f /opt/omics/utils/omics_workflow_builder.sh ]; then
        pipe_log_fail "Omcis Worklow helper script /opt/omics/utils/omics_workflow_builder.sh wasn't found. Are you using library/aws-omics-workflow docker image?" "${LOG_TASK_NAME}"
        exit 1
    fi
}

function execute_workflow() {
  local ENGINE=${ENGINE:-NEXTFLOW}
  local TASK_NAME="AWSOmicsWorkflow"
  local WORKFLOW_NAME="CPOmicsWorkflow-${RUN_ID}"

  pipe_log_info "Start of the workflow: $WORKFLOW_NAME" "${TASK_NAME}"
  bash /opt/omics/utils/omics_workflow_builder.sh --engine "$ENGINE" --workflow_name "$WORKFLOW_NAME" --logging_task "$TASK_NAME"

  if [ $? -ne 0 ]; then
      pipe_log_fail "There were problems with running HealthOmics workflow: $WORKFLOW_NAME. Please, check logs for errors." "${TASK_NAME}"
  else
      pipe_log_success "Successfully run workflow $WORKFLOW_NAME" "${TASK_NAME}"
  fi
}

verify_environment && \
execute_workflow
