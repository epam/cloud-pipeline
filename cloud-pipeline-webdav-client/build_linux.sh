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

CP_NODE_DOCKER="node:14.9.0-stretch"
docker pull $CP_NODE_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_NODE_DOCKER image"
    exit 1
fi

_BUILD_SCRIPT_NAME=/tmp/build_cloud_data_linux_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<'EOL'

cd /cloud-data

npm install
npm run package:linux

if [ $? -ne 0 ]; then
    echo "Unable to build UI for Linux"
    exit 1
fi

tar -zcf /cloud-data/out/cloud-data-linux.tar.gz \
        -C /cloud-data/out/ \
        cloud-data-linux-x64

chmod -R 777 /cloud-data/out

EOL

docker run -i --rm \
           -v $CP_CLOUD_DATA_SOURCES_DIR:/cloud-data \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           --env CLOUD_DATA_APP_VERSION=$CLOUD_DATA_APP_VERSION \
           $CP_NODE_DOCKER \
           bash $_BUILD_SCRIPT_NAME

if [ $? -ne 0 ]; then
    echo "An error occurred during Cloud Data linux build"
    rm -f $_BUILD_SCRIPT_NAME
    exit 1
fi

rm -f $_BUILD_SCRIPT_NAME
