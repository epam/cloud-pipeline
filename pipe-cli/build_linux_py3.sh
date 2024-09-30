#!/bin/bash

# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_linux_$(date +%s).sh
_BUILD_DOCKER_IMAGE="${_BUILD_DOCKER_IMAGE:-python:3.6-stretch}"

cat >$_BUILD_SCRIPT_NAME <<EOL

###
# Resolve bundle os
###

build_os_id=''
build_os_version_id=''
if [ -f "/etc/os-release" ]; then
    source /etc/os-release
    build_os_id="\${ID}"
    build_os_version_id="\${VERSION_ID}"
elif [ -f "/etc/centos-release" ]; then
    build_os_id="centos"
    build_os_version_id=\$(cat /etc/centos-release | tr -dc '0-9.'|cut -d \. -f1)
fi

###
# Setup Pyinstaller
###

pip install pyinstaller==3.4

###
# Setup common dependencies
###

pip install -r ${PIPE_CLI_SOURCES_DIR}/requirements.txt

###
# Build pipe fuse
###

if [[ "\$build_os_id" == "centos" ]] && [[ "\$build_os_version_id" == "6" ]]; then
  libfuse_version="2.8.3"
else
  libfuse_version="2.9.2"
fi
cp ${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.\${libfuse_version} ${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.frozen

pip install -r ${PIPE_MOUNT_SOURCES_DIR}/requirements.txt
cd $PIPE_MOUNT_SOURCES_DIR && \
pyinstaller \
    --paths "${PIPE_CLI_SOURCES_DIR}" \
    --hidden-import=boto3 \
    --hidden-import=configparser \
    --hidden-import=itertools \
    --hidden-import=collections \
    --hidden-import=future.backports.misc \
    --hidden-import=base64 \
    --hidden-import=__builtin__ \
    --hidden-import=math \
    --hidden-import=reprlib \
    --hidden-import=functools \
    --hidden-import=re \
    --hidden-import=subprocess \
    --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks" \
    --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks-py39" \
    -y \
    --clean \
    --distpath /tmp/mount/dist \
    --add-data "${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.frozen:libfuse" \
    ${PIPE_MOUNT_SOURCES_DIR}/pipe-fuse.py

chmod +x /tmp/mount/dist/pipe-fuse/pipe-fuse

###
# Build pipe
###

function build_pipe {
    local distpath="\$1"
    local onefile="\$2"

    version_file="${PIPE_CLI_SOURCES_DIR}/src/version.py"
    sed -i '/__bundle_info__/d' \$version_file

    bundle_type="one-folder"
    [ "\$onefile" ] && bundle_type="one-file"

    echo "__bundle_info__ = { 'bundle_type': '\$bundle_type', 'build_os_id': '\$build_os_id', 'build_os_version_id': '\$build_os_version_id' }" >> \$version_file

    sed -i '/__component_version__/d' \$version_file
    echo "__component_version__='\${PIPE_COMMIT_HASH}'" >> \$version_file

    cd $PIPE_CLI_SOURCES_DIR
    pyinstaller
        --paths $PIPE_CLI_SOURCES_DIR \
        --add-data "$PIPE_CLI_SOURCES_DIR/res/effective_tld_names.dat.txt:tld/res/" \
        --hidden-import=boto3 \
        --hidden-import=pytz \
        --hidden-import=tkinter \
        --hidden-import=configparser \
        --hidden-import=UserDict \
        --hidden-import=itertools \
        --hidden-import=collections \
        --hidden-import=future.backports.misc \
        --hidden-import=base64 \
        --hidden-import=__builtin__ \
        --hidden-import=math \
        --hidden-import=reprlib \
        --hidden-import=functools \
        --hidden-import=re \
        --hidden-import=subprocess \
        --additional-hooks-dir="$PIPE_CLI_SOURCES_DIR/hooks" \
        --additional-hooks-dir="$PIPE_CLI_SOURCES_DIR/hooks-py36" \
        --runtime-hook="${PIPE_CLI_SOURCES_DIR}/hooks-py36/hook-env.py" \
        -y \
        --clean \
        --distpath \$distpath \
        --add-data /tmp/mount/dist/pipe-fuse:mount \
        ${PIPE_CLI_SOURCES_DIR}/pipe.py \$onefile
}

build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file --onefile
build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder
tar -zcf $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder/pipe.tar.gz \
        -C $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder \
        pipe

EOL

cd $PIPE_CLI_SOURCES_DIR
PIPE_COMMIT_HASH=$(git log --pretty=tformat:"%H" -n1 .)
cd -

docker pull $_BUILD_DOCKER_IMAGE &> /dev/null
docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:$PIPE_CLI_SOURCES_DIR \
           -v $PIPE_CLI_LINUX_DIST_DIR:$PIPE_CLI_LINUX_DIST_DIR \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           --env PIPE_CLI_SOURCES_DIR=$PIPE_CLI_SOURCES_DIR \
           --env PIPE_MOUNT_SOURCES_DIR=$PIPE_MOUNT_SOURCES_DIR \
           --env PIPE_CLI_LINUX_DIST_DIR=$PIPE_CLI_LINUX_DIST_DIR \
           --env PYINSTALLER_PATH=$PYINSTALLER_PATH \
           --env PIPE_COMMIT_HASH=$PIPE_COMMIT_HASH \
           $_BUILD_DOCKER_IMAGE \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
