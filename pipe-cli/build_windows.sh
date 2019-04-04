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

CP_PYINSTALL_WIN64_DOCKER="lifescience/cloud-pipeline:pyinstaller-win64"
docker pull $CP_PYINSTALL_WIN64_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_PYINSTALL_WIN64_DOCKER image, it will be rebuilt"
    docker build docker/win64 -t $CP_PYINSTALL_WIN64_DOCKER
fi

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_win64_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<'EOL'

pip install -r /src/requirements.txt && \
pip install pywin32 && \
pip install --upgrade setuptools && \
cd /src && \
pyinstaller --add-data "/src/res/effective_tld_names.dat.txt;tld/res/" \
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
            --workpath /tmp \
            --distpath /src/dist/win64 \
            /src/pipe.py
EOL

docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:/src \
           -v $PIPE_CLI_WIN_DIST_DIR:/src/dist/win64 \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           $CP_PYINSTALL_WIN64_DOCKER \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
