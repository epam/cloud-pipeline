#!/bin/bash

# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

# Write any output of this script to CP_WDL_TASK_LOGS (Defaults to /var/log/cromwell_runs.log)
CP_WDL_TASK_LOGS=${CP_WDL_TASK_LOGS:-"/var/log/cromwell_runs.log"}
exec > $CP_WDL_TASK_LOGS 2>&1

# Parse args
POSITIONAL=()
while [[ $# -gt 0 ]]
do
    key="$1"

    case $key in
        -d|--docker)
        export CP_WDL_TASK_DOCKER="$2"
        shift
        shift
        ;;
        -n|--node)
        export CP_WDL_TASK_NODE="$2"
        shift
        shift
        ;;
        -p|--pipeline)
        export CP_WDL_TASK_PIPELINE="$2"
        shift
        shift
        ;;
        -j|--job-name)
        export CP_WDL_TASK_JOB_NAME="$2"
        shift
        shift
        ;;
        -s|--script)
        export CP_WDL_TASK_SCRIPT="$2"
        shift
        shift
        ;;
        *)    # unknown option
        POSITIONAL+=("$1") # save it in an array for later
        shift # past argument
        ;;
    esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

# Validate args
if [ -z "$CP_WDL_TASK_SCRIPT" ] || [ -z "$CP_WDL_TASK_JOB_NAME" ]; then
    echo "ERROR: Task script or job name are not defined, exiting"
    exit 1
fi

# No docker, No node, No pipeline - just run a script locally
if [ -z "$CP_WDL_TASK_DOCKER" ] && [ -z "$CP_WDL_TASK_NODE" ] && [ -z "$CP_WDL_TASK_PIPELINE" ]; then
    /bin/bash ${CP_WDL_TASK_SCRIPT}
    exit $?
fi

# Otherwise collect `pipe run` options
[ "$CP_WDL_TASK_NODE" ] && \
    CP_WDL_TASK_PARENT_SPEC="--parent-id $RUN_ID --RUN_ON_PARENT_NODE true" || \
    CP_WDL_TASK_PARENT_SPEC="--parent-node $RUN_ID"

[ -z "$CP_WDL_TASK_PIPELINE" ] && \
    CP_WDL_TASK_CMD_TEMPLATE="--cmd-template \"bash ${CP_WDL_TASK_SCRIPT}\""

[ "$CP_WDL_TASK_DOCKER" ] && \
    CP_WDL_TASK_DOCKER_IMAGE="--docker-image \"${CP_WDL_TASK_DOCKER}\""

[ -z "$CP_WDL_TASK_DOCKER" ] && [ "$CP_WDL_TASK_NODE" ] && \
    CP_WDL_TASK_DOCKER_IMAGE="--docker-image \"$docker_image\""

[ "$CP_WDL_TASK_NODE" ] && \
    CP_WDL_TASK_INSTANCE_TYPE_SPEC="--instance-type \"${CP_WDL_TASK_NODE}\""

[ "$CP_WDL_TASK_NODE" ] && [ -z "$CP_WDL_TASK_PIPELINE" ] && \
    CP_WDL_TASK_INSTANCE_DISK_SPEC="--instance-disk \"20\""
  

if [ "$CP_WDL_TASK_PIPELINE" ]; then
    CP_WDL_TASK_PIPELINE_SPEC="--pipeline \"${CP_WDL_TASK_PIPELINE}\""
    # Additional parameters build routine if a pipeline run is submitted
    # It will parse all parameter names from `# PARAM NAME` entries in the command template and pass it to the pipe run
    while read param; do
        param_items=($param)
        param_name="${param_items[-2]}"
        param_value="${param_items[-1]}"
        if [ -z "$param_value" ] || [ -z "$param_name" ]; then
            echo "Parameter name or value of the pipeline $CP_WDL_TASK_PIPELINE is empty (Actual Name: $param_name Value: $param_value)"
            exit 1
        fi
        CP_WDL_TASK_PIPELINE_PARAMS="$pipeline_params --$param_name \"$param_value\""
    done <<< "$(grep '^[ \t]*#[ \t]*PARAM' < $CP_WDL_TASK_SCRIPT)"
fi

# Generate `pipe run` command using options collected above
CP_WDL_TASK_CMD="pipe run   --cluster_role worker \
                            --cluster_role_type additional \
                            --job_name ${CP_WDL_TASK_JOB_NAME} \
                            --quiet \
                            --yes \
                            --sync \
                            $CP_WDL_TASK_PARENT_SPEC \
                            $CP_WDL_TASK_CMD_TEMPLATE \
                            $CP_WDL_TASK_DOCKER_IMAGE \
                            $CP_WDL_TASK_INSTANCE_TYPE_SPEC \
                            $CP_WDL_TASK_INSTANCE_DISK_SPEC \
                            $CP_WDL_TASK_PIPELINE_SPEC \
                            $CP_WDL_TASK_PIPELINE_PARAMS"

# Once again check - if it's a pipeline run - add the CP_WDL_TASK_CMD to the original script and run with /bin/bash ...
# Otherwise Cromwell will be waiting for CP_WDL_TASK_SCRIPT return code forever
if [ "$CP_WDL_TASK_PIPELINE" ]; then
    if ! grep -q "# PIPE_RUN_COMMAND" < "$CP_WDL_TASK_SCRIPT"; then
        echo "No PIPE_RUN_COMMAND placeholder defined in $CP_WDL_TASK_SCRIPT script, pipeline will not executed"
        exit 1
    fi
    # Remove extra whitespaces via echoing back
    CP_WDL_TASK_CMD="$(echo $CP_WDL_TASK_CMD)"
    sed -i "s|# PIPE_RUN_COMMAND|$CP_WDL_TASK_CMD|g" "$CP_WDL_TASK_SCRIPT"
    /bin/bash "$CP_WDL_TASK_SCRIPT"
else
    # If not a pipeline run - execute `pipe run` directly
    eval "$CP_WDL_TASK_CMD"
fi
