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

_SERVICES_TO_INSTALL="$CP_SERVICES_LIST"
_ERASE_DATA="$CP_ERASE_DATA"

# Simple check for kube and pods availablility
# If kube is not installed yet or DB pod is not available - treat this as a fresh installation
if ! kubectl version &> /dev/null || ! kubectl get po 2>/dev/null | grep -q cp-api-db; then
    echo "kubectl is not installed or API DB pod is not running - full installation will be performed"
    unset _SERVICES_TO_INSTALL
    unset _ERASE_DATA
fi

sudo chmod +x $DEPLOY_DIR/pipectl && \
sudo -E $DEPLOY_DIR/pipectl install \
    -env CP_AWS_KMS_ARN="$CP_AWS_KMS_ARN" \
    -env CP_AWS_ACCESS_KEY_ID="$CP_AWS_ACCESS_KEY_ID" \
    -env CP_AWS_SECRET_ACCESS_KEY="$CP_AWS_SECRET_ACCESS_KEY" \
    -env CP_PREF_CLUSTER_SSH_KEY_NAME="$CP_PREF_CLUSTER_SSH_KEY_NAME" \
    -env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="$CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS" \
    -env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE="$CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE" \
    -env CP_NOTIFIER_SMTP_SERVER_HOST="$CP_NOTIFIER_SMTP_SERVER_HOST" \
    -env CP_NOTIFIER_SMTP_SERVER_PORT="$CP_NOTIFIER_SMTP_SERVER_PORT" \
    -env CP_NOTIFIER_SMTP_FROM="$CP_NOTIFIER_SMTP_FROM" \
    -env CP_NOTIFIER_SMTP_USER="$CP_NOTIFIER_SMTP_USER" \
    -env CP_NOTIFIER_SMTP_PASS="$CP_NOTIFIER_SMTP_PASS" \
    -env CP_DEFAULT_ADMIN_EMAIL="$CP_DEFAULT_ADMIN_EMAIL" \
    -env CP_CLUSTER_SSH_KEY="$DEPLOY_DIR/$CP_PREF_CLUSTER_SSH_KEY_NAME" \
    -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME="$CP_PREF_STORAGE_SYSTEM_STORAGE_NAME" \
    -env CP_CLOUD_REGION_FILE_STORAGE_HOSTS="$CP_CLOUD_REGION_FILE_STORAGE_HOSTS" \
    -env CP_DOCKER_STORAGE_TYPE="$CP_DOCKER_STORAGE_TYPE" \
    -env CP_DOCKER_STORAGE_CONTAINER="$CP_DOCKER_STORAGE_CONTAINER" \
    -env CP_DEPLOYMENT_ID="$CP_DEPLOYMENT_ID" \
    -m \
    -demo ${DOLLAR}_SERVICES_TO_INSTALL ${DOLLAR}_ERASE_DATA
