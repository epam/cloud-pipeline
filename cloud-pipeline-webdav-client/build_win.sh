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

CP_NODE_WINE_DOCKER="lifescience/cloud-pipeline:node-wine"
docker pull $CP_NODE_WINE_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_NODE_WINE_DOCKER image, it will be rebuilt"
    docker build docker/win -t $CP_NODE_WINE_DOCKER
fi

_BUILD_SCRIPT_NAME=/tmp/build_webdav_win32_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<'EOL'

cd /webdav

npm install
npm run package:win32

if [ $? -ne 0 ]; then
    echo "Unable to build UI for Windows"
    exit 1
fi

zip -r -q /webdav/out/webdav-win64.zip /webdav/out/cloud-pipeline-webdav-client-win32-x64/

chmod -R 777 /webdav/out

EOL

docker run -i --rm \
           -v $WEBDAV_SOURCES_DIR:/webdav \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           $CP_NODE_WINE_DOCKER \
           bash $_BUILD_SCRIPT_NAME

if [ $? -ne 0 ]; then
    echo "An error occurred during webdav windows build"
    rm -f $_BUILD_SCRIPT_NAME
    exit 1
fi

rm -f $_BUILD_SCRIPT_NAME
