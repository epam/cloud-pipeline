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

# Checks a built pypi3 mirror in a base image, with the platform's Python 3.12 from the distribution bucket, as
# a run uses it: the pipeline package of this repository installs from the mirror alone, and every requirement
# of requirements.txt has a file that fits the image. The mirror is served from the build, and a file of the
# index that the build does not have, published before, from the bucket. Needs Docker and curl.
#
# Usage: check.sh <image> <output dir of build.sh>
# e.g.   check.sh centos:7 /tmp/pypi3

set -euo pipefail

IMAGE="${1:?usage: $0 <image> <output dir of build.sh>}"
BUILD_DIR="$(cd "${2:?usage: $0 <image> <output dir of build.sh>}" && pwd)"
RECIPE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$RECIPE_DIR/../../.." && pwd)"
CP_PYPI3_FILES_URL="${CP_PYPI3_FILES_URL:-https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/python/pypi3/}"

[ -f "$BUILD_DIR/simple/index.html" ] || { echo "[ERROR] $BUILD_DIR has no simple/index.html" >&2; exit 1; }

# shellcheck source=platform-python.sh
. "$RECIPE_DIR/platform-python.sh"
WORK="$(docker_shared_temp_dir)"
trap 'rm -rf "${WORK:?}"' EXIT
fetch_platform_python "$WORK"

docker run --rm --platform linux/amd64 \
    -e CP_PYPI3_FILES_URL="$CP_PYPI3_FILES_URL" \
    -v "$WORK":/python:ro \
    -v "$BUILD_DIR":/mirror:ro \
    -v "$REPO_DIR":/repo:ro \
    "$IMAGE" \
    sh /repo/deploy/dependencies/pypi3/check-in-image.sh
