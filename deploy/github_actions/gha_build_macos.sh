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

# Setup python2
wget -q "https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/python/2/python-2.7.18-macosx10.9.pkg"
/usr/bin/sudo installer -allowUntrusted -dumplog -package python-2.7.18-macosx10.9.pkg -target /
#

# Setup python3
mkdir -p ~/mamba
cd ~/mamba
curl -Ls https://micro.mamba.pm/api/micromamba/osx-64/latest | tar -xvj bin/micromamba
export PATH=$PATH:~/mamba/bin
export MAMBA_ROOT_PREFIX=~/mamba
eval "$(micromamba shell hook --shell bash)"
micromamba create -n py3
micromamba install -n py3 -y python==3.10
cd -
#

CLOUD_PIPELINE_BUILD_NUMBER=$(($CLOUD_PIPELINE_BUILD_NUMBER_SEED+$GITHUB_RUN_NUMBER))

./gradlew -PbuildNumber=${CLOUD_PIPELINE_BUILD_NUMBER}.${GITHUB_SHA} \
          -Pprofile=release \
          pipe-cli:buildMac \
          --no-daemon \
          -x :pipe-cli:test

cd pipe-cli
DIST_TGZ_NAME=pipe-osx-full.$CLOUD_PIPELINE_BUILD_NUMBER.tar.gz
tar -zcf $DIST_TGZ_NAME dist
if [ "$GITHUB_REPOSITORY" == "epam/cloud-pipeline" ]; then
    if [ "$GITHUB_REF_NAME" == "develop" ] || [ "$GITHUB_REF_NAME" == "master" ] || [[ "$GITHUB_REF_NAME" == "release/"* ]] || [[ "$GITHUB_REF_NAME" == "stage/"* ]] || [[ "$GITHUB_REF_NAME" == "gha-25" ]] ; then
        aws s3 cp --no-progress $DIST_TGZ_NAME s3://cloud-pipeline-oss-builds/temp/
    fi
fi
