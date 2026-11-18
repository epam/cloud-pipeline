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

# Tests the options of build-ubuntu-repo.sh with a stub docker and a stub curl: which containers it runs, in which
# image, with which packages, previous repository, docker binary, TLS setting and GPG key, and its errors. The scripts
# that run inside the containers are only checked to parse (bash -n), not run. Needs bash only, no docker.
#
# Usage: test-build-ubuntu-repo.sh

set -uo pipefail
# The script reads DOCKER: one set in the caller's environment must not reach it
unset DOCKER

SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/build-ubuntu-repo.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "${TMP:?}"' EXIT
PUBLIC="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/ubuntu"
mkdir -p "$TMP/bin" "$TMP/other"

# A docker that logs each call, one argument per line after a "--- call" line, and fails on the FAIL_CALL-th call.
# It fails when the script of the container does not parse. For the repository container (a "<dir>:/repo" mount) it
# leaves a Release file and a package in <dir>, as the real one does, unless NO_REPO is set
DOCKER_STUB='#!/bin/bash
{ echo "--- call"; printf "%s\n" "$@"; } >> "$DOCKER_LOG"
n=$(( $(cat "$DOCKER_LOG.count" 2>/dev/null || echo 0) + 1 )); echo "$n" > "$DOCKER_LOG.count"
[ "${FAIL_CALL:-0}" -eq "$n" ] && exit 7
for script; do :; done
bash -n -c "$script" || { echo "the script of call $n does not parse" >&2; exit 9; }
for arg; do
    if [[ "$arg" == *:/repo ]] && [ -z "${NO_REPO:-}" ]; then
        mkdir -p "${arg%:/repo}/dists/stable" "${arg%:/repo}/pool/main"
        touch "${arg%:/repo}/dists/stable/Release" "${arg%:/repo}/pool/main/a.deb"
    fi
