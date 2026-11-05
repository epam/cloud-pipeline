#!/bin/sh
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

# Runs in a base image, started by check.sh, with the platform's python3.12.tar.gz in /python, the build in
# /mirror and the repository in /repo. POSIX sh: the base images differ.

P=/usr/local/python3.12/bin/python3.12
INDEX=http://127.0.0.1:8000/simple
PIP_ARGS="-q --disable-pip-version-check --no-cache-dir --index-url $INDEX"
export PIP_ROOT_USER_ACTION=ignore
FAILED=0

pass() { echo "[OK] $*"; }
fail() { echo "[FAIL] $*"; FAILED=1; }

OS="$( (. /etc/os-release && echo "$PRETTY_NAME") 2>/dev/null || cat /etc/system-release)"
echo "==== $OS, glibc $(ldd --version 2>&1 | head -1 | awk '{ print $NF }')"

if command -v tar > /dev/null; then
    tar -xzf /python/python3.12.tar.gz -C /usr/local
else
    for py in python2 python python3; do
        command -v "$py" > /dev/null \
            && "$py" -c "import tarfile; tarfile.open('/python/python3.12.tar.gz').extractall('/usr/local')" && break
    done
fi
"$P" --version > /dev/null 2>&1 || { fail "$P does not start"; exit 1; }

# The mirror as a run sees it after the publication: the merged index, with the files of the build, and the
# files published before from the bucket
"$P" -I /repo/deploy/dependencies/pypi3/mirror.py serve /mirror 8000 "$CP_PYPI3_FILES_URL" > /tmp/serve.log 2>&1 &
for _ in 1 2 3 4 5 6 7 8 9 10; do
    "$P" -c "import urllib.request; urllib.request.urlopen('$INDEX/', timeout=5)" 2> /dev/null && break
    sleep 1
done
"$P" -c "import urllib.request; urllib.request.urlopen('$INDEX/', timeout=5)" \
    || { fail "the mirror is not served: $(cat /tmp/serve.log)"; exit 1; }

# The pipeline package, as a run installs it
cp -r /repo/workflows/pipe-common /tmp/pipe-common
if "$P" -m pip install $PIP_ARGS /tmp/pipe-common > /tmp/pip.log 2>&1; then
    case "$("$P" -m pip check 2>&1)" in
        "No broken requirements found.") pass "pipeline package from the mirror, pip check clean" ;;
        *) fail "pipeline package from the mirror, pip check: $("$P" -m pip check 2>&1 | head -3)" ;;
    esac
else
    fail "pipeline package from the mirror: $(grep -E '^ERROR' /tmp/pip.log | head -3)"
fi

# Each requirement has a file that pip picks in this image
missing=""
while read -r requirement _ || [ -n "$requirement" ]; do
    requirement="$(printf '%s' "$requirement" | tr -d '\r')"
    case "$requirement" in
        ''|'#'*) continue ;;
    esac
    "$P" -m pip download $PIP_ARGS --no-deps --dest /tmp/download "$requirement" > /tmp/download.log 2>&1 \
        || missing="$missing $requirement"
done < /repo/deploy/dependencies/pypi3/requirements.txt
if [ -z "$missing" ]; then
    pass "every requirement has a file for this image"
else
    fail "no file for this image, or its metadata does not build:$missing"
fi

[ "$FAILED" -eq 0 ] && echo "==== All checks passed in $OS" || echo "==== Checks failed in $OS"
exit "$FAILED"
