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

CP_PYINSTALL_WIN64_DOCKER="${CP_PYINSTALL_WIN64_DOCKER:-lifescience/cloud-pipeline:pyinstaller-win64-py312}"
docker pull $CP_PYINSTALL_WIN64_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_PYINSTALL_WIN64_DOCKER image, it will be rebuilt"
    docker build docker/win64-py312 -t $CP_PYINSTALL_WIN64_DOCKER
fi

export PIPE_CLI_BUILD_VERSION="${PIPE_CLI_BUILD_VERSION:-0}"

#######################################
# pipe CLI
#######################################
_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_win64_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<'EOL'

cat > /tmp/pipe-win-version-info.txt <<< "$(envsubst < /pipe-cli/res/pipe-win-version-info.txt)" && \
pip install -r /pipe-cli/requirements.txt && \
pip install pywin32==306 && \
pip install 'setuptools==68.2.2' && \
cd /pipe-cli && \
pyinstaller --paths "/pipe-cli" \
            --add-data "/pipe-cli/res/effective_tld_names.dat.txt;tld/res/" \
            --collect-submodules src \
            --collect-submodules google.auth \
            --collect-submodules google.oauth2 \
            --collect-submodules google.cloud \
            --collect-submodules google.resumable_media \
            --collect-submodules azure.core \
            --collect-submodules azure.storage \
            --collect-data botocore \
            --collect-data azure \
            --collect-data tldextract \
            --hidden-import=boto3 \
            --hidden-import=pytz \
            --hidden-import=tkinter \
            --hidden-import=itertools \
            --hidden-import=collections \
            --hidden-import=base64 \
            --hidden-import=math \
            --hidden-import=reprlib \
            --hidden-import=functools \
            --hidden-import=re \
            --hidden-import=subprocess \
            --hidden-import=pypac \
            --hidden-import=treelib \
            --hidden-import=psutil \
            --hidden-import=scp \
            --hidden-import=tzlocal \
            --hidden-import=prettytable \
            --hidden-import=jwt \
            --hidden-import=paramiko \
            --additional-hooks-dir="/pipe-cli/hooks" \
            -y \
            --clean \
            --workpath /tmp \
            --distpath /pipe-cli/dist/win64 \
            pipe.py \
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
