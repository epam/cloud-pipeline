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

cd $WORKSPACE/cloud-pipeline/e2e/gui
echo $(ls $WORKSPACE/cloud-pipeline/e2e/gui)
echo "e2e.ui.default.timeout=${AWS_E2E_DEFAULT_TIMEOUT}" > default.conf
echo "e2e.ui.commit.appearing.timeout=${AWS_E2E_COMMIT_APPEARING_TIMEOUT}" >> default.conf
echo "e2e.ui.ssh.appearing.timeout=${AWS_E2E_SSH_APPEARING_TIMEOUT}" >> default.conf
echo "e2e.ui.committing.timeout=${AWS_E2E_COMMITTING_TIMEOUT}" >> default.conf
echo "e2e.ui.run.completion.timeout=${AWS_E2E_RUN_COMPLETION_TIMEOUT}" >> default.conf
echo "e2e.ui.buckets.mounting.timeout=${AWS_E2E_BUCKETS_MOUNTING_TIMEOUT}" >> default.conf
echo "e2e.ui.endpoint.initialization.timeout=${AWS_E2E_ENDPOINT_INITIALIZATION_TIMEOUT}" >> default.conf
echo "e2e.ui.login.delay.timeout=${AWS_E2E_LOGIN_DELAY_TIMEOUT}" >> default.conf
echo "e2e.ui.login=${AWS_E2E_LOGIN}" >> default.conf
echo "e2e.ui.password=${AWS_E2E_PASSWORD}" >> default.conf
echo "e2e.ui.root.address=${AWS_E2E_ROOT_ADDRESS}" >> default.conf
echo "e2e.ui.download.folder=${AWS_E2E_DOWNLOAD_FOLDER}" >> default.conf
echo "e2e.ui.default.registry=${AWS_E2E_DEFAULT_REGISTRY}" >> default.conf
echo "e2e.ui.default.registry.ip=${AWS_E2E_DEFAULT_REGISTRY_IP}" >> default.conf
echo "e2e.ui.default.group=${AWS_E2E_DEFAULT_GROUP}" >> default.conf
echo "e2e.ui.another.login=${AWS_E2E_ANOTHER_LOGIN}" >> default.conf
echo "e2e.ui.another.password=${AWS_E2E_ANOTHER_PASSWORD}" >> default.conf
echo "e2e.ui.clean.history.login=${AWS_E2E_CLEAN_HISTORY_LOGIN}" >> default.conf
echo "e2e.ui.clean.history.password=${AWS_E2E_CLEAN_HISTORY_PASSWORD}" >> default.conf
echo "e2e.ui.testing.tool=${AWS_E2E_TESTING_TOOL}" >> default.conf
echo "e2e.ui.tool.without.default.settings=${AWS_E2E_TOOL_WITHOUT_DEFAULT_SETTINGS}" >> default.conf
echo "e2e.ui.image.luigi=${AWS_E2E_IMAGE_LUIGI}" >> default.conf
echo "e2e.ui.valid.endpoint=${AWS_E2E_VALID_ENDPOINT}" >> default.conf
echo "e2e.ui.tool.registry.path=${AWS_E2E_TOOL_REGISTRY_PATH}" >> default.conf
echo "e2e.ui.tool.invalid.registry.path=${AWS_E2E_TOOL_INVALID_REGISTRY_PATH}" >> default.conf
echo "e2e.ui.registry.login=${AWS_E2E_REGISTRY_LOGIN}" >> default.conf
echo "e2e.ui.registry.password=${AWS_E2E_REGISTRY_PASSWORD}" >> default.conf
echo "e2e.ui.repository=${AWS_E2E_REPOSITORY}" >> default.conf
echo "e2e.ui.token=${AWS_E2E_TOKEN}" >> default.conf
echo "e2e.ui.storage.prefix=${AWS_E2E_STORAGE_PREFIX}" >> default.conf
echo "e2e.ui.default.instance.type=${AWS_E2E_DEFAULT_INSTANCE_TYPE}" >> default.conf
echo "e2e.ui.nfs.prefix=${AWS_E2E_NFS_PREFIX}" >> default.conf
echo "e2e.ui.default.instance.price.type=${AWS_E2E_DEFAULT_INSTANCE_PRICE_TYPE}" >> default.conf
echo "e2e.ui.cloud.provider=${AWS_E2E_CLOUD_PROVIDER}" >> default.conf
echo "e2e.ui.spot.price.name=${AWS_E2E_SPOT_PRICE_NAME}" >> default.conf
df -h

docker run  -i \
            --rm \
            -v $WORKSPACE/cloud-pipeline/e2e/gui:/headless/e2e/gui \
            --user 0:0 \
            -p 5902:5902 \
            -p 6902:6902 \
            -v /dev/shm:/dev/shm \
            consol/ubuntu-xfce-vnc \
            bash -c "df -h && mkdir -p /headless/Downloads && cd /headless/e2e/gui && sleep infinity"