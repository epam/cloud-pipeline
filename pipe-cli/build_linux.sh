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
_BUILD_DOCKER_IMAGE="python:2.7-stretch"

cat >$_BUILD_SCRIPT_NAME <<EOL

mkdir -p $PYINSTALLER_PATH

cd $PYINSTALLER_PATH

git clone --single-branch --branch resolve_tmpdir https://github.com/mzueva/pyinstaller.git
cd pyinstaller/bootloader/

python2 ./waf all

#echo "Runtime tmpdir: $PIPE_CLI_RUNTIME_TMP_DIR"

python2 -m pip install -r ${PIPE_CLI_SOURCES_DIR}/requirements.txt && \
cd $PIPE_CLI_SOURCES_DIR && \
python2 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py \
                                --add-data "$PIPE_CLI_SOURCES_DIR/res/effective_tld_names.dat.txt:tld/res/" \
                                --onefile \
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
                                --distpath $PIPE_CLI_LINUX_DIST_DIR/dist \
                                ${PIPE_CLI_SOURCES_DIR}/pipe.py
EOL

docker pull $_BUILD_DOCKER_IMAGE &> /dev/null
docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:$PIPE_CLI_SOURCES_DIR \
           -v $PIPE_CLI_LINUX_DIST_DIR:$PIPE_CLI_LINUX_DIST_DIR \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           --env PIPE_CLI_SOURCES_DIR=$PIPE_CLI_SOURCES_DIR \
           --env PIPE_CLI_LINUX_DIST_DIR=$PIPE_CLI_LINUX_DIST_DIR \
           --env PIPE_CLI_RUNTIME_TMP_DIR="'"$PIPE_CLI_RUNTIME_TMP_DIR"'" \
           --env PYINSTALLER_PATH=$PYINSTALLER_PATH \
           $_BUILD_DOCKER_IMAGE \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
