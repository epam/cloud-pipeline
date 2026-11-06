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

# Builds the pypi3 mirror: downloads each requirement of requirements.txt, with its dependencies, with the
# platform's Python 3.12 from the distribution bucket, then writes the index of these files merged with the
# index published today, which it never drops a link of. Needs Docker, curl and python3; on a non-x86-64 host
# the downloads run under emulation.
#
# Usage: build.sh <output dir>
# Writes the package files, simple/ and BUILD-INFO.txt into the output dir: the layout of the mirror. The output
# dir must have no package files: the index, and the publication, take every one of them.

set -euo pipefail

# pip picks the files that fit the platform it runs on, so the downloads run twice: on glibc 2.17, the oldest
# that the platform supports (CentOS 7), and on glibc 2.34 (Rocky Linux 9 / RHEL 9). A package that has no
# glibc 2.17 wheel gets its source distribution there and its newer wheel here.
# quay.io/pypa/manylinux2014_x86_64:2026.11.01-2 and quay.io/pypa/manylinux_2_34_x86_64:2026.11.01-2
BUILD_IMAGES=(
    "quay.io/pypa/manylinux2014_x86_64@sha256:dc58399aca4cfb0de257f92e2de71b4f8421e60cce8542fcd5e28df0c0760942"
    "quay.io/pypa/manylinux_2_34_x86_64@sha256:e0b7a1bdb4a0b7d519b29b301e2d3c626a33ea78e9bb5bb1590ce2d99d0ae62f"
)
CP_PYPI3_MIRROR_URL="${CP_PYPI3_MIRROR_URL:-http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/tools/python/pypi3/simple}"

RECIPE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:?usage: $0 <output dir>}"
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
GIT_COMMIT="${GIT_COMMIT:-$(git -C "$RECIPE_DIR" rev-parse HEAD 2>/dev/null || echo unknown)}"
if [ -n "$(find "$OUTPUT_DIR" -maxdepth 1 -type f \( -name '*.whl' -o -name '*.tar.gz' -o -name '*.zip' \) | head -1)" ]; then
    echo "[ERROR] $OUTPUT_DIR has package files already: give a new or empty output dir," \
         "the index and the publication take every package file of it" >&2
    exit 1
fi
# shellcheck source=platform-python.sh
. "$RECIPE_DIR/platform-python.sh"
WORK="$(docker_shared_temp_dir)"
trap 'rm -rf "${WORK:?}"' EXIT
fetch_platform_python "$WORK"

for image in "${BUILD_IMAGES[@]}"; do
    echo "==== Downloading in $image"
    docker run --rm --platform linux/amd64 \
        -v "$WORK":/python:ro \
        -v "$RECIPE_DIR":/recipe:ro \
        -v "$OUTPUT_DIR":/out \
        "$image" \
        bash /recipe/download-in-container.sh
done

echo "==== Index"
python3 -I "$RECIPE_DIR/mirror.py" fetch "$CP_PYPI3_MIRROR_URL" "$WORK/published"
rm -rf "${OUTPUT_DIR:?}/simple"
python3 -I "$RECIPE_DIR/mirror.py" merge "$OUTPUT_DIR/simple" "$OUTPUT_DIR" "$WORK/published"
python3 -I "$RECIPE_DIR/mirror.py" require "$RECIPE_DIR/requirements.txt" "$OUTPUT_DIR/simple"

{
    echo "pypi3 mirror build"
    echo "commit: $GIT_COMMIT"
    echo "built: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "merged with: $CP_PYPI3_MIRROR_URL"
    echo "downloaded in:"
    printf '  %s\n' "${BUILD_IMAGES[@]}"
    echo "with $CP_PYTHON3_DISTRO_URL:"
    sed 's/^/  /' "$WORK/python3.12.BUILD-INFO.txt"
} > "$OUTPUT_DIR/BUILD-INFO.txt"

echo "Files: $(find "$OUTPUT_DIR" -maxdepth 1 -type f \( -name '*.whl' -o -name '*.tar.gz' -o -name '*.zip' \) | wc -l | tr -d ' ')," \
     "$(du -sh "$OUTPUT_DIR" | cut -f1)"
