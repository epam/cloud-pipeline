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
    ./gradlew clean buildDependents -Pfast -x test --no-daemon

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

_OSX_CLI_TAR_NAME=pipe-osx-full.$CLOUD_PIPELINE_BUILD_NUMBER.tar.gz
_OSX_CLI_PATH=$(mktemp -d)
aws s3 cp s3://cloud-pipeline-oss-builds/temp/${_OSX_CLI_TAR_NAME} ${_OSX_CLI_PATH}/
tar -zxf $_OSX_CLI_PATH/$_OSX_CLI_TAR_NAME -C $_OSX_CLI_PATH

mv $_OSX_CLI_PATH/dist/dist-file/pipe-osx ${API_STATIC_PATH}/pipe-osx
mv $_OSX_CLI_PATH/dist/dist-folder/pipe-osx.tar.gz ${API_STATIC_PATH}/pipe-osx.tar.gz

_BUILD_DOCKER_IMAGE="${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline:python2.7-centos6" ./gradlew -PbuildNumber=${CLOUD_PIPELINE_BUILD_NUMBER}.${GITHUB_SHA} -Pprofile=release pipe-cli:buildLinux --no-daemon -x :pipe-cli:test
mv pipe-cli/dist/dist-file/pipe ${API_STATIC_PATH}/pipe-el6
mv pipe-cli/dist/dist-folder/pipe.tar.gz ${API_STATIC_PATH}/pipe-el6.tar.gz

df -hT "/home/runner/work/cloud-pipeline/cloud-pipeline"

./gradlew clean distTar -PbuildNumber=${CLOUD_PIPELINE_BUILD_NUMBER}.${GITHUB_SHA} \
                        -Pprofile=release \
                        -x test \
                        -Pfast \
                        --no-daemon \
                        --debug \
                        --stacktrace

if [ "$GITHUB_REPOSITORY" == "epam/cloud-pipeline" ]; then
    DIST_TGZ_NAME=$(echo build/install/dist/cloud-pipeline*)

    # Publish repackaged distribution tgz to S3 into builds/ prefix
    aws s3 cp --quiet $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/${GITHUB_REF_NAME}/
fi
