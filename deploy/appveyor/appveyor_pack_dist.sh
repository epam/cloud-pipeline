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

function get_pipe_binaries() {
  _suffix="$1"
  _OSX_CLI_TAR_NAME="pipe-osx-full${_suffix}.$APPVEYOR_BUILD_NUMBER.tar.gz"
  _OSX_CLI_PATH=$(mktemp -d)
  aws s3 cp s3://cloud-pipeline-oss-builds/temp/${_OSX_CLI_TAR_NAME} ${_OSX_CLI_PATH}/
  tar -zxf $_OSX_CLI_PATH/$_OSX_CLI_TAR_NAME -C $_OSX_CLI_PATH

  mv $_OSX_CLI_PATH/dist/dist-file/pipe-osx* ${API_STATIC_PATH}/
  mv $_OSX_CLI_PATH/dist/dist-folder/pipe-osx*.tar.gz ${API_STATIC_PATH}/
}

# Setup python3: the build runs "python setup.py sdist" for the Python packages and "mkdocs build" for the docs
# in a virtual environment of the Python 3.12 distribution, which the runs install too
CP_PYTHON3_DIST_URL="${CP_PYTHON3_DIST_URL:-https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/python/3/python3.12.tar.gz}"
_PYTHON3_HOME=$(mktemp -d)
if ! { wget -q "$CP_PYTHON3_DIST_URL" -O "$_PYTHON3_HOME/python3.12.tar.gz" && \
       tar -zxf "$_PYTHON3_HOME/python3.12.tar.gz" -C "$_PYTHON3_HOME" && \
       "$_PYTHON3_HOME/python3.12/bin/python3.12" -m venv "$_PYTHON3_HOME/venv"; }; then
    echo "Failed to setup Python 3.12 from $CP_PYTHON3_DIST_URL"
    exit 1
fi
source "$_PYTHON3_HOME/venv/bin/activate"
# The node-gyp of npm 6 (Node 14), which builds the native npm modules, fails on Python 3.11 and later.
# So npm gets the system's own python3, not the Python 3.12 on PATH
if [ -x /usr/bin/python3 ]; then
    export npm_config_python=/usr/bin/python3
fi
# A Python 3.12 virtual environment has no setuptools, which "setup.py sdist" needs
pip install setuptools==81.0.0 mkdocs==1.6.1 || exit 1

# Authenticate to have an increased pull limit
# https://www.docker.com/increase-rate-limit
docker login -u "$DOCKER_USER" -p "$DOCKER_TOKEN"

API_STATIC_PATH=api/src/main/resources/static
rm -rf ${API_STATIC_PATH}/*
rm -rf build/install/dist/*
mkdir -p ${API_STATIC_PATH}

get_pipe_binaries "-arm"

./gradlew distTar   -PbuildNumber=${APPVEYOR_BUILD_NUMBER}.${APPVEYOR_REPO_COMMIT} \
                    -Pprofile=release \
                    -x test \
                    -Pfast \
                    --no-daemon

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
