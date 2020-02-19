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

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_linux_$(date +%s).sh
_BUILD_DOCKER_IMAGE="${_BUILD_DOCKER_IMAGE:-python:2.7-stretch}"

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

mkdir -p $PYINSTALLER_PATH
cd $PYINSTALLER_PATH
git clone --branch resolve_tmpdir https://github.com/mzueva/pyinstaller.git
cd pyinstaller/bootloader/
python2 ./waf all

###
# Setup common dependencies
###
python2 -m pip install -r ${PIPE_CLI_SOURCES_DIR}/requirements.txt

###
# Build pipe fuse
###

if [[ "\$build_os_id" == "centos" ]] && [[ "\$build_os_version_id" == "7" ]]; then
  libfuse_version="2.8.3"
else
  libfuse_version="2.9.2"
fi
cp ${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.\${libfuse_version} ${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.frozen

python2 -m pip install -r ${PIPE_MOUNT_SOURCES_DIR}/requirements.txt
cd $PIPE_MOUNT_SOURCES_DIR && \
python2 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py \
                                --hidden-import=UserList \
                                --hidden-import=UserString \
                                -y \
                                --clean \
                                --runtime-tmpdir $PIPE_CLI_RUNTIME_TMP_DIR \
                                --distpath /tmp/mount/dist \
                                --add-data "${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.frozen:libfuse" \
                                ${PIPE_MOUNT_SOURCES_DIR}/pipe-fuse.py

chmod +x /tmp/mount/dist/pipe-fuse/pipe-fuse

###
# Build ntlm proxy
###
cd /tmp
git clone https://github.com/sidoruka/ntlmaps.git && \
cd ntlmaps && \
git checkout 5f798a88369eddbe732364b98fbd445aacc809d0

python2 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py \
        main.py -y \
        --clean \
        --distpath /tmp/ntlmaps/dist \
        -p /tmp/ntlmaps/lib \
        --add-data ./server.cfg:./ \
        --name ntlmaps

chmod +x /tmp/ntlmaps/dist/ntlmaps/ntlmaps

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

    cd $PIPE_CLI_SOURCES_DIR
    python2 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py \
                                    --add-data "$PIPE_CLI_SOURCES_DIR/res/effective_tld_names.dat.txt:tld/res/" \
                                    --hidden-import=UserList \
                                    --hidden-import=UserString \
                                    --hidden-import=commands \
                                    --hidden-import=ConfigParser \
                                    --hidden-import=UserDict \
                                    --hidden-import=itertools \
                                    --hidden-import=collections \
                                    --hidden-import=future.backports.misc \
                                    --hidden-import=commands \
                                    --hidden-import=base64 \
                                    --hidden-import=__builtin__ \
                                    --hidden-import=math \
                                    --hidden-import=reprlib \
                                    --hidden-import=functools \
                                    --hidden-import=re \
                                    --hidden-import=subprocess \
                                    --additional-hooks-dir="$PIPE_CLI_SOURCES_DIR/hooks" \
                                    -y \
                                    --clean \
                                    --runtime-tmpdir $PIPE_CLI_RUNTIME_TMP_DIR \
                                    --distpath \$distpath \
                                    --add-data /tmp/ntlmaps/dist/ntlmaps:ntlmaps \
                                    --add-data /tmp/mount/dist/pipe-fuse:mount \
                                    ${PIPE_CLI_SOURCES_DIR}/pipe.py \$onefile
}
build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file --onefile
build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder
tar -zcf $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder/pipe.tar.gz \
        -C $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder \
        pipe

EOL

docker pull $_BUILD_DOCKER_IMAGE &> /dev/null
docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:$PIPE_CLI_SOURCES_DIR \
           -v $PIPE_CLI_LINUX_DIST_DIR:$PIPE_CLI_LINUX_DIST_DIR \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           --env PIPE_CLI_SOURCES_DIR=$PIPE_CLI_SOURCES_DIR \
           --env PIPE_MOUNT_SOURCES_DIR=$PIPE_MOUNT_SOURCES_DIR \
           --env PIPE_CLI_LINUX_DIST_DIR=$PIPE_CLI_LINUX_DIST_DIR \
           --env PIPE_CLI_RUNTIME_TMP_DIR="'"$PIPE_CLI_RUNTIME_TMP_DIR"'" \
           --env PYINSTALLER_PATH=$PYINSTALLER_PATH \
           $_BUILD_DOCKER_IMAGE \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
