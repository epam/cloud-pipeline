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
python -m pip install pyinstaller==5.13.2

###
# Setup common dependencies
###
python -m pip install macholib==1.16.2
python -m pip install -r ${PIPE_CLI_SOURCES_DIR}/requirements.txt

###
# Build pipe fuse
###
python -m pip install -r ${PIPE_MOUNT_SOURCES_DIR}/requirements.txt
cd $PIPE_MOUNT_SOURCES_DIR && \
pyinstaller \
                                --paths "$PIPE_CLI_SOURCES_DIR" \
                                --paths "$PIPE_MOUNT_SOURCES_DIR" \
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
                                --hidden-import=_sysconfigdata \
                                --additional-hooks-dir="$PIPE_MOUNT_SOURCES_DIR/hooks" \
                                -y \
                                --clean \
                                --distpath /tmp/mount/dist \
                                ${PIPE_MOUNT_SOURCES_DIR}/pipe-fuse.py

chmod +x /tmp/mount/dist/pipe-fuse/pipe-fuse

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
                                    --hidden-import=_sysconfigdata \
                                    --additional-hooks-dir="$PIPE_CLI_SOURCES_DIR/hooks" \
                                    -y \
                                    --clean \
                                    --distpath $distpath \
                                    --add-data /tmp/mount/dist/pipe-fuse:mount \
                                    --add-data "$PIPE_CLI_SOURCES_DIR/res/effective_tld_names.dat.txt:tld/res/" \
                                    ${PIPE_CLI_SOURCES_DIR}/pipe.py $onefile
}
build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file --onefile
mv $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file/pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file/pipe-osx

build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder
tar -zcf $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder/pipe-osx.tar.gz \
        -C $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder \
        pipe
