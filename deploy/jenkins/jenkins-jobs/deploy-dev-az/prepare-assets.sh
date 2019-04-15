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

if [ "$AZURE_VM_NAME_FORCE" ]; then
    echo "Azure VM name was forced to $AZURE_VM_NAME_FORCE instead of a default $AZURE_VM_NAME"
    export AZURE_VM_NAME=$AZURE_VM_NAME_FORCE
fi

if [ "$AZURE_VM_RG_FORCE" ]; then
    echo "Azure VM resource group was forced to $AZURE_VM_RG_FORCE instead of a default $AZURE_VM_RG"
    export AZURE_VM_RG=$AZURE_VM_RG_FORCE
fi


echo "Deploying using $PIPECTL_DIST_URL to a VM $AZURE_VM_NAME in $AZURE_VM_RG resource group"

export DEPLOY_DIR="$WORKSPACE/assets"
rm -rf "$DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR"

###########################
# Prepare installation command
###########################

# Full list of services available for pipectl
# cp-api-db
# cp-api-srv
# cp-git
# cp-git-sync
# cp-idp
# cp-edge
# cp-notifier
# cp-docker-registry
# cp-docker-comp
# cp-clair
# cp-search
# cp-heapster

# For incremental deploys - we do not clear the DBs and restart only cloud pipeline's services
export CP_SERVICES_LIST="-s cp-api-srv -s cp-git-sync -s cp-edge -s cp-notifier -s cp-docker-comp"
# And do not clear the data
unset CP_ERASE_DATA

# Substitute parameters and write command to the file
export DOLLAR="$"
envsubst <$WORKSPACE/cloud-pipeline/deploy/jenkins/jenkins-jobs/deploy-dev-az/run-pipectl.sh > $DEPLOY_DIR/execute.sh


###########################
# Prepare az cli
###########################

AZ_USERNAME="$(cat $AZURE_AUTH_LOCATION | jq -r .clientId)"
AZ_PASS="$(cat $AZURE_AUTH_LOCATION | jq -r .clientSecret)"
AZ_TENANT="$(cat $AZURE_AUTH_LOCATION | jq -r .tenantId)"

az login --service-principal \
         --username "$AZ_USERNAME" \
         --password "$AZ_PASS" \
         --tenant "$AZ_TENANT" > /dev/null

if [ $? -ne 0 ]; then
    echo "Unable to configure Azure CLI"
    exit 1
fi


###########################
# Run installation
###########################

az vm run-command invoke --resource-group "$AZURE_VM_RG" \
                         --name "$AZURE_VM_NAME" \
                         --command-id "RunShellScript" \
                         --scripts @$DEPLOY_DIR/execute.sh
