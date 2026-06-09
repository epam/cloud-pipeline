#!/bin/bash

# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

set -euo pipefail

if [ -z "${VSC_EXTENSION_ROOT}" ]; then
  echo "[ERROR] VSC_EXTENSION_ROOT is not set" >&2
  exit 1
fi
VSC_EXTENSION_BUILD_DOCKER_IMAGE="${VSC_EXTENSION_BUILD_DOCKER_IMAGE:-node:20-bookworm}"

if [[ ! -f "$VSC_EXTENSION_ROOT/package.json" ]]; then
  echo "error: no package.json under VSC_EXTENSION_ROOT=$VSC_EXTENSION_ROOT" >&2
  exit 1
fi

if [[ ! -f "$VSC_EXTENSION_ROOT/package-lock.json" ]]; then
  echo "error: package-lock.json is required for npm ci (VSC_EXTENSION_ROOT=$VSC_EXTENSION_ROOT)" >&2
  exit 1
fi

VSC_COMMIT_HASH=$(git log --pretty=tformat:"%H" -n1 "${VSC_EXTENSION_ROOT}")

docker pull "$VSC_EXTENSION_BUILD_DOCKER_IMAGE" &> /dev/null

docker run --rm -i \
  -e "VSC_EXTENSION_ROOT=$VSC_EXTENSION_ROOT" \
  -e "VSC_COMMIT_HASH=$VSC_COMMIT_HASH" \
  -v "$VSC_EXTENSION_ROOT:$VSC_EXTENSION_ROOT" \
  -w "$VSC_EXTENSION_ROOT" \
  "$VSC_EXTENSION_BUILD_DOCKER_IMAGE" \
  bash -c 'set -euo pipefail
    version_file="${VSC_EXTENSION_ROOT}/src/version.ts"
    sed -i "s/COMPONENT_VERSION = '"'"'[a-f0-9]*'"'"'/COMPONENT_VERSION = '"'"'${VSC_COMMIT_HASH}'"'"'/" "$version_file"
    if [ -n "${VSC_EXTENSION_VERSION:-}" ]; then
      sed -i "s/\"version\": \"[^\"]*\"/\"version\": \"${VSC_EXTENSION_VERSION}\"/" "${VSC_EXTENSION_ROOT}/package.json"
    fi
    node --version
    npm --version
    npm ci
    npm run compile
    npm run package
    echo "VSIX written under ${VSC_EXTENSION_ROOT}"
    ls -1 "${VSC_EXTENSION_ROOT}"/*.vsix 2>/dev/null || true
  '

if [ ! -f "${VSC_EXTENSION_ROOT}"/cloud-pipeline-remote*.vsix ]; then
  echo "[ERROR] VSIX file not found" >&2
  exit 1
fi

mv "${VSC_EXTENSION_ROOT}"/cloud-pipeline-remote*.vsix "${VSC_EXTENSION_ROOT}"/cloud-pipeline-remote.vsix
