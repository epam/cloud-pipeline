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

# The pypi3 mirror (s3://cloud-pipeline-oss-builds/tools/python/pypi3/) is built from deploy/dependencies/pypi3/:
# its package list is deploy/dependencies/pypi3/requirements.txt, and the Build-Dependencies workflow builds,
# checks and publishes it on develop when that folder changes. This runs the same steps by hand: the build, the
# check in each image of check-images.txt, and the publication. Needs Docker, curl, python3 and the aws CLI with
# write access to the mirror; see deploy/dependencies/pypi3/README.md.
#
# Usage: build-repo-pypi3.sh [<output dir>]    (new or empty; default: a new folder under $HOME)
# CP_PYPI3_PUBLISH_DRY_RUN=1 stops before the first write to the bucket.

set -euo pipefail

RECIPE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../dependencies/pypi3" && pwd)"
OUTPUT_DIR="${1:-$(mktemp -d "$HOME/pypi3-mirror.XXXXXX")}"
echo "Building into $OUTPUT_DIR"

bash "$RECIPE_DIR/test.sh"
bash "$RECIPE_DIR/build.sh" "$OUTPUT_DIR"
while read -r image _ || [ -n "$image" ]; do
    image="${image%$'\r'}"
    case "$image" in
        ''|'#'*) continue ;;
    esac
    bash "$RECIPE_DIR/check.sh" "$image" "$OUTPUT_DIR"
done < "$RECIPE_DIR/check-images.txt"
bash "$RECIPE_DIR/publish.sh" "$OUTPUT_DIR"
