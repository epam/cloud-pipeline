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
# - cli-linux-el6.tgz
# - cli-win.tgz
# - fsbrowser.tgz
mkdir assemble
cd assemble
aws s3 cp s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/ ./ --recursive

# Untar Web GUI and move it to the pipeline.jar static assets
tar -zxf client.tgz
mv client/build/* $API_STATIC_PATH/

# Untar pipe-cli linux binary and tar.gz. Move them to the pipeline.jar static assets
tar -zxf cli-linux.tgz
mv pipe-cli/dist/PipelineCLI-* $API_STATIC_PATH/PipelineCLI.tar.gz
mv pipe-cli/dist/dist-file/pipe $API_STATIC_PATH/
mv pipe-cli/dist/dist-folder/pipe.tar.gz $API_STATIC_PATH/

# Untar pipe-cli linux el6 binary. Move them to the pipeline.jar static assets
# Clean the previous cli version (non-el6)
rm -rf pipe-cli/dist
tar -zxf cli-linux-el6.tgz
mv pipe-cli/dist/dist-file/pipe $API_STATIC_PATH/pipe-el6
mv pipe-cli/dist/dist-folder/pipe.tar.gz $API_STATIC_PATH/pipe-el6.tar.gz

# Untar pipe-cli windows binary and move it to the pipeline.jar static assets
tar -zxf cli-win.tgz
mv pipe-cli/dist/win/pipe.zip $API_STATIC_PATH/

# Untar fsbrowser and move it to the pipeline.jar static assets
mv fsbrowser-* $API_STATIC_PATH/fsbrowser.tar.gz

# Move cloud-data client distributions to the pipeline.jar static assets
mv cloud-data-linux.tar.gz $API_STATIC_PATH/cloud-data-linux.tar.gz
mv cloud-data-win64.zip $API_STATIC_PATH/cloud-data-win64.zip

# Create distribution tgz
cd ..
./gradlew distTar   -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} \
                    -Pprofile=release \
                    -x test \
                    -x client:buildUI \
                    -x pipe-cli:build \
                    -x pipe-cli:buildLinux \
                    -x pipe-cli:buildWin \
                    -x fs-browser:build \
                    -x cloud-pipeline-webdav-client:buildLinux \
                    -x cloud-pipeline-webdav-client:buildWin \
                    -Pfast \
                    --no-daemon

DIST_TGZ_NAME=$(echo build/install/dist/cloud-pipeline*)

# Always publish repackaged distribution tgz to S3 to temp directory, even if this is not a "good" branch or it is a PR
aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/

# Publish repackaged distribution tgz to S3 into builds/ prefix
if [ "$CP_GIT_REF" == "develop" ] || [ "$CP_GIT_REF" == "master" ] || [[ "$CP_GIT_REF" == "release/"* ]] || [[ "$CP_GIT_REF" == "stage/"* ]]; then
        aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/latest/${CP_GIT_REF}/cloud-pipeline.latest.tgz
        aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/${CP_GIT_REF}/
fi
