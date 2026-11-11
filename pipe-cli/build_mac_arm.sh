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

###
# Setup Pyinstaller
###
python -m pip install "pyinstaller==6.5.0"

###
# Setup common dependencies
###
python -m pip install macholib==1.16.2


export LDFLAGS="-L$(brew --prefix openssl@1.1)/lib"
export CFLAGS="-I$(brew --prefix openssl@1.1)/include"
export PKG_CONFIG_PATH="$(brew --prefix openssl@1.1)/lib/pkgconfig"
export TMP_MOUNT_BIN=$(pwd)/tmp/mount/dist
python -m pip install -r ${PIPE_CLI_SOURCES_DIR}/requirements.txt


###
# Build pipe fuse
###
python -m pip install -r ${PIPE_MOUNT_SOURCES_DIR}/requirements.txt
cd $PIPE_MOUNT_SOURCES_DIR && \
pyinstaller \
                                --paths "$PIPE_CLI_SOURCES_DIR" \
                                --paths "$PIPE_MOUNT_SOURCES_DIR" \
                                --collect-submodules pipefuse \
                                --collect-submodules google.auth \
                                --collect-submodules google.oauth2 \
                                --collect-submodules google.cloud \
                                --collect-submodules google.resumable_media \
                                --collect-data botocore \
                                --hidden-import=fuse \
                                --hidden-import=cachetools \
                                --hidden-import=intervals \
                                --hidden-import=pygtrie \
                                --hidden-import=itertools \
                                --hidden-import=collections \
                                --hidden-import=base64 \
                                --hidden-import=math \
                                --hidden-import=reprlib \
                                --hidden-import=functools \
                                --hidden-import=re \
                                --hidden-import=subprocess \
                                --hidden-import=_sysconfigdata \
                                --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks" \
                                --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks-py39" \
                                -y \
                                --clean \
                                --distpath $TMP_MOUNT_BIN \
                                ${PIPE_MOUNT_SOURCES_DIR}/pipe-fuse.py

chmod +x $TMP_MOUNT_BIN/pipe-fuse/pipe-fuse

###
# Build pipe
###
function build_pipe {
    local distpath="$1"
    local onefile="$2"

    version_file="${PIPE_CLI_SOURCES_DIR}/src/version.py"
    sed -i'.bkp' '/__bundle_info__/d' $version_file

    bundle_type="one-folder"
    [ "$onefile" ] && bundle_type="one-file"

    build_os_version_id=$(sw_vers -productVersion)
    echo "__bundle_info__ = { 'bundle_type': '$bundle_type', 'build_os_id': 'macos', 'build_os_version_id': '$build_os_version_id' }" >> $version_file

    cd $PIPE_CLI_SOURCES_DIR
    sed -i'.bkp' '/__component_version__/d' $version_file
    local pipe_commit_hash=$(git log --pretty=tformat:"%H" -n1 .)
    echo "__component_version__='$pipe_commit_hash'" >> $version_file

    pyinstaller \
                                    --paths "$PIPE_CLI_SOURCES_DIR" \
                                    --collect-submodules src \
                                    --collect-submodules google.auth \
                                    --collect-submodules google.oauth2 \
                                    --collect-submodules google.cloud \
                                    --collect-submodules google.resumable_media \
                                    --collect-submodules azure.core \
                                    --collect-submodules azure.storage \
                                    --collect-data botocore \
                                    --collect-data azure \
                                    --hidden-import=pygtrie \
                                    --hidden-import=pypac \
                                    --hidden-import=treelib \
                                    --hidden-import=psutil \
                                    --hidden-import=scp \
                                    --hidden-import=tzlocal \
                                    --hidden-import=prettytable \
                                    --hidden-import=jwt \
                                    --hidden-import=paramiko \
                                    --hidden-import=itertools \
                                    --hidden-import=collections \
                                    --hidden-import=base64 \
                                    --hidden-import=math \
                                    --hidden-import=reprlib \
                                    --hidden-import=functools \
                                    --hidden-import=re \
                                    --hidden-import=subprocess \
                                    --hidden-import=_sysconfigdata \
                                    --additional-hooks-dir="${PIPE_CLI_SOURCES_DIR}/hooks" \
                                    --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks" \
                                    --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks-py39" \
                                    -y \
                                    --clean \
                                    --distpath $distpath \
                                    --add-data $TMP_MOUNT_BIN/pipe-fuse:mount \
                                    --add-data "$PIPE_CLI_SOURCES_DIR/res/effective_tld_names.dat.txt:tld/res/" \
                                    ${PIPE_CLI_SOURCES_DIR}/pipe.py $onefile
}

build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder
if [ "$PIPE_CLI_REQUIRES_SIGNING" == "true" ]; then
  bash sign_mac.sh $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder/pipe
fi
tar -zcf $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder/pipe-osx-arm.tar.gz \
        -C $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder \
        pipe

rm -rf $TMP_MOUNT_BIN
