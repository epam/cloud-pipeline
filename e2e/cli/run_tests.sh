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

export CP_SRC=${CP_SRC:-/home/cloud-pipeline}
export RESULTS_DIR=${RESULTS_DIR:-/home/results}
export CP_CLI_DIR=${CP_CLI_DIR:-/home/pipe}
export CP_VERSION=${CP_VERSION:-0.17}

mkdir -p ${CP_SRC}
git clone https://github.com/epam/cloud-pipeline.git ${CP_SRC}
cd ${CP_SRC}
git checkout ${GIT_BRANCH}

pip install -r ${CP_SRC}/pipe-cli/requirements.txt
pip install -r ${CP_SRC}/e2e/cli/requirements.txt
pip install -I requests==2.22.0

if [[ -z $PIPE_CLI_DOWNLOAD_URL ]]; then
    pip install -I google-resumable-media==0.3.2
    cd pipe-cli
    python setup.py sdist
    cd dist
    pip install PipelineCLI-${CP_VERSION}.tar.gz
else
    mkdir -p ${CP_CLI_DIR}
    wget --no-check-certificate ${PIPE_CLI_DOWNLOAD_URL} -O ${CP_CLI_DIR}/pipe
    chmod +x ${CP_CLI_DIR}/pipe
    export PATH=$PATH:${CP_CLI_DIR}
fi

pip install awscli==1.14.56

export PYTHONPATH=$PYTHONPATH:${CP_SRC}/pipe-cli:${CP_SRC}/e2e/cli

${RUN_TESTS_CMD}
RUN_TESTS_CMD_EXIT_CODE=$?

${RUN_METADATA_TESTS_CMD}
RUN_METADATA_TESTS_CMD_EXIT_CODE=$?

${RUN_MOUNT_OBJECT_TESTS_CMD}
RUN_MOUNT_OBJECT_TESTS_CMD_EXIT_CODE=$?

${RUN_MOUNT_OBJECT_PREFIX_TESTS_CMD}
RUN_MOUNT_OBJECT_PREFIX_TESTS_CMD_EXIT_CODE=$?

${RUN_MOUNT_WEBDAV_TESTS_CMD}
RUN_MOUNT_WEBDAV_TESTS_CMD_EXIT_CODE=$?

if [[ "$RUN_TESTS_CMD" && $RUN_TESTS_CMD_EXIT_CODE -ne 0 ]] \
   || [[ "$RUN_METADATA_TESTS_CMD" && $RUN_METADATA_TESTS_CMD_EXIT_CODE -ne 0 ]] \
   || [[ "$RUN_MOUNT_OBJECT_TESTS_CMD" && $RUN_MOUNT_OBJECT_TESTS_CMD_EXIT_CODE -ne 0 ]] \
   || [[ "$RUN_MOUNT_OBJECT_PREFIX_TESTS_CMD" && $RUN_MOUNT_OBJECT_PREFIX_TESTS_CMD_EXIT_CODE -ne 0 ]] \
   || [[ "$RUN_MOUNT_WEBDAV_TESTS_CMD" && $RUN_MOUNT_WEBDAV_TESTS_CMD_EXIT_CODE -ne 0 ]]; then
    exit 1
fi
