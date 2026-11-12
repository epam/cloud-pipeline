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

_BUILD_SCRIPT_NAME=/tmp/build_mnt_pytinstaller_linux_$(date +%s).sh
_BUILD_DOCKER_IMAGE="${_BUILD_DOCKER_IMAGE:-lifescience/cloud-pipeline:pipe-cli-centos7-py312}"
_PIPE_CLI_SOURCES_DIR="$(cd "$PIPE_MOUNT_SOURCES_DIR/.." && pwd)"

# The one-file binary unpacks itself into this folder at run time, see build_linux.sh
_PIPE_CLI_RUNTIME_TMP_DIR='~/.pipe/tmp'
if [ "$PIPE_CLI_RUNTIME_TMP_DIR" ]; then
    _PIPE_CLI_RUNTIME_TMP_DIR="${PIPE_CLI_RUNTIME_TMP_DIR#\\}"
fi

cat >$_BUILD_SCRIPT_NAME <<EOL

###
# Setup common dependencies: pipe-fuse imports the pipe CLI modules (src) as well
###
pip install -r ${_PIPE_CLI_SOURCES_DIR}/requirements.txt -c ${_PIPE_CLI_SOURCES_DIR}/constraints.txt || exit 1
pip install -r ${PIPE_MOUNT_SOURCES_DIR}/requirements.txt -c ${_PIPE_CLI_SOURCES_DIR}/constraints.txt || exit 1
pip install 'setuptools==68.2.2' || exit 1
bash ${_PIPE_CLI_SOURCES_DIR}/pyinstaller/build_bootloader.sh || exit 1

###
# Build pipe-fuse, as build_linux.sh does, but as a one-file binary
###
cp ${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.2.9.2 ${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.frozen
cd $PIPE_MOUNT_SOURCES_DIR && \
pyinstaller \
                                --paths "${PIPE_MOUNT_SOURCES_DIR}" \
                                --paths "${_PIPE_CLI_SOURCES_DIR}" \
                                --collect-submodules pipefuse \
                                --collect-submodules src \
                                --collect-submodules google.auth \
                                --collect-submodules google.oauth2 \
                                --collect-submodules google.cloud \
                                --collect-submodules google.resumable_media \
                                --collect-data botocore \
                                --hidden-import=fuse \
                                --hidden-import=cachetools \
                                --hidden-import=intervals \
                                --hidden-import=pygtrie \
                                --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks" \
                                -y \
                                --clean \
                                --runtime-tmpdir '$_PIPE_CLI_RUNTIME_TMP_DIR' \
                                --distpath $PIPE_CLI_LINUX_DIST_DIR/dist \
                                --add-data "${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.frozen:libfuse" \
                                ${PIPE_MOUNT_SOURCES_DIR}/pipe-fuse.py \
                                --onefile
EOL

docker pull $_BUILD_DOCKER_IMAGE &> /dev/null
docker run -i --rm \
           -v $_PIPE_CLI_SOURCES_DIR:$_PIPE_CLI_SOURCES_DIR \
           -v $PIPE_CLI_LINUX_DIST_DIR:$PIPE_CLI_LINUX_DIST_DIR \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           --env PIPE_MOUNT_SOURCES_DIR=$PIPE_MOUNT_SOURCES_DIR \
           --env PIPE_CLI_LINUX_DIST_DIR=$PIPE_CLI_LINUX_DIST_DIR \
           $_BUILD_DOCKER_IMAGE \
           bash $_BUILD_SCRIPT_NAME
_BUILD_EXIT_CODE=$?

rm -f $_BUILD_SCRIPT_NAME

if [ "$_BUILD_EXIT_CODE" != 0 ]; then
    echo "[ERROR] The pipe-fuse build in $_BUILD_DOCKER_IMAGE failed"
    exit "$_BUILD_EXIT_CODE"
fi
