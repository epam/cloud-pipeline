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
export USER_HOME_DIR="/headless"
echo "e2e.ui.default.timeout=${AZURE_E2E_DEFAULT_TIMEOUT}" > default.conf
echo "e2e.ui.commit.appearing.timeout=${AZURE_E2E_COMMIT_APPEARING_TIMEOUT}" >> default.conf
echo "e2e.ui.ssh.appearing.timeout=${AZURE_E2E_SSH_APPEARING_TIMEOUT}" >> default.conf
echo "e2e.ui.committing.timeout=${AZURE_E2E_COMMITTING_TIMEOUT}" >> default.conf
echo "e2e.ui.run.completion.timeout=${AZURE_E2E_RUN_COMPLETION_TIMEOUT}" >> default.conf
echo "e2e.ui.buckets.mounting.timeout=${AZURE_E2E_BUCKETS_MOUNTING_TIMEOUT}" >> default.conf
echo "e2e.ui.endpoint.initialization.timeout=${AZURE_E2E_ENDPOINT_INITIALIZATION_TIMEOUT}" >> default.conf
echo "e2e.ui.login.delay.timeout=${AZURE_E2E_LOGIN_DELAY_TIMEOUT}" >> default.conf
echo "e2e.ui.login=${AZURE_E2E_LOGIN}" >> default.conf
echo "e2e.ui.password=${AZURE_E2E_PASSWORD}" >> default.conf
echo "e2e.ui.root.address=${AZURE_E2E_ROOT_ADDRESS}" >> default.conf
echo "e2e.ui.download.folder=${AZURE_E2E_DOWNLOAD_FOLDER}" >> default.conf
echo "e2e.ui.default.registry=${AZURE_E2E_DEFAULT_REGISTRY}" >> default.conf
echo "e2e.ui.default.registry.ip=${AZURE_E2E_DEFAULT_REGISTRY_IP}" >> default.conf
echo "e2e.ui.default.group=${AZURE_E2E_DEFAULT_GROUP}" >> default.conf
echo "e2e.ui.another.login=${AZURE_E2E_ANOTHER_LOGIN}" >> default.conf
echo "e2e.ui.another.password=${AZURE_E2E_ANOTHER_PASSWORD}" >> default.conf
echo "e2e.ui.clean.history.login=${AZURE_E2E_CLEAN_HISTORY_LOGIN}" >> default.conf
echo "e2e.ui.clean.history.password=${AZURE_E2E_CLEAN_HISTORY_PASSWORD}" >> default.conf
echo "e2e.ui.testing.tool=${AZURE_E2E_TESTING_TOOL}" >> default.conf
echo "e2e.ui.tool.without.default.settings=${AZURE_E2E_TOOL_WITHOUT_DEFAULT_SETTINGS}" >> default.conf
echo "e2e.ui.image.luigi=${AZURE_E2E_IMAGE_LUIGI}" >> default.conf
echo "e2e.ui.valid.endpoint=${AZURE_E2E_VALID_ENDPOINT}" >> default.conf
echo "e2e.ui.tool.registry.path=${AZURE_E2E_TOOL_REGISTRY_PATH}" >> default.conf
echo "e2e.ui.tool.invalid.registry.path=${AZURE_E2E_TOOL_INVALID_REGISTRY_PATH}" >> default.conf
echo "e2e.ui.registry.login=${AZURE_E2E_REGISTRY_LOGIN}" >> default.conf
echo "e2e.ui.registry.password=${AZURE_E2E_REGISTRY_PASSWORD}" >> default.conf
echo "e2e.ui.repository=${AZURE_E2E_REPOSITORY}" >> default.conf
echo "e2e.ui.token=${AZURE_E2E_TOKEN}" >> default.conf
echo "e2e.ui.storage.prefix=${AZURE_E2E_STORAGE_PREFIX}" >> default.conf
echo "e2e.ui.default.instance.type=${AZURE_E2E_DEFAULT_INSTANCE_TYPE}" >> default.conf
echo "e2e.ui.nfs.prefix=${AZURE_E2E_NFS_PREFIX}" >> default.conf
echo "e2e.ui.default.instance.price.type=${AZURE_E2E_DEFAULT_INSTANCE_PRICE_TYPE}" >> default.conf
echo "e2e.ui.cloud.provider=${AZURE_E2E_CLOUD_PROVIDER}" >> default.conf
echo "e2e.ui.spot.price.name=${AZURE_E2E_SPOT_PRICE_NAME}" >> default.conf
df -h

docker run  -i \
            --rm \
            -v $WORKSPACE/cloud-pipeline/e2e/gui:/headless/e2e/gui \
            --user 0:0 \
            -p 5903:5903 \
            -p 6903:6903 \
            -v /dev/shm:/dev/shm \
            consol/ubuntu-xfce-vnc \
            bash -c "df -h && mkdir -p /$USER_HOME_DIR/Downloads && cd /$USER_HOME_DIR/e2e/gui && bash install.sh && ./gradlew clean test"