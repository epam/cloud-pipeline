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

SCRIPT_PATH="$SCRIPTS_DIR/common_commit_initialization.sh"
. $SCRIPT_PATH

commit_file $FULL_NEW_IMAGE_NAME
check_last_exit_code $? "[ERROR] Error occurred while committing temporary container" \
                        "[INFO] Temporary container was successfully committed with name: $image_name" \
                        "python $COMMON_REPO_DIR/scripts/commit_run.py ups $RUN_ID FAILURE"

export tmp_container=`docker run --entrypoint "/bin/sleep" -d ${FULL_NEW_IMAGE_NAME} 1d`

if [[ ! -z "${PRE_COMMIT_COMMAND}" ]]; then
    pipe_log_info "[INFO] Pre-commit command found by path ${PRE_COMMIT_COMMAND}" "$TASK_NAME"
    pipe_exec "docker exec ${tmp_container} ls ${PRE_COMMIT_COMMAND} > /dev/null" "$TASK_NAME"
    if [[ $? -eq 0 ]]; then
        pipe_log_info "[INFO] Run pre-commit command" "$TASK_NAME"
        pipe_exec "docker exec ${tmp_container} sh -c '${PRE_COMMIT_COMMAND} ${CLEAN_UP} ${STOP_PIPELINE}'" "$TASK_NAME"
        check_last_exit_code $? "[ERROR] There are some troubles while executing pre-commit script." \
                        "[INFO] Pre-commit operations were successfully performed."
    fi
fi

pipe_log_info "[INFO] Clean up container with env vars and files ..." "$TASK_NAME"
docker cp $SCRIPTS_DIR/cleanup_container.sh "${tmp_container}":/

ENVS_TO_UNSET=`docker exec "${tmp_container}" sh -c "chmod +x /cleanup_container.sh && /cleanup_container.sh $CLEAN_UP && rm /cleanup_container.sh"`

check_last_exit_code $? "[ERROR] There are some troubles while clean up pipeline's container." \
                        "[INFO] Clean up for pipeline's container was successfully performed." \
                        "python $COMMON_REPO_DIR/scripts/commit_run.py ups $RUN_ID FAILURE"

pipe_exec "docker commit --change=\'ENV $ENVS_TO_UNSET API_TOKEN= PARENT= RUN_DATE= RUN_TIME= AWS_ACCESS_KEY_ID= AWS_SECRET_ACCESS_KEY= AWS_DEFAULT_REGION= \
           CLUSTER_NAME= BUCKETS= MOUNT_OPTIONS= MOUNT_POINTS= OWNER= SSH_PASS= GIT_USER= GIT_TOKEN= cluster_role= \
           node_count= parent_id= \' ${tmp_container} ${FULL_NEW_IMAGE_NAME} > /dev/null" "$TASK_NAME"

commit_exit_code=$?

if [[ ! -z "${POST_COMMIT_COMMAND}" ]]; then
    pipe_log_info "[INFO] Post-commit command found by path ${POST_COMMIT_COMMAND}" "$TASK_NAME"
    pipe_exec "docker exec ${tmp_container} ls ${POST_COMMIT_COMMAND} > /dev/null" "$TASK_NAME"
    if [[ $? -eq 0 ]]; then
        pipe_log_info "[INFO] Run post-commit command" "$TASK_NAME"
        pipe_exec "docker exec ${tmp_container} sh -c '${POST_COMMIT_COMMAND} ${CLEAN_UP} ${STOP_PIPELINE}'" "$TASK_NAME"
        check_last_exit_code $? "[ERROR] There are some troubles while executing post-commit script." \
                        "[INFO] Post-commit operations were successfully performed."
    fi
fi

check_last_exit_code "${commit_exit_code}" \
                        "[ERROR] Error occurred while committing container" \
                        "[INFO] Container was successfully committed with name: ${FULL_NEW_IMAGE_NAME}" \
                        "python $COMMON_REPO_DIR/scripts/commit_run.py ups $RUN_ID FAILURE"

docker stop ${tmp_container}

if [[ "$DOCKER_LOGIN" && "$DOCKER_PASSWORD" ]]; then
    pipe_log_info "[INFO] Registry: ${REGISTRY_TO_PUSH} is a private docker registry. Authorization ..." "$TASK_NAME"
    docker login -u "$DOCKER_LOGIN" -p "$DOCKER_PASSWORD" "${REGISTRY_TO_PUSH}"
    check_last_exit_code $? "[ERROR] Error occurred while logging into ${REGISTRY_TO_PUSH}." \
                            "[INFO] Login to  ${REGISTRY_TO_PUSH} was successfully performed." \
                            "python $COMMON_REPO_DIR/scripts/commit_run.py ups $RUN_ID FAILURE"
fi

