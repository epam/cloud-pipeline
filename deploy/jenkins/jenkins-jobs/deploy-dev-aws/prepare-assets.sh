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


###########################
# Prepare assets locally
###########################

if [ -z "$PIPECTL_DIST_URL" ]; then
    echo "PIPECTL_DIST_URL is not defined and cannot be sourced from $PIPECTL_DIST_URL_PATH"
    exit 1
fi

if [ "$AWS_HOST_FORCE" ]; then
    echo "AWS host address was forced to $AWS_HOST_FORCE instead of a default $AWS_HOST"
    export AWS_HOST=$AWS_HOST_FORCE
fi

echo "Deploying using $PIPECTL_DIST_URL to $AWS_HOST"

set -o allexport
source "${JENKINS_ENV}"
set +o allexport

export DEPLOY_DIR="$WORKSPACE/assets"
rm -rf "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR"

wget -q "$PIPECTL_DIST_URL" -O "$DEPLOY_DIR/pipectl"
if [ $? -ne 0 ]; then
    echo "Unable to download pipectl distribution from $PIPECTL_DIST_URL"
    exit 1
fi

AWS_SSH_KEY_PATH_TMP="$DEPLOY_DIR/$CP_PREF_CLUSTER_SSH_KEY_NAME"
aws s3 cp "$AWS_SSH_KEY_S3" "$AWS_SSH_KEY_PATH_TMP" &>/dev/null &&
    chmod 600 "$AWS_SSH_KEY_PATH_TMP"

###########################
# Prepare installation command
###########################

# Substitute parameters and write command to the file
export DOLLAR="$"
envsubst <$WORKSPACE/cloud-pipeline/deploy/jenkins/jenkins-jobs/deploy-dev-aws/run-pipectl.sh > $DEPLOY_DIR/execute.sh


###########################
# Upload assets
###########################

rsync --rsync-path="sudo mkdir -p $DEPLOY_DIR && sudo rsync" \
    -e "ssh -o StrictHostKeyChecking=no
            -o LogLevel=ERROR
            -o UserKnownHostsFile=/dev/null
            -i $AWS_SSH_KEY_PATH_TMP" \
    "$DEPLOY_DIR/" \
    -r \
    --ignore-times \
    "${AWS_SSH_USER}@${AWS_HOST}:${DEPLOY_DIR}/"

if [ $? -ne 0 ]; then
    echo "Unable to publish assets to the host"
    exit 1
fi


###########################
# Run installation
###########################

ssh -o StrictHostKeyChecking=no \
    -o LogLevel=ERROR \
    -o UserKnownHostsFile=/dev/null \
    -i "$AWS_SSH_KEY_PATH_TMP" \
    "${AWS_SSH_USER}@${AWS_HOST}" \
    "cd $DEPLOY_DIR; sudo bash $DEPLOY_DIR/execute.sh"


###########################
# Cleanup
###########################

rm -f "$AWS_SSH_KEY_PATH_TMP"
