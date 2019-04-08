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

set -e 

# Grab the temporary artifacts into assemble/ dir
# - cloud-pipeline.${VERSION}.tgz
# - client.tgz
# - cli-linux.tgz
# - cli-win.tgz
mkdir assemble
cd assemble
aws s3 cp s3://cloud-pipeline-oss-builds/temp/${TRAVIS_BUILD_NUMBER}/ ./ --recursive

# Determine exact name of the cloud-pipeline.${VERSION}.tgz
DIST_TGZ_NAME=$(echo cloud-pipeline*.tgz)

# Untar cloud-pipeline.${VERSION}.tgz and unzip pipeline.jar
# TGZ contents will be located in assemble/bin/
# JAE contents will be located in assemble/pipeline-jar-repackage/
tar -zxf $DIST_TGZ_NAME
mkdir pipeline-jar-repackage
mv bin/pipeline.jar pipeline-jar-repackage/
cd pipeline-jar-repackage
unzip -q pipeline.jar
rm -f pipeline.jar
cd ..

# Untar Web GUI and move it to the pipeline.jar static assets
tar -zxf client.tgz
mv client/build/* pipeline-jar-repackage/BOOT-INF/classes/static/

# Untar pipe-cli linux binary and tar.gz. Move them to the pipeline.jar static assets
tar -zxf cli-linux.tgz
mv pipe-cli/dist/PipelineCLI-* pipeline-jar-repackage/BOOT-INF/classes/static/PipelineCLI.tar.gz
mv pipe-cli/dist/pipe pipeline-jar-repackage/BOOT-INF/classes/static/

# Untar pipe-cli windoes binary and move it to the pipeline.jar static assets
tar -zxf cli-win.tgz
mv pipe-cli/dist/win/pipe.zip pipeline-jar-repackage/BOOT-INF/classes/static/

# Zip pipeline.jar back
cd pipeline-jar-repackage/
zip -r -q pipeline.jar .

# Create distribution tgz with the original name
cd ..
mv pipeline-jar-repackage/pipeline.jar bin/
tar -zcf $DIST_TGZ_NAME bin/
