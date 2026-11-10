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

# Runs in a build image, started by build.sh, with the platform's python3.12.tar.gz in /python, this folder in
# /recipe and the output dir in /out. Unpacks the Python into /usr/local as a run does, and downloads each
# requirement with it from PyPI into /out. A file that is there already is not downloaded again.

set -uo pipefail

P=/usr/local/python3.12/bin/python3.12
if ! tar -xzf /python/python3.12.tar.gz -C /usr/local || ! "$P" --version > /dev/null; then
    echo "[ERROR] the platform's Python 3.12 does not unpack or start from /python/python3.12.tar.gz"
    exit 1
fi
echo "$("$P" --version), glibc $(ldd --version 2>&1 | head -1 | awk '{ print $NF }')"

failed=()
while read -r requirement _ || [ -n "$requirement" ]; do
    requirement="${requirement%$'\r'}"
    case "$requirement" in
        ''|'#'*) continue ;;
    esac
    if ! "$P" -m pip download -q --disable-pip-version-check --no-cache-dir --dest /out "$requirement"; then
        failed+=("$requirement")
    fi
done < /recipe/requirements.txt

if [ "${#failed[@]}" -ne 0 ]; then
    echo "[ERROR] pip could not download: ${failed[*]}"
    exit 1
fi
