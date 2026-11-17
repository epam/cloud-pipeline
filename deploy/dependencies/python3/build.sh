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

# Builds the platform's Python 3.12 distribution in the manylinux2014 image (glibc 2.17), so it runs on
# CentOS 7 and later. Needs Docker; on a non-x86-64 host it runs under emulation, which is much slower.
#
# Usage: build.sh <output dir>
# Writes python3.12.tar.gz, python3.12.tar.gz.sha256 and BUILD-INFO.txt into the output dir.

set -euo pipefail

# quay.io/pypa/manylinux2014_x86_64:2026.11.01-2
BUILD_IMAGE="quay.io/pypa/manylinux2014_x86_64@sha256:dc58399aca4cfb0de257f92e2de71b4f8421e60cce8542fcd5e28df0c0760942"

RECIPE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:?usage: $0 <output dir>}"
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
GIT_COMMIT="${GIT_COMMIT:-$(git -C "$RECIPE_DIR" rev-parse HEAD 2>/dev/null || echo unknown)}"

docker run --rm --platform linux/amd64 \
    -e BUILD_IMAGE="$BUILD_IMAGE" \
    -e GIT_COMMIT="$GIT_COMMIT" \
    -v "$RECIPE_DIR":/recipe:ro \
    -v "$OUTPUT_DIR":/out \
    "$BUILD_IMAGE" \
    bash /recipe/build-in-container.sh

ls -l "$OUTPUT_DIR"
