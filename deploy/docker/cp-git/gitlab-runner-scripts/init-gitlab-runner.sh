#!/bin/bash

# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
# Execute this script by hand, as the GitLab registration token can't bre retrieved from the API
# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!

if [ -z "$CP_GITLAB_INTERNAL_HOST" ] || [ -z "$CP_GITLAB_INTERNAL_PORT" ]; then
    echo "[ERROR] Can't build a gitlab URL. \$CP_GITLAB_INTERNAL_HOST or \$CP_GITLAB_INTERNAL_PORT is not specified"
    exit 1
fi
_CP_GITLAB_URL="https://${CP_GITLAB_INTERNAL_HOST}:${CP_GITLAB_INTERNAL_PORT}"

if [ -z "$CP_API_SRV_INTERNAL_HOST" ] || [ -z "$CP_API_SRV_INTERNAL_PORT" ]; then
    echo "[ERROR] Can't download 'pipe' CLI as the \$CP_API_INTERNAL_HOST or \$CP_API_INTERNAL_PORT is not specified"
    exit 1
fi
_CP_API_PIPE_URL="https://${CP_API_SRV_INTERNAL_HOST}:${CP_API_SRV_INTERNAL_PORT}/pipeline/pipe"

if [ -z "$1" ]; then
    echo "[ERROR] Specify a Shared Runner registration token as a first argument, it can be retrieved from ${_CP_GITLAB_URL}/admin/runners"
    exit 1
fi
_CP_GITLAB_REGISTRATION_TOKEN="$1"

CP_GITLAB_RUNNER_BUILDS_DIR="${CP_GITLAB_RUNNER_BUILDS_DIR:-/tmp/gitlab}"
CP_GITLAB_RUNNER_CACHE_DIR="${CP_GITLAB_RUNNER_CACHE_DIR:-/tmp/gitlab/cache}"
CP_GITLAB_RUNNER_CA_CERT_PATH="${CP_GITLAB_RUNNER_CA_CERT_PATH:-$CP_COMMON_CERT_DIR/ca-public-cert.pem}"
CP_GITLAB_RUNNER_MAX_JOBS="${CP_GITLAB_RUNNER_MAX_JOBS:-25}"

if ! which pipe &> /dev/null; then
    echo "[INFO] Downloading 'pipe' CLI"
    wget --quiet -O /bin/pipe --no-check-certificate "$_CP_API_PIPE_URL" && \
    chmod +x /bin/pipe
    if [ $? -ne 0 ]; then
        echo "[ERROR] Cannot download 'pipe' CLI from $_CP_API_PIPE_URL, exiting"
        exit 1
    fi
fi

if ! pipe --version &> /dev/null; then
    echo "[ERROR] Cannot use 'pipe' CLI. '--version' command returned non-zero exit code, exiting"
    exit 1
else
    echo "[OK] 'pipe' CLI is working fine"
fi

gitlab-runner register \
  --url "$_CP_GITLAB_URL" \
  --registration-token "$_CP_GITLAB_REGISTRATION_TOKEN" \
  --name "${CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME:-Cloud Pipeline} GitLab Runner" \
  --executor custom \
  --builds-dir "${CP_GITLAB_RUNNER_BUILDS_DIR}" \
  --cache-dir "${CP_GITLAB_RUNNER_CACHE_DIR}" \
  --custom-prepare-exec "/gitlab-runner-scripts/prepare.sh" \
  --custom-run-exec "/gitlab-runner-scripts/run.sh" \
  --custom-cleanup-exec "/gitlab-runner-scripts/cleanup.sh" \
  --tls-ca-file="$CP_GITLAB_RUNNER_CA_CERT_PATH" \
  --non-interactive \
  --locked=false

if [ $? -ne 0 ]; then
    echo "[ERROR] Runner registration failed, exiting"
    exit 1
fi

sed -i "/^concurrent/d" /etc/gitlab-runner/config.toml && \
sed  -i "1i concurrent = $CP_GITLAB_RUNNER_MAX_JOBS" /etc/gitlab-runner/config.toml
if [ $? -ne 0 ]; then
    echo "[WARN] Cannot update 'concurrent' value for the runner at /etc/gitlab-runner/config.toml"
else
    echo "[OK] 'concurrent' value was updated to $CP_GITLAB_RUNNER_MAX_JOBS for the runner at /etc/gitlab-runner/config.toml"
fi