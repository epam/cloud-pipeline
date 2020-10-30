#!/bin/bash
# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

export HOME=${AZURE_KUBECONFIG_HOME}

exec > ${AZURE_DEPLOY_LOG_LOCATION} 2>&1

_SERVICES_TO_INSTALL="$CP_SERVICES_LIST"
_ERASE_DATA="$CP_ERASE_DATA"

# Simple check for kube and pods availablility
# If kube is not installed yet or DB pod is not available - treat this as a fresh installation
if ! kubectl version &> /dev/null || ! kubectl get po 2>/dev/null | grep -q cp-api-db; then
    echo "kubectl is not installed or API DB pod is not running - full installation will be performed"
    unset _SERVICES_TO_INSTALL
    unset _ERASE_DATA
fi

# Install awscli if not available
curl -s https://bootstrap.pypa.io/get-pip.py | python - && \
    pip install awscli

# Grab required files from the S3
export AWS_ACCESS_KEY_ID="$AWS_ACCESS_KEY_ID"
export AWS_SECRET_ACCESS_KEY="$AWS_SECRET_ACCESS_KEY"
export AWS_DEFAULT_REGION="$AWS_DEFAULT_REGION"
export AZURE_SSH_KEY_S3_LOCAL="${DEPLOY_DIR}/${CP_AZURE_PREF_CLUSTER_SSH_KEY_NAME}"
export AZURE_SSH_PUB_S3_LOCAL="${DOLLAR}{AZURE_SSH_KEY_S3_LOCAL}.pub"
export AZURE_JSON_S3_LOCAL="${DOLLAR}{AZURE_SSH_KEY_S3_LOCAL}.json"

aws s3 cp "$AZURE_SSH_KEY_S3" "${DOLLAR}{AZURE_SSH_KEY_S3_LOCAL}" > /dev/null && \
aws s3 cp "$AZURE_SSH_PUB_S3" "${DOLLAR}{AZURE_SSH_PUB_S3_LOCAL}" > /dev/null && \
aws s3 cp "$AZURE_JSON_S3" "${DOLLAR}{AZURE_JSON_S3_LOCAL}" > /dev/null
if [ $? -ne 0 ]; then
    echo "Unable to get files from S3"
    exit 1
fi

# Grab pipectl binary
wget -q "$PIPECTL_DIST_URL" -O "${DEPLOY_DIR}/pipectl"
if [ $? -ne 0 ]; then
    echo "Unable to get $PIPECTL_DIST_URL"
    exit 1
fi
chmod +x "${DEPLOY_DIR}/pipectl"

# Do install
# Note that for Azure we require two separate disks (in addition to osDisk) to be present on the VM (Premium SSD is better), mounted to 
# $CP_AZURE_KUBE_MASTER_DOCKER_PATH and $CP_AZURE_KUBE_MASTER_ETCD_HOST_PATH
# This allows to overcome disk I/O issues with a single drive
${DEPLOY_DIR}/pipectl install \
                      -env CP_CLUSTER_SSH_KEY="${DOLLAR}{AZURE_SSH_KEY_S3_LOCAL}" \
                      -env CP_CLUSTER_SSH_PUB="${DOLLAR}{AZURE_SSH_PUB_S3_LOCAL}" \
                      -env CP_CLOUD_CREDENTIALS_FILE="${DOLLAR}{AZURE_JSON_S3_LOCAL}" \
                      -env CP_AZURE_STORAGE_ACCOUNT="${CP_AZURE_STORAGE_ACCOUNT}" \
                      -env CP_AZURE_STORAGE_KEY="${CP_AZURE_STORAGE_KEY}" \
                      -env CP_AZURE_DEFAULT_RESOURCE_GROUP=${CP_AZURE_DEFAULT_RESOURCE_GROUP} \
                      -env CP_AZURE_OFFER_DURABLE_ID="${CP_AZURE_OFFER_DURABLE_ID}" \
                      -env CP_AZURE_SUBSCRIPTION_ID="${CP_AZURE_SUBSCRIPTION_ID}" \
                      -env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="${CP_AZURE_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS}" \
                      -env CP_NOTIFIER_SMTP_SERVER_HOST="$CP_NOTIFIER_SMTP_SERVER_HOST" \
                      -env CP_NOTIFIER_SMTP_SERVER_PORT="$CP_NOTIFIER_SMTP_SERVER_PORT" \
                      -env CP_NOTIFIER_SMTP_FROM="$CP_NOTIFIER_SMTP_FROM" \
                      -env CP_NOTIFIER_SMTP_USER="$CP_NOTIFIER_SMTP_USER" \
                      -env CP_NOTIFIER_SMTP_PASS="$CP_NOTIFIER_SMTP_PASS" \
                      -env CP_DEFAULT_ADMIN_EMAIL="$CP_DEFAULT_ADMIN_EMAIL" \
                      -env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME="${CP_AZURE_PREF_STORAGE_SYSTEM_STORAGE_NAME}" \
                      -env CP_DOCKER_STORAGE_TYPE="${CP_AZURE_DOCKER_STORAGE_TYPE}" \
                      -env CP_DOCKER_STORAGE_CONTAINER="${CP_AZURE_DOCKER_STORAGE_CONTAINER}" \
                      -env CP_DEPLOYMENT_ID="${CP_AZURE_DEPLOYMENT_ID}" \
                      -env CP_KUBE_MASTER_DOCKER_PATH="${CP_AZURE_KUBE_MASTER_DOCKER_PATH}" \
                      -env CP_KUBE_MASTER_ETCD_HOST_PATH="${CP_AZURE_KUBE_MASTER_ETCD_HOST_PATH}" \
                      -env CP_KUBE_MIN_DNS_REPLICAS=3 \
                      -env CP_PREF_CLUSTER_ALLOWED_PRICE_TYPES="${CP_PREF_CLUSTER_ALLOWED_PRICE_TYPES}" \
                      -env CP_PREF_CLUSTER_ALLOWED_MASTER_PRICE_TYPES="${CP_PREF_CLUSTER_ALLOWED_MASTER_PRICE_TYPES}" \
                      -env CP_PREF_CLUSTER_SPOT="${CP_PREF_CLUSTER_SPOT}" \
                      -m \
                      -demo  ${DOLLAR}_SERVICES_TO_INSTALL ${DOLLAR}_ERASE_DATA
