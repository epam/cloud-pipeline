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

CP_NODE_DOCKER="node:14.17.5-stretch"
docker pull $CP_NODE_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_NODE_DOCKER image"
    exit 1
fi

# Write inner script under project dir so we only mount the project (no file bind-mount).
_INNER_SCRIPT="$CP_CLOUD_DATA_SOURCES_DIR/.build_linux_inner.sh"

cat >"$_INNER_SCRIPT" <<'EOL'

cd /cloud-data

version_file="scripts/PublishVersionPlugin.js"
cp $version_file /tmp/version.bkp
chmod -R a+w scripts 2>/dev/null || true
sed -i "s/1111111111111111111111111111111111111111/$CLOUD_DATA_COMMIT_HASH/g" $version_file

npm install
npm run package:linux

if [ $? -ne 0 ]; then
    echo "Unable to build UI for Linux"
    cp /tmp/version.bkp \$version_file
    exit 1
fi

tar -zcf /cloud-data/out/cloud-data-linux.tar.gz \
        -C /cloud-data/out/ \
        cloud-data-linux-x64

chmod -R 777 /cloud-data/out
cp /tmp/version.bkp $version_file

EOL

cd $CP_CLOUD_DATA_SOURCES_DIR
CLOUD_DATA_COMMIT_HASH=$(git log --pretty=tformat:"%H" -n1 .)
cd -

docker run -i --rm \
           -v "$CP_CLOUD_DATA_SOURCES_DIR:/cloud-data" \
           --env CLOUD_DATA_APP_VERSION=$CLOUD_DATA_APP_VERSION \
           --env CLOUD_DATA_COMMIT_HASH=$CLOUD_DATA_COMMIT_HASH \
           $CP_NODE_DOCKER \
           bash /cloud-data/.build_linux_inner.sh

_ERR=$?
rm -f "$_INNER_SCRIPT"
if [ $_ERR -ne 0 ]; then
    echo "An error occurred during Cloud Data linux build"
    exit 1
fi
