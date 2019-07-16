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

mkdir -p /home/cloud-pipeline/
git clone https://github.com/epam/cloud-pipeline.git /home/cloud-pipeline/
cd /home/cloud-pipeline/
git checkout ${GIT_BRANCH}

pip install -r /home/cloud-pipeline/e2e/cli/requirements.txt
pip install -r /home/cloud-pipeline/pipe-cli/requirements.txt
pip install awscli

mkdir -p /home/pipe
wget --no-check-certificate ${PIPE_CLI_DOWNLOAD_URL} -O /home/pipe/pipe
chmod +x /home/pipe/pipe
export PATH=$PATH:/home/pipe/

export PYTHONPATH=$PYTHONPATH:/home/cloud-pipeline/pipe-cli:/home/cloud-pipeline/e2e/cli

${RUN_TESTS_CMD}
RUN_1=$?
${RUN_METADATA_TESTS_CMD}
RUN_2=$?

if [[ $RUN_1 -ne 0 || -z $RUN_METADATA_TESTS_CMD && $RUN_2 -ne 0 ]];
then
    exit 1
fi
