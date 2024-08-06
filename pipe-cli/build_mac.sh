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
mkdir -p $PYINSTALLER_PATH
cd $PYINSTALLER_PATH
git clone --branch resolve_tmpdir https://github.com/mzueva/pyinstaller.git
cd pyinstaller/bootloader/
python2 ./waf all
cd -

###
# Setup common dependencies
###
python2 -m pip install macholib
python2 -m pip install -r ${PIPE_CLI_SOURCES_DIR}/requirements.txt

###
# Build pipe fuse
###
python2 -m pip install -r ${PIPE_MOUNT_SOURCES_DIR}/requirements.txt
cd $PIPE_MOUNT_SOURCES_DIR && \
python2 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py \
                                --paths "${PIPE_CLI_SOURCES_DIR}" \
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
                                --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks" \
                                -y \
                                --clean \
                                --runtime-tmpdir $PIPE_CLI_RUNTIME_TMP_DIR \
                                --distpath /tmp/mount/dist \
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
# Build pipe-omics
###
source ~/venv3.10*/bin/activate
python3 -m pip install pyinstaller==6.5.0
python3 -m pip install -r ${PIPE_OMICS_SOURCES_DIR}/requirements.txt
cd $PIPE_OMICS_SOURCES_DIR && \
pyinstaller \
  --paths "${PIPE_OMICS_SOURCES_DIR}" \
  --hidden-import=itertools \
  --hidden-import=collections \
  --hidden-import=base64 \
  --hidden-import=math \
  --hidden-import=reprlib \
  --hidden-import=functools \
  --hidden-import=re \
  --hidden-import=subprocess \
  -y \
  --clean \
  --distpath /tmp/pipe-omics/dist \
  ${PIPE_OMICS_SOURCES_DIR}/pipe-omics.py

chmod +x /tmp/pipe-omics/dist/pipe-omics/pipe-omics
deactivate

###
# Build pipe
###
function build_pipe {
    local distpath="$1"
    local onefile="$2"

    version_file="${PIPE_CLI_SOURCES_DIR}/src/version.py"
    sed -i.bkp '/__bundle_info__/d' $version_file

    bundle_type="one-folder"
    [ "$onefile" ] && bundle_type="one-file"

    build_os_version_id=$(sw_vers -productVersion)
    echo "__bundle_info__ = { 'bundle_type': '$bundle_type', 'build_os_id': 'macos', 'build_os_version_id': '$build_os_version_id' }" >> $version_file

    cd $PIPE_CLI_SOURCES_DIR
    sed -i '/__component_version__/d' $version_file
    local pipe_commit_hash=$(git log --pretty=tformat:"%H" -n1 .)
    echo "__component_version__='$pipe_commit_hash'" >> $version_file

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
                                    --hidden-import=_sysconfigdata \
                                    --additional-hooks-dir="$PIPE_CLI_SOURCES_DIR/hooks" \
                                    -y \
                                    --clean \
                                    --runtime-tmpdir $PIPE_CLI_RUNTIME_TMP_DIR \
                                    --distpath $distpath \
                                    --add-data /tmp/ntlmaps/dist/ntlmaps:ntlmaps \
                                    --add-data /tmp/mount/dist/pipe-fuse:mount \
                                    --add-data /tmp/pipe-omics/dist/pipe-omics:pipe-omics \
                                    ${PIPE_CLI_SOURCES_DIR}/pipe.py $onefile
}
build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file --onefile
mv $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file/pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file/pipe-osx

build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder
tar -zcf $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder/pipe-osx.tar.gz \
        -C $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder \
        pipe
