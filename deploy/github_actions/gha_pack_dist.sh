#!/bin/bash

# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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
CLOUD_PIPELINE_BUILD_NUMBER=$(($CLOUD_PIPELINE_BUILD_NUMBER_SEED+$GITHUB_RUN_NUMBER))

# Setup python3: the build runs "python setup.py sdist" for the Python packages and "mkdocs build" for the docs
# in a virtual environment of the Python 3.12 distribution, which the runs install too
CP_PYTHON3_DIST_URL="${CP_PYTHON3_DIST_URL:-https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/python/3/python3.12.tar.gz}"
cd /opt
if ! { wget -q "$CP_PYTHON3_DIST_URL" -O python3.12.tar.gz && \
       tar -zxf python3.12.tar.gz && \
       /opt/python3.12/bin/python3.12 -m venv /opt/py3-build; }; then
    echo "Failed to setup Python 3.12 from $CP_PYTHON3_DIST_URL"
    exit 1
fi
source /opt/py3-build/bin/activate
cd -
# The node-gyp of npm 6 (Node 14), which builds the native npm modules, fails on Python 3.11 and later.
# So npm gets the runner's own python3 (3.10 on ubuntu-22.04), not the Python 3.12 on PATH
if [ -x /usr/bin/python3 ]; then
    export npm_config_python=/usr/bin/python3
fi
#

# Setup node
source ~/.nvm/nvm.sh
nvm install 14.21.3
nvm use 14.21.3
echo "node binary: $(which node)"
#

# pre-fetch gradle dependency to get rid of gradle timeouts in the distTar step
function download_gradle_dependencies() {
    ./gradlew clean buildDependents -Pfast -x test --no-daemon

    if [ "$?" != 0 ]; then
        echo "Problem with resolving gradle dependencies..."
        return 1
    fi
}

function get_pipe_binaries() {
  _suffix="$1"
  _OSX_CLI_TAR_NAME="pipe-osx-full${_suffix}.$CLOUD_PIPELINE_BUILD_NUMBER.tar.gz"
  _OSX_CLI_PATH=$(mktemp -d)
  aws s3 cp --no-progress s3://cloud-pipeline-oss-builds/temp/${_OSX_CLI_TAR_NAME} ${_OSX_CLI_PATH}/
  tar -zxf $_OSX_CLI_PATH/$_OSX_CLI_TAR_NAME -C $_OSX_CLI_PATH

  mv $_OSX_CLI_PATH/dist/dist-folder/pipe-osx*.tar.gz ${API_STATIC_PATH}/
}

# A Python 3.12 virtual environment has no setuptools, which "setup.py sdist" needs
pip install setuptools==81.0.0 mkdocs==1.6.1 || exit 1

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

get_pipe_binaries "-arm"

./gradlew distTar   -PbuildNumber=${CLOUD_PIPELINE_BUILD_NUMBER}.${GITHUB_SHA} \
                    -Pprofile=release \
                    -x test \
                    -Pfast \
                    --no-daemon

if [ "$GITHUB_REPOSITORY" == "epam/cloud-pipeline" ]; then
    DIST_TGZ_NAME=$(echo build/install/dist/cloud-pipeline*)

    # Publish repackaged distribution tgz to S3 into builds/ prefix
    # Only if it is one of the allowed branches and it is a push (not PR)
    if [ "$GITHUB_REF_NAME" == "develop" ] || [ "$GITHUB_REF_NAME" == "master" ] || [[ "$GITHUB_REF_NAME" == "release/"* ]] || [[ "$GITHUB_REF_NAME" == "stage/"* ]] || [[ "$GITHUB_REF_NAME" == "vscode-ext" ]] ; then
            aws s3 cp --no-progress $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/builds/${GITHUB_REF_NAME}/
    fi
fi