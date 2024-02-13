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

set -e

source ~/venv2.7*/bin/activate

which python2
which pip
which pip2
python2 -m pip freeze

./gradlew -PbuildNumber=${APPVEYOR_BUILD_NUMBER}.${APPVEYOR_REPO_COMMIT} \
          -Pprofile=release \
          pipe-cli:buildMac \
          --no-daemon \
          -x :pipe-cli:test

deactivate

source ~/venv3.8*/bin/activate
pip install awscli

cd pipe-cli
DIST_TGZ_NAME=pipe-osx-full.$APPVEYOR_BUILD_NUMBER.tar.gz
tar -zcf $DIST_TGZ_NAME dist
if [ "$APPVEYOR_REPO_NAME" == "epam/cloud-pipeline" ]; then
    if [ "$APPVEYOR_REPO_BRANCH" == "develop" ] || [ "$APPVEYOR_REPO_BRANCH" == "master" ] || [[ "$APPVEYOR_REPO_BRANCH" == "release/"* ]] || [[ "$APPVEYOR_REPO_BRANCH" == "stage/"* ]] ; then
        aws s3 cp $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/temp/
    fi
fi

deactivate
