#!/bin/bash

# Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

set -e

CLOUD_PIPELINE_BUILD_NUMBER=$(($CLOUD_PIPELINE_BUILD_NUMBER_SEED+$GITHUB_RUN_NUMBER))

./gradlew -PbuildNumber=${CLOUD_PIPELINE_BUILD_NUMBER}.${GITHUB_SHA} \
          -Pprofile=release \
          pipe-cli:buildMacArm \
          --no-daemon \
          -x :pipe-cli:test

pip install awscli

cd pipe-cli
DIST_TGZ_NAME="pipe-osx-full-arm.${CLOUD_PIPELINE_BUILD_NUMBER}.tar.gz"
tar -zcf $DIST_TGZ_NAME dist
if [ "$GITHUB_REPOSITORY" == "epam/cloud-pipeline" ]; then
    if [ "$GITHUB_REF_NAME" == "develop" ] || [ "$GITHUB_REF_NAME" == "issue-4029_cli-retry-batch" ] || [ "$GITHUB_REF_NAME" == "master" ] || [[ "$GITHUB_REF_NAME" == "release/"* ]] || [[ "$GITHUB_REF_NAME" == "stage/"* ]] ; then
        aws s3 cp --no-progress $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/temp/
    fi
fi
