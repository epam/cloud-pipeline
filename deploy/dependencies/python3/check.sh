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

# Checks a built Python 3.12 distribution in a base image: unpacked into /usr/local as a run does, its
# modules load, pip works and the pipeline package installs from the pypi3 mirror. Needs Docker.
#
# Usage: check.sh <image> <folder with python3.12.tar.gz>
# e.g.   check.sh centos:7 /tmp/python3

set -euo pipefail

IMAGE="${1:?usage: $0 <image> <folder with python3.12.tar.gz>}"
DIST_DIR="$(cd "${2:?usage: $0 <image> <folder with python3.12.tar.gz>}" && pwd)"
REPO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"

if command -v sha256sum > /dev/null; then
    (cd "$DIST_DIR" && sha256sum -c python3.12.tar.gz.sha256)
else
    (cd "$DIST_DIR" && shasum -a 256 -c python3.12.tar.gz.sha256)
fi

docker run --rm --platform linux/amd64 \
    -v "$DIST_DIR":/dist:ro \
    -v "$REPO_DIR":/repo:ro \
    "$IMAGE" \
    sh /repo/deploy/dependencies/python3/check-in-image.sh