IS_TOOL_EXISTS=`python $COMMON_REPO_DIR/scripts/commit_run.py ite $RUN_ID $TOOL_GROUP_ID $NEW_IMAGE_NAME`
echo "IS_TOOL_EXISTS = $IS_TOOL_EXISTS"

pipe_log_info "[INFO] Pushing to ${REGISTRY_TO_PUSH} ..." "$TASK_NAME"
pipe_exec "docker push ${FULL_NEW_IMAGE_NAME} > /dev/null" "$TASK_NAME"
check_last_exit_code $? "[ERROR] Error occurred while pushing image" \
                        "[INFO] Image was successfully pushed." \
                        "python $COMMON_REPO_DIR/scripts/commit_run.py ups $RUN_ID FAILURE"

if [[ "$IS_TOOL_EXISTS" = "False" ]]; then
    pipe_log_info "[INFO] Applying setting from the parent image ..." "$TASK_NAME"
    pipe_exec "python $COMMON_REPO_DIR/scripts/commit_run.py etwc $RUN_ID $REGISTRY_TO_PUSH $REGISTRY_TO_PUSH_ID $TOOL_GROUP_ID $NEW_IMAGE_NAME 600 > /dev/null" "$TASK_NAME"
    if [[ $? -ne 0 ]]; then
        pipe_log_info "[WARN] Can't apply settings from parent image to committed one. You have to do in manually." "$TASK_NAME"
    else
        pipe_log_info "[INFO] Settings from parent image were successfully applied to the committed one." "$TASK_NAME"
    fi
fi

IS_VERSION_EXISTS=`python $COMMON_REPO_DIR/scripts/commit_run.py ive $RUN_ID $FULL_NEW_IMAGE_NAME latest`
echo "IS_VERSION_EXISTS = $IS_VERSION_EXISTS"

if [[ "$IS_VERSION_EXISTS" = "False" ]]; then
    pipe_log_info "[INFO] $FULL_NEW_IMAGE_NAME tool does not contain latest version, this may introduce issues in running this tool. Will create latest version..." "$TASK_NAME"
    # Replace existing tag in FULL_NEW_IMAGE_NAME if exists with latest
    LATEST_NEW_IMAGE_NAME=$(sed -e 's/\:[^\:/]*$//' <<< $FULL_NEW_IMAGE_NAME):latest
    # Tag FULL_NEW_IMAGE_NAME with latest
    pipe_exec "docker tag ${FULL_NEW_IMAGE_NAME} ${LATEST_NEW_IMAGE_NAME} > /dev/null" "$TASK_NAME"
    if [[ $? -ne 0 ]]; then
        pipe_log_warn "[WARN] Error occurred while tagging ${FULL_NEW_IMAGE_NAME} as ${LATEST_NEW_IMAGE_NAME}. latest version will not be created" "$TASK_NAME"
    else
        # Push latest
        pipe_exec "docker push ${LATEST_NEW_IMAGE_NAME} > /dev/null" "$TASK_NAME"
        if [[ $? -ne 0 ]]; then
            pipe_log_warn "[WARN] Error occurred while pushing ${LATEST_NEW_IMAGE_NAME}. latest version was not created" "$TASK_NAME"
        else
            pipe_log_info "[INFO] latest version created as ${LATEST_NEW_IMAGE_NAME}" "$TASK_NAME"
        fi
    fi
fi

echo "Applying configuration settings from initial tool to a new one"
pipe_exec "python $COMMON_REPO_DIR/scripts/commit_run.py evs $RUN_ID $REGISTRY_TO_PUSH $NEW_IMAGE_NAME > /dev/null" "$TASK_NAME"
if [[ $? -ne 0 ]]; then
     pipe_log_warn "[WARN] Error occurred while applying settings to ${FULL_NEW_IMAGE_NAME}." "$TASK_NAME"
else
     pipe_log_info "[INFO] Settings successfully applied to ${FULL_NEW_IMAGE_NAME}" "$TASK_NAME"
fi

pipe_exec "python $COMMON_REPO_DIR/scripts/commit_run.py ups $RUN_ID SUCCESS > /dev/null" "$TASK_NAME"
pipe_log_success "[INFO] Commit pipeline run task succeeded" "$TASK_NAME"

if [[ "$STOP_PIPELINE" = true || "$STOP_PIPELINE" = TRUE ]]; then
    pipe_log_info "[INFO] STOP_PIPELINE flag was got. Pipeline will be stopped." "$TASK_NAME"
	pipe_exec "python $COMMON_REPO_DIR/scripts/commit_run.py sp $RUN_ID > /dev/null" "$TASK_NAME"
	if [[ $? -ne 0 ]]; then
		docker stop "$CONTAINER_ID"
        pipe_log_info "[WARN] Pipeline was stopped by force." "$TASK_NAME"
    else
        pipe_log_info "[INFO] Pipeline was successfully stopped." "$TASK_NAME"
    fi
fi
