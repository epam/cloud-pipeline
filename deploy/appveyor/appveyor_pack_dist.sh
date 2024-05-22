#!/bin/bash

# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

CLOUD_PIPELINE_BUILD_RETRY_TIMES=${CLOUD_PIPELINE_BUILD_RETRY_TIMES:-5}

# pre-fetch gradle dependency to get rid of gradle timeouts in the distTar step
function download_gradle_dependencies() {
    ./gradlew clean buildDependents -Pfast -x test --no-daemon

    if [ "$?" != 0 ]; then
        echo "Problem with resolving gradle dependencies..."
        return 1
    fi
}

source ~/venv2.7.18/bin/activate
pip install PyYAML==3.12
pip install mkdocs==1.0.4

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

set -e

API_STATIC_PATH=api/src/main/resources/static
rm -rf ${API_STATIC_PATH}/*
rm -rf build/install/dist/*
mkdir -p ${API_STATIC_PATH}

_OSX_CLI_TAR_NAME=pipe-osx-full.$APPVEYOR_BUILD_NUMBER.tar.gz
_OSX_CLI_PATH=$(mktemp -d)
aws s3 cp s3://cloud-pipeline-oss-builds/temp/${_OSX_CLI_TAR_NAME} ${_OSX_CLI_PATH}/
tar -zxf $_OSX_CLI_PATH/$_OSX_CLI_TAR_NAME -C $_OSX_CLI_PATH

mv $_OSX_CLI_PATH/dist/dist-file/pipe-osx ${API_STATIC_PATH}/pipe-osx
mv $_OSX_CLI_PATH/dist/dist-folder/pipe-osx.tar.gz ${API_STATIC_PATH}/pipe-osx.tar.gz

CLI_TAR_NAME=pipe.$APPVEYOR_BUILD_NUMBER.tar.gz
CLI_PATH=$(mktemp -d)
aws s3 cp s3://cloud-pipeline-oss-builds/temp/${CLI_TAR_NAME} ${CLI_PATH}/
tar -zxf $CLI_PATH/$CLI_TAR_NAME -C $CLI_PATH

mv $CLI_PATH/dist/dist-file/pipe ${API_STATIC_PATH}/pipe
mv $CLI_PATH/dist/dist-folder/pipe.tar.gz ${API_STATIC_PATH}/pipe.tar.gz
mv $CLI_PATH/dist/dist-file/pipe-el6 ${API_STATIC_PATH}/pipe-el6
mv $CLI_PATH/dist/dist-folder/pipe-el6.tar.gz ${API_STATIC_PATH}/pipe-el6.tar.gz
mv $CLI_PATH/dist/win/pipe.zip ${API_STATIC_PATH}/pipe.zip


./gradlew distTar   -PbuildNumber=${APPVEYOR_BUILD_NUMBER}.${APPVEYOR_REPO_COMMIT} \
                    -Pprofile=release \
                    -x test pipe-cli:build pipe-cli:buildLinux pipe-cli:buildWin \
                    -Pfast \
                    --no-daemon

deactivate

source ~/venv3.8.17/bin/activate
pip install awscli

if [ "$APPVEYOR_REPO_NAME" == "epam/cloud-pipeline" ]; then
    DIST_TGZ_NAME=$(echo build/install/dist/cloud-pipeline*)

    # Publish repackaged distribution tgz to S3 into builds/ prefix
    # Only if it is one of the allowed branches and it is a push (not PR)
    if [ "$APPVEYOR_REPO_BRANCH" == "develop" ] || [ "$APPVEYOR_REPO_BRANCH" == "master" ] || [[ "$APPVEYOR_REPO_BRANCH" == "release/"* ]] || [[ "$APPVEYOR_REPO_BRANCH" == "stage/"* ]]; then
            aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/${APPVEYOR_REPO_BRANCH}/
    fi
fi

deactivate
