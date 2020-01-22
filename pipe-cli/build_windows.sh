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

#######################################
# Step 1: Build NTLM APS with Python 2
#######################################

CP_PYINSTALL_WIN32_PY2_DOCKER="lifescience/cloud-pipeline:pyinstaller-win32-py2"
docker pull $CP_PYINSTALL_WIN32_PY2_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_PYINSTALL_WIN32_PY2_DOCKER image, it will be rebuilt"
    docker build docker/win32 -t $CP_PYINSTALL_WIN32_PY2_DOCKER
fi

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_win32_py2_$(date +%s).sh
export PIPE_CLI_BUILD_VERSION="${PIPE_CLI_BUILD_VERSION:-0}"

cat >$_BUILD_SCRIPT_NAME <<'EOL'

cat > /tmp/ntlmp-win-version-info.txt <<< "$(envsubst < /pipe-cli/res/ntlmp-win-version-info.txt)"

pip install --upgrade 'setuptools<=45.1.0' && \
pip install -r /pipe-cli/requirements.txt

cd /tmp
git clone https://github.com/sidoruka/ntlmaps.git && \
cd ntlmaps && \
git checkout 5f798a88369eddbe732364b98fbd445aacc809d0

pyinstaller main.py -y \
        --clean \
        --distpath /tmp/ntlmaps/dist \
        -p /tmp/ntlmaps/lib \
        --add-data "./server.cfg;./" \
        --name ntlmaps \
        --version-file /tmp/ntlmp-win-version-info.txt

cp -r /tmp/ntlmaps/dist/ntlmaps /pipe-cli/
EOL

docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:/pipe-cli \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           -e PIPE_CLI_MAJOR_VERSION=$PIPE_CLI_MAJOR_VERSION \
           -e PIPE_CLI_MINOR_VERSION=$PIPE_CLI_MINOR_VERSION \
           -e PIPE_CLI_PATCH_VERSION=$PIPE_CLI_PATCH_VERSION \
           -e PIPE_CLI_BUILD_VERSION=$(cut -d. -f1 <<< "$PIPE_CLI_BUILD_VERSION") \
           $CP_PYINSTALL_WIN32_PY2_DOCKER \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME


#######################################
# Step 2: pipe CLI
#######################################

CP_PYINSTALL_WIN64_DOCKER="lifescience/cloud-pipeline:pyinstaller-win64"
docker pull $CP_PYINSTALL_WIN64_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_PYINSTALL_WIN64_DOCKER image, it will be rebuilt"
    docker build docker/win64 -t $CP_PYINSTALL_WIN64_DOCKER
fi

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_win64_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<'EOL'

cat > /tmp/pipe-win-version-info.txt <<< "$(envsubst < /pipe-cli/res/pipe-win-version-info.txt)" && \
pip install --upgrade 'setuptools<=45.1.0' && \
pip install -r /pipe-cli/requirements.txt && \
pip install pywin32 && \
cd /pipe-cli && \
pyinstaller --add-data "/pipe-cli/res/effective_tld_names.dat.txt;tld/res/" \
            --hidden-import=boto3 \
            --hidden-import=pytz \
            --hidden-import=tkinter \
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
            --hidden-import=pkg_resources.py2_warn \
            --additional-hooks-dir="/pipe-cli/hooks" \
            -y \
            --clean \
            --workpath /tmp \
            --distpath /pipe-cli/dist/win64 \
            pipe.py \
            --add-data "/pipe-cli/ntlmaps;ntlmaps" \
            --version-file /tmp/pipe-win-version-info.txt \
            --icon /pipe-cli/res/cloud-pipeline.ico && \
cd /pipe-cli/dist/win64 && \
zip -r -q pipe.zip pipe
EOL

docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:/pipe-cli \
           -v $PIPE_CLI_WIN_DIST_DIR:/pipe-cli/dist/win64 \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           -e PIPE_CLI_MAJOR_VERSION=$PIPE_CLI_MAJOR_VERSION \
           -e PIPE_CLI_MINOR_VERSION=$PIPE_CLI_MINOR_VERSION \
           -e PIPE_CLI_PATCH_VERSION=$PIPE_CLI_PATCH_VERSION \
           -e PIPE_CLI_BUILD_VERSION=$(cut -d. -f1 <<< "$PIPE_CLI_BUILD_VERSION") \
           $CP_PYINSTALL_WIN64_DOCKER \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
