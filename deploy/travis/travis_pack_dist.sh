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

set -e

API_STATIC_PATH=$(pwd)/api/src/main/resources/static/
mkdir -p $API_STATIC_PATH

# Grab the temporary artifacts into assemble/ dir
# - cloud-pipeline.${VERSION}.tgz
# - client.tgz
# - cli-linux.tgz
# - cli-win.tgz
mkdir assemble
cd assemble
aws s3 cp s3://cloud-pipeline-oss-builds/temp/${TRAVIS_BUILD_NUMBER}/ ./ --recursive

# Untar Web GUI and move it to the pipeline.jar static assets
tar -zxf client.tgz
mv client/build/* $API_STATIC_PATH/

# Untar pipe-cli linux binary and tar.gz. Move them to the pipeline.jar static assets
tar -zxf cli-linux.tgz
mv pipe-cli/dist/PipelineCLI-* $API_STATIC_PATH/PipelineCLI.tar.gz
mv pipe-cli/dist/pipe $API_STATIC_PATH/

# Untar pipe-cli windoes binary and move it to the pipeline.jar static assets
tar -zxf cli-win.tgz
mv pipe-cli/dist/win/pipe.zip $API_STATIC_PATH/

# Create distribution tgz
cd ..
./gradlew distTar   -PbuildNumber=${TRAVIS_BUILD_NUMBER}.${TRAVIS_COMMIT} \
                    -Pprofile=release \
                    -x test \
                    -x client:buildUI \
                    -x pipe-cli:build \
                    -x pipe-cli:buildLinux \
                    -x pipe-cli:buildWin \
                    -Pfast \
                    --no-daemon

if [ "$TRAVIS_REPO_SLUG" == "epam/cloud-pipeline" ]; then
    DIST_TGZ_NAME=$(echo build/install/dist/cloud-pipeline*)

    # Always publish repackaged distribution tgz to S3 to temp directory, even if this is not a "good" branch or it is a PR
    aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/temp/${TRAVIS_BUILD_NUMBER}/

    # Publish repackaged distribution tgz to S3 into builds/ prefix
    # Only if it is one of the allowed branches and it is a push (not PR)
    if ([ "$TRAVIS_BRANCH" == "develop" ] || [ "$TRAVIS_BRANCH" == "master" ] || [[ "$TRAVIS_BRANCH" == "release/"* ]]) && \
        ([ "$TRAVIS_EVENT_TYPE" == "push" ] || [ "$TRAVIS_EVENT_TYPE" == "api" ]); then
            aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/latest/${TRAVIS_BRANCH}/cloud-pipeline.latest.tgz
            aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/${TRAVIS_BRANCH}/
    fi
fi
