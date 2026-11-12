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

# The bundle is built in lifescience/cloud-pipeline:pipe-cli-centos7-py312 (Docker Hub): CentOS 7 (glibc 2.17) with
# Python 3.12.16 and PyInstaller 6.5.0. PyInstaller bundles every library but glibc, so the bundle runs on glibc 2.17
# or later
_BUILD_DOCKER_IMAGE="${_BUILD_DOCKER_IMAGE:-lifescience/cloud-pipeline:pipe-cli-centos7-py312}"
_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_fsbrowser_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<EOL
set -e

###
# Setup dependencies
###
python3 -m pip install -r ${FSBROWSER_SOURCES_DIR}/requirements.txt

###
# Setup fsbrowser
###
# PyInstaller 6.5.0 of the builder image. Its build files stay in the container. pygit2 loads the cffi backend from its C
# extension, so PyInstaller does not find it by itself
pyinstaller -y --clean \
  --distpath ${FSBROWSER_DIST_PATH} \
  --workpath /tmp/fsbrowser-build \
  --specpath /tmp/fsbrowser-build \
  --runtime-tmpdir ${FSBROWSER_RUNTIME_TMP_DIR} \
  --add-data ${FSBROWSER_SOURCES_DIR}/fsbrowser:fsbrowser \
  --collect-data flasgger \
  --hidden-import=pygit2 \
  --hidden-import=_cffi_backend \
  --hidden-import=requests ${FSBROWSER_SOURCES_DIR}/fsbrowser/fsbrowser-cli.py
tar -zcf ${FSBROWSER_DIST_PATH}/fsbrowser.tar.gz -C ${FSBROWSER_DIST_PATH} fsbrowser-cli
EOL

docker pull $_BUILD_DOCKER_IMAGE &> /dev/null
docker run -i --rm \
           --env FSBROWSER_SOURCES_DIR=$FSBROWSER_SOURCES_DIR \
           --env FSBROWSER_DIST_PATH=$FSBROWSER_DIST_PATH \
           --env FSBROWSER_RUNTIME_TMP_DIR="'"$FSBROWSER_RUNTIME_TMP_DIR"'" \
           -v $FSBROWSER_SOURCES_DIR:$FSBROWSER_SOURCES_DIR \
           -v $FSBROWSER_DIST_PATH:$FSBROWSER_DIST_PATH \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           $_BUILD_DOCKER_IMAGE \
           bash $_BUILD_SCRIPT_NAME
_BUILD_EXIT_CODE=$?

rm -f $_BUILD_SCRIPT_NAME
exit $_BUILD_EXIT_CODE
