#!/bin/bash
# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

function verify_environment() {
   if [ ! -f /opt/omics/utils/omics_workflow_builder.sh ]; then
        pipe_log_fail "Omcis Worklow helper script /opt/omics/utils/omics_workflow_builder.sh wasn't found. Are you using library/aws-healthomics-workflow docker image?" "${LOG_TASK_NAME}"
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
      exit 1
  else
      pipe_log_success "Successfully run workflow $WORKFLOW_NAME" "${TASK_NAME}"
  fi
}

verify_environment && \
execute_workflow
