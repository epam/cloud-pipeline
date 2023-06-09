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

function build_cloud_pipeline() {
    # pre-fetch gradle dependency to get rid of gradle timeouts in the distTar step
    ./gradlew clean buildDependents -Pfast -x test --no-daemon

    if [ "$?" != 0 ]; then
        echo "Problem with resolving gradle dependencies..."
        return 1
    fi

    API_STATIC_PATH=api/src/main/resources/static
    rm -rf ${API_STATIC_PATH}/*
    rm -rf build/install/dist/*
    mkdir -p ${API_STATIC_PATH}

    _OSX_CLI_TAR_NAME=pipe-osx-full.$CLOUD_PIPELINE_BUILD_NUMBER.tar.gz
    _OSX_CLI_PATH=$(mktemp -d)

    curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 - \
    && python2 -m pip install mkdocs

    aws s3 cp s3://cloud-pipeline-oss-builds/temp/${_OSX_CLI_TAR_NAME} ${_OSX_CLI_PATH}/ \
    && tar -zxf $_OSX_CLI_PATH/$_OSX_CLI_TAR_NAME -C $_OSX_CLI_PATH \
    && mv $_OSX_CLI_PATH/dist/dist-file/pipe-osx ${API_STATIC_PATH}/pipe-osx \
    && mv $_OSX_CLI_PATH/dist/dist-folder/pipe-osx.tar.gz ${API_STATIC_PATH}/pipe-osx.tar.gz \
    && _BUILD_DOCKER_IMAGE="${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline:python2.7-centos6" ./gradlew -PbuildNumber=${CLOUD_PIPELINE_BUILD_NUMBER}.${GITHUB_SHA} -Pprofile=release pipe-cli:buildLinux --no-daemon -x :pipe-cli:test \
    && mv pipe-cli/dist/dist-file/pipe ${API_STATIC_PATH}/pipe-el6 \
    && mv pipe-cli/dist/dist-folder/pipe.tar.gz ${API_STATIC_PATH}/pipe-el6.tar.gz

    if [ "$?" != 0 ]; then
        echo "Problem with building pipe-cli dependencies..."
        return 1
    fi

    ./gradlew clean distTar -PbuildNumber=${CLOUD_PIPELINE_BUILD_NUMBER}.${GITHUB_SHA} \
                            -Pprofile=release \
                            -x test \
                            -Pfast \
                            --no-daemon

    if [ "$?" != 0 ]; then
        echo "Problem with creating Cloud Pipeline dist..."
        return 1
    fi

    if [ "$GITHUB_REPOSITORY" == "epam/cloud-pipeline" ]; then
        DIST_TGZ_NAME=$(echo build/install/dist/cloud-pipeline*)

        # Publish repackaged distribution tgz to S3 into builds/ prefix
        # Only if it is one of the allowed branches and it is a push (not PR)
        if [ "$GITHUB_REF_NAME" == "develop" ] || [ "$GITHUB_REF_NAME" == "master" ] || [[ "$GITHUB_REF_NAME" == "release/"* ]] || [[ "$GITHUB_REF_NAME" == "stage/"* ]]; then
                aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/${GITHUB_REF_NAME}/
        fi
    fi
}

_BUILD_EXIT_CODE=1
try_count=0
while [ $_BUILD_EXIT_CODE != 0 ] && [ $try_count -lt "$CLOUD_PIPELINE_BUILD_RETRY_TIMES" ]; do
  echo "Try to build Cloud Pipeline distribution, try $try_count ..."
  build_cloud_pipeline
  _BUILD_EXIT_CODE=$?
  if [ $_BUILD_EXIT_CODE != 0 ]; then
      echo "Failed to build Cloud Pipeline distribution ..."
  else
    echo "Successfully built Cloud Pipeline distribution, finishing the job."
    exit 0
  fi
	try_count=$(( $try_count + 1 ))
done

echo "Job wasn't able to build Cloud Pipeline distribution after $try_count retries, failing the Job ..."
exit 62




