# shellcheck shell=bash
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

# Sourced by build.sh and check.sh: the Python 3.12 of the platform, the published build that the runs download,
# so the mirror is built and checked with the pip and the platform tags that the runs have.

CP_PYTHON3_DISTRO_URL="${CP_PYTHON3_DISTRO_URL:-https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/python/3/python3.12.tar.gz}"

# docker_shared_temp_dir: a new temporary folder that docker can mount. Under $HOME: Docker Desktop, colima and
# the GitHub runners share it with the containers, colima does not share /tmp or the macOS TMPDIR
docker_shared_temp_dir() {
    mktemp -d "$HOME/.cp-pypi3.XXXXXX"
}

# fetch_platform_python <dir>: downloads python3.12.tar.gz into <dir> and checks it against its .sha256
fetch_platform_python() {
    local dir="$1" expected actual
    if ! curl -sSfL --retry 3 -o "$dir/python3.12.tar.gz.sha256" "$CP_PYTHON3_DISTRO_URL.sha256" \
            || ! curl -sSfL --retry 3 -o "$dir/python3.12.tar.gz" "$CP_PYTHON3_DISTRO_URL"; then
        echo "[ERROR] The platform's Python 3.12 is not available: $CP_PYTHON3_DISTRO_URL or its .sha256." >&2
        echo "[ERROR] Build and publish it first: the python3 dependency of the Build-Dependencies workflow" \
             "(deploy/dependencies/python3), on develop." >&2
        return 1
    fi
    expected="$(awk '{ print $1; exit }' "$dir/python3.12.tar.gz.sha256")"
    if command -v sha256sum > /dev/null; then
        actual="$(sha256sum "$dir/python3.12.tar.gz" | awk '{ print $1 }')"
    else
        actual="$(shasum -a 256 "$dir/python3.12.tar.gz" | awk '{ print $1 }')"
    fi
    if [ -z "$expected" ] || [ "$expected" != "$actual" ]; then
        echo "[ERROR] $CP_PYTHON3_DISTRO_URL does not match its .sha256 (expected '$expected', got '$actual')" >&2
        return 1
    fi
    curl -sSfL --retry 3 -o "$dir/python3.12.BUILD-INFO.txt" "${CP_PYTHON3_DISTRO_URL%.tar.gz}.BUILD-INFO.txt" \
        || echo "no build info next to $CP_PYTHON3_DISTRO_URL" > "$dir/python3.12.BUILD-INFO.txt"
    echo "The platform's Python 3.12: $CP_PYTHON3_DISTRO_URL, sha256 $actual"
}
