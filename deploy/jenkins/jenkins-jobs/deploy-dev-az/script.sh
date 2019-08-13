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

if [ "$SKIP_DEPLOYMENT" == "true" ] || [ "$SKIP_DEPLOYMENT_AZ" == "true" ]; then
    echo "SKIP_DEPLOYMENT or SKIP_DEPLOYMENT_AZ is set - skipping Azure deployment"
    exit 0
fi

if [ -z "$PIPECTL_DIST_URL_PATH" ] || [ ! -f "$PIPECTL_DIST_URL_PATH" ]; then
    echo "PIPECTL_DIST_URL_PATH is not defined or file does not exist"
    exit 1
fi

set -o allexport
source "$PIPECTL_DIST_URL_PATH"
set +o allexport

source /usr/local/bin/checkout_url "$PIPECTL_DIST_URL"
bash $WORKSPACE/cloud-pipeline/deploy/jenkins/jenkins-jobs/deploy-dev-az/prepare-assets.sh