done
exit 0
'
printf '%s' "$DOCKER_STUB" > "$TMP/bin/docker"
printf '%s' "$DOCKER_STUB" > "$TMP/other/docker"
# A curl that logs its arguments and prints an HTTP code: 200 for the public repository of the release PUBLIC_VERSION,
# else 403 as S3 does. With CURL_FAILS set, it fails as with no network
cat > "$TMP/bin/curl" <<'EOF'
#!/bin/bash
echo "$*" >> "$CURL_LOG"
for url; do :; done
[ -n "${CURL_FAILS:-}" ] && { printf 000; exit 7; }
if [ -n "${PUBLIC_VERSION:-}" ] && [[ "$url" == */ubuntu/"$PUBLIC_VERSION"/* ]]; then printf 200; else printf 403; fi
EOF
chmod +x "$TMP/bin/docker" "$TMP/other/docker" "$TMP/bin/curl"
failed=0

# build <environment...> -- <options...>: runs the script with the stubs, and keeps its exit code, output and calls
build() {
    local env=()
    while [ $# -gt 0 ] && [ "$1" != "--" ]; do env+=("$1"); shift; done
    shift
    rm -rf "$TMP/out" "$TMP/docker.log"* "$TMP/curl.log"
    : > "$TMP/curl.log"
    (cd "$TMP" && env PATH="$TMP/bin:$PATH" DOCKER_LOG="$TMP/docker.log" CURL_LOG="$TMP/curl.log" ${env[@]+"${env[@]}"} \
        bash "$SCRIPT" "$@" > "$TMP/output" 2>&1)
    CODE=$?
}

# count <log>: the docker calls in a stub's log, 0 when there is none
count() { local n=0; [ -f "$1" ] && n="$(grep -c '^--- call$' "$1")"; echo "$n"; }
calls() { count "$TMP/docker.log"; }
# call <n>: the arguments of the n-th docker call
call() { awk -v n="$1" '/^--- call$/ { i++; next } i == n' "$TMP/docker.log" 2>/dev/null; }

check() {
    local case="$1"; shift
    if "$@"; then
        echo "[OK]   $case"
    else
        echo "[FAIL] $case (exit code $CODE): $(tail -3 "$TMP/output" | tr '\n' ' ')"
        failed=1
    fi
}

has() { call "$1" | grep -q -x -F -- "$2"; }
has_any() { call "$1" | grep -q -F -- "$2"; }
# all_in <image>: every docker call runs that image
all_in() { local i; for i in $(seq 1 "$(calls)"); do has "$i" "$1" || return 1; done; }

build -- --out out
check "default: 4 containers, each in ubuntu:24.04, done" eval '[ $CODE -eq 0 ] && [ "$(calls)" -eq 4 ] &&
    all_in ubuntu:24.04 && grep -q "^\[INFO\] Done" "$TMP/output"'
check "default: the downloads go to <out>/.debs" has 1 "$TMP/out/.debs:/debs"
check "default: no public 24.04 repository, so the previous one is 22.04" has 3 "PREV_REPO=$PUBLIC/22.04"
check "default: TLS checks on" eval 'has 2 INSECURE=false && has 3 INSECURE=false && ! grep -q -- "-k" "$TMP/curl.log"'
check "default: no GPG key" eval '! has_any 4 ":/gpg.key:ro" && has 4 GPG_KEY_ID='
check "default: the package lists, and the pinned versions from the previous repository" eval 'has 1 WANT_VERSION=24.04 &&
    has_any 1 "squashfs-tools libseccomp-dev" && has_any 3 "gridengine-master=8.1.9+dfsg-4*" &&
    has_any 3 "lizardfs-client=3.12.0" && has_any 3 "blobfuse=1.3.8"'
check "default: the upload command deletes the files of an older build" grep -q -- "tools/repos/ubuntu/24.04/ --delete" "$TMP/output"

build PUBLIC_VERSION=20.04 -- --out out --version 20.04
check "20.04: the image of the release, and its own public repository" eval '[ $CODE -eq 0 ] && all_in ubuntu:20.04 &&
    has 3 "PREV_REPO=$PUBLIC/20.04"'

build -- --out out --version 26.04 --image example/ubuntu:custom
check "--image replaces ubuntu:<version>" eval '[ $CODE -eq 0 ] && all_in example/ubuntu:custom && has 1 WANT_VERSION=26.04'

build -- --out out --prev-repo https://repo.example/ubuntu/22.04
check "--prev-repo is used as is, and not looked up" eval '[ $CODE -eq 0 ] &&
    has 3 PREV_REPO=https://repo.example/ubuntu/22.04 && [ ! -s "$TMP/curl.log" ]'

build CURL_FAILS=true -- --out out
check "the previous repository cannot be looked up: an error, and no container" eval '[ $CODE -eq 1 ] &&
    [ "$(calls)" -eq 0 ] && grep -q "Cannot check $PUBLIC/24.04 (HTTP code 000)" "$TMP/output"'

build -- --out out --no-prev-repo
check "--no-prev-repo: no container for the previous repository" eval '[ $CODE -eq 0 ] && [ "$(calls)" -eq 3 ] &&
    ! grep -q "^PREV_REPO=" "$TMP/docker.log"'

build -- --out out --no-cloud-fuse
check "--no-cloud-fuse: no container for gcsfuse and blobfuse2" eval '[ $CODE -eq 0 ] && [ "$(calls)" -eq 3 ] &&
    ! grep -q "packages-microsoft-prod" "$TMP/docker.log"'

build -- --out out --insecure
check "--insecure: no TLS checks in the containers, and curl -k" eval '[ $CODE -eq 0 ] && has 2 INSECURE=true &&
    has 3 INSECURE=true && grep -q -- "-k" "$TMP/curl.log"'

echo key > "$TMP/signing.key"
build -- --out out --gpg-key signing.key --gpg-key-id 0123456789ABCDEF
check "--gpg-key: the key file is mounted read-only into the repository container" eval '[ $CODE -eq 0 ] &&
    has 4 "$TMP/signing.key:/gpg.key:ro" && has 4 GPG_KEY_ID=0123456789ABCDEF && ! has_any 1 ":/gpg.key:ro"'

# Both stubs log to DOCKER_LOG, so the other binary is told apart by a log of its own
sed -i.bak 's|"\$DOCKER_LOG"|"$DOCKER_LOG.other"|; s|"\$DOCKER_LOG.count"|"$DOCKER_LOG.other.count"|g' "$TMP/other/docker"
build -- --out out --docker "$TMP/other/docker"
check "--docker: the other binary gets the 4 calls, docker on PATH none" eval '[ $CODE -eq 0 ] &&
    [ "$(count "$TMP/docker.log.other")" -eq 4 ] && [ "$(calls)" -eq 0 ]'
build DOCKER="$TMP/other/docker" -- --out out
check "DOCKER: the same as --docker" eval '[ $CODE -eq 0 ] && [ "$(count "$TMP/docker.log.other")" -eq 4 ] &&
    [ "$(calls)" -eq 0 ]'

build NO_REPO=true -- --out out
check "a repository that does not land in --out: an error, not Done" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 4 ] &&
    grep -q "has no repository" "$TMP/output" && ! grep -q "^\[INFO\] Done" "$TMP/output"'

build -- --out out --docker "$TMP/no-such-docker"
check "a docker binary that is not there: an error, and no container" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 0 ] &&
    grep -q "docker is not found: $TMP/no-such-docker" "$TMP/output"'

build FAIL_CALL=2 -- --out out
check "a container that fails stops the build" eval '[ $CODE -ne 0 ] && [ "$(calls)" -eq 2 ] &&
    ! grep -q "^\[INFO\] Done" "$TMP/output"'

build -- --version 24.04
check "no --out: an error" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 0 ] && grep -q -- "--out <dir> is required" "$TMP/output"'

build -- --out out --gpg-key signing.key
check "--gpg-key without --gpg-key-id: an error" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 0 ] &&
    grep -q -- "--gpg-key needs --gpg-key-id" "$TMP/output"'

build -- --out out --gpg-key no-such.key --gpg-key-id 0123456789ABCDEF
check "a --gpg-key that is not there: an error" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 0 ] &&
    grep -q -- "--gpg-key no-such.key: no such file" "$TMP/output"'

build -- --out out --gpg-key-id 0123456789ABCDEF
check "--gpg-key-id without --gpg-key: an error" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 0 ] &&
    grep -q -- "--gpg-key-id needs --gpg-key" "$TMP/output"'

for version in 18.04 16.04 24; do
    build -- --out out --version "$version"
    check "--version $version: an error" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 0 ] &&
        grep -q "Ubuntu 20.04 and later only" "$TMP/output"'
done

build -- --version 24.04 --out
check "an option without its value: an error" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 0 ] &&
    grep -q -- "--out needs a value" "$TMP/output"'
build -- --out --version 24.04
check "an option followed by another option: an error" eval '[ $CODE -eq 1 ] && grep -q -- "--out needs a value" "$TMP/output"'

build -- --out out --no-such-option
check "an unknown option: an error" eval '[ $CODE -eq 1 ] && [ "$(calls)" -eq 0 ] &&
    grep -q "Unknown option --no-such-option" "$TMP/output"'

[ "$failed" -eq 0 ] && echo "==== passed" || echo "==== FAILED"
exit "$failed"
