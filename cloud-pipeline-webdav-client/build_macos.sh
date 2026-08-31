#!/bin/bash
# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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


cd "$CP_CLOUD_DATA_SOURCES_DIR"
CLOUD_DATA_COMMIT_HASH=$(git log --pretty=tformat:"%H" -n1 .)

version_file="scripts/PublishVersionPlugin.js"
cp $version_file /tmp/version.bkp
sed -i '' "s/1111111111111111111111111111111111111111/$CLOUD_DATA_COMMIT_HASH/g" $version_file

npm install
npm run package:linux

if [ $? -ne 0 ]; then
    echo "Unable to build UI for MacOS"
    cp /tmp/version.bkp \$version_file
    exit 1
fi

cd out 
zip -vr cloud-data-darwin-arm64.zip cloud-data-darwin-arm64

