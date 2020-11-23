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

cd $WORKSPACE/cloud-pipeline/deploy
export CP_DOCKER_DIST_USER="$DOCKER_USER"
export CP_DOCKER_DIST_PASS="$DOCKER_PASS"
export CP_VERSION_SHORT="${dist_major}.${dist_minor}"
export CP_VERSION_FULL="${dist_major}.${dist_minor}.${dist_patch}.${dist_build}.${dist_commit}"
export CP_API_DIST_URL="$API_DIST_URL"
export CP_PIPECTL_DIST_FILE_NAME="pipectl.$CP_VERSION_FULL"
export CP_PIPECTL_DIST="$WORKSPACE/build/$CP_PIPECTL_DIST_FILE_NAME"
export CP_DOCKER_DIST_SRV=quay.io/

bash build.sh -o $CP_PIPECTL_DIST \
              -p $WORKSPACE/cloud-pipeline/workflows/pipe-templates/__SYSTEM/data_loader \
              -p $WORKSPACE/cloud-pipeline/e2e/prerequisites \
              -p $WORKSPACE/cloud-pipeline/workflows/pipe-demo \
              -v $CP_VERSION_SHORT \
              -t

if [ $? -eq 0 ]; then
    aws s3 cp $CP_PIPECTL_DIST s3://cloud-pipeline-oss-builds/builds/${dist_branch}/${CP_PIPECTL_DIST_FILE_NAME} && \
        cat > "/tmp/${JOB_NAME}-${BUILD_NUMBER}.env" <<< "PIPECTL_DIST_URL=https://s3.amazonaws.com/cloud-pipeline-oss-builds/builds/${dist_branch}/${CP_PIPECTL_DIST_FILE_NAME}"

else 
    echo "pipectl build artifacts skipped as build.sh returned non-zero exit code"
    exit 1
fi
