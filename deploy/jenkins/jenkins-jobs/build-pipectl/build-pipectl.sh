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
export CP_VERSION_FULL="${dist_major}.${dist_minor}.${dist_patch}.${dist_build}"
export CP_API_DIST_URL="$API_DIST_URL"
export CP_PIPECTL_DIST="$WORKSPACE/build/pipectl-$CP_VERSION_FULL"

bash build.sh -o $CP_PIPECTL_DIST \
              -p $WORKSPACE/cloud-pipeline/workflows/pipe-templates/__SYSTEM/data_loader \
              -p $WORKSPACE/cloud-pipeline/e2e/prerequisites \
              -p $WORKSPACE/cloud-pipeline/workflows/pipe-demo \
              -v $CP_VERSION_SHORT \
              -t
