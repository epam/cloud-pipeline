#!/bin/bash
# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

source /usr/local/bin/checkout_url "$API_DIST_URL"
bash $WORKSPACE/cloud-pipeline/deploy/jenkins/jenkins-jobs/build-pipectl/build-pipectl.sh


if [ $? -ne 0 ]; then
	echo "Build has failed, skipping deployment"
	exit 1
fi

if [ "$dist_branch" != "develop" ]; then
	echo "Skipping ${dist_branch} deployment"
    exit 0
fi

export PIPECTL_DIST_URL_PATH=/tmp/${JOB_NAME}-${BUILD_NUMBER}.env

if [ "$SKIP_DEPLOYMENT" == "true" ]; then
    echo "SKIP_DEPLOYMENT is set - skipping deployment"
    exit 0
fi

if [ ! -f "$PIPECTL_DIST_URL_PATH" ]; then
    echo "PIPECTL_DIST_URL_PATH is not defined or file does not exist"
    exit 1
fi

cd $WORKSPACE
rm -rf $WORKSPACE/cloud-pipeline

set -o allexport
source "$PIPECTL_DIST_URL_PATH"
set +o allexport

source /usr/local/bin/checkout_url "$PIPECTL_DIST_URL"
bash $WORKSPACE/cloud-pipeline/deploy/jenkins/jenkins-jobs/deploy-dev-aws/prepare-assets.sh
