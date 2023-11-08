#!/bin/bash

# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

CLOUD_PIPELINE_BUILD_NUMBER=$(($CLOUD_PIPELINE_BUILD_NUMBER_SEED+$GITHUB_RUN_NUMBER))
CLOUD_PIPELINE_BUILD_RETRY_TIMES=${CLOUD_PIPELINE_BUILD_RETRY_TIMES:-5}

# pre-fetch gradle dependency to get rid of gradle timeouts in the distTar step
function download_gradle_dependencies() {
    ./gradlew buildDependents \
              -x test \
              -x :pipe-cli:buildLinux \
              -x :pipe-cli:buildMac \
              -x :pipe-cli:buildMacPy3 \
              -x :pipe-cli:buildWin \
              -x :client:buildUI \
              -x :cloud-pipeline-webdav-client:buildLinux \
              -x :cloud-pipeline-webdav-client:buildWin \
              -x :fs-browser:build \
              -x :data-sharing-service:client:buildUI \
              -x :data-sharing-service:api:build \
              -x :data-sharing-service:buildAll \
              -x :data-sharing-service:buildFast \
              -x :data-transfer-service:bootJar \
              -x :data-transfer-service:build \
              -x :data-transfer-service:bundleWindows \
              -x :data-transfer-service:bundleLinux \
              -x :workflows:buildGpuStat \
              -Pfast \
              --no-daemon

    if [ "$?" != 0 ]; then
        echo "Problem with resolving gradle dependencies..."
        return 1
    fi
}

_BUILD_EXIT_CODE=1
try_count=0
while [ $_BUILD_EXIT_CODE != 0 ] && [ $try_count -lt "$CLOUD_PIPELINE_BUILD_RETRY_TIMES" ]; do
  echo "Try to to pre-load deps Cloud Pipeline distribution, try $try_count ..."
  download_gradle_dependencies
  _BUILD_EXIT_CODE=$?
  if [ $_BUILD_EXIT_CODE != 0 ]; then
      echo "Failed to pre-load deps for Cloud Pipeline distribution ..."
  else
    echo "Successfully pre-load deps for Cloud Pipeline."
  fi
	try_count=$(( $try_count + 1 ))
done

# Fail script if any command will fail
set -e

python -m pip install mkdocs

API_STATIC_PATH=api/src/main/resources/static
rm -rf ${API_STATIC_PATH}/*
rm -rf build/install/dist/*
mkdir -p ${API_STATIC_PATH}

aws s3 cp --no-progress --recursive s3://cloud-pipeline-oss-builds/temp/$CLOUD_PIPELINE_BUILD_NUMBER/ ${API_STATIC_PATH}/

ls -lh ${API_STATIC_PATH}/pipe \
       ${API_STATIC_PATH}/pipe.tar.gz \
       ${API_STATIC_PATH}/pipe-el6 \
       ${API_STATIC_PATH}/pipe-el6.tar.gz \
       ${API_STATIC_PATH}/pipe-osx \
       ${API_STATIC_PATH}/pipe-osx.tar.gz \
       ${API_STATIC_PATH}/pipe.zip \
       ${API_STATIC_PATH}/client.tar.gz \
       ${API_STATIC_PATH}/cloud-data-linux.tar.gz \
       ${API_STATIC_PATH}/cloud-data-win64.zip \
       ${API_STATIC_PATH}/fsbrowser.tar.gz \
       ${API_STATIC_PATH}/gpustat.tar.gz \
       ${API_STATIC_PATH}/data-sharing-service.jar \
       ${API_STATIC_PATH}/data-transfer-service.jar \
       ${API_STATIC_PATH}/data-transfer-service-windows.zip \
       ${API_STATIC_PATH}/data-transfer-service-linux.zip

tar -xzf ${API_STATIC_PATH}/client.tar.gz -C ${API_STATIC_PATH}
rm -f ${API_STATIC_PATH}/client.tar.gz

mkdir -p data-sharing-service/api/build/libs
mv ${API_STATIC_PATH}/data-sharing-service.jar data-sharing-service/api/build/libs/data-sharing-service.jar

mkdir -p data-transfer-service/build/libs \
         data-transfer-service/build/distributions
mv ${API_STATIC_PATH}/data-transfer-service.jar data-transfer-service/build/libs/data-transfer-service.jar
mv ${API_STATIC_PATH}/data-transfer-service-windows.zip data-transfer-service/build/distributions/data-transfer-service-windows.zip
mv ${API_STATIC_PATH}/data-transfer-service-linux.zip data-transfer-service/build/distributions/data-transfer-service-linux.zip

./gradlew distTar \
          -PbuildNumber=${CLOUD_PIPELINE_BUILD_NUMBER}.${GITHUB_SHA} \
          -Pprofile=release \
          -x test \
          -x :pipe-cli:buildLinux \
          -x :pipe-cli:buildMac \
          -x :pipe-cli:buildMacPy3 \
          -x :pipe-cli:buildWin \
          -x :client:buildUI \
          -x :cloud-pipeline-webdav-client:buildLinux \
          -x :cloud-pipeline-webdav-client:buildWin \
          -x :fs-browser:build \
          -x :data-sharing-service:client:buildUI \
          -x :data-sharing-service:api:build \
          -x :data-sharing-service:buildAll \
          -x :data-sharing-service:buildFast \
          -x :data-transfer-service:bootJar \
          -x :data-transfer-service:build \
          -x :data-transfer-service:bundleWindows \
          -x :data-transfer-service:bundleLinux \
          -x :workflows:buildGpuStat \
          -Pfast \
          --no-daemon

DIST_TGZ_NAME=$(echo build/install/dist/cloud-pipeline*)

ls -lh $DIST_TGZ_NAME

if [ "$GITHUB_REPOSITORY" == "epam/cloud-pipeline" ]; then
    aws s3 cp --no-progress $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/gha/${GITHUB_REF_NAME}/
fi
