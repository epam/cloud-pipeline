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

# Tests select-changed.sh in a scratch git repository: which dependencies a push, a pull request or a manual run
# selects, and the JSON it prints. The workflow runs it before it selects. Needs git and python3.
#
# Usage: test-select-changed.sh

set -uo pipefail

SELECT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/select-changed.sh"
REPO="$(mktemp -d)"
trap 'rm -rf "${REPO:?}"' EXIT
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@localhost GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@localhost

cd "$REPO" || exit 1
git init -q
mkdir -p deploy/dependencies/python3 deploy/dependencies/other deploy/dependencies/helpers .github/workflows api
cp "$SELECT" deploy/dependencies/select-changed.sh
echo '# docs' > deploy/dependencies/README.md
for dependency in python3 other; do
    for f in build.sh check.sh README.md sources.txt; do echo a > "deploy/dependencies/$dependency/$f"; done
done
printf '# images\ncentos:7\n\nubuntu:24.04\n' > deploy/dependencies/python3/check-images.txt
printf 'debian:12\n' > deploy/dependencies/other/check-images.txt
echo a > deploy/dependencies/helpers/common.sh
echo a > .github/workflows/build-dependencies.yml
echo a > api/Main.java
git add -A && git commit -qm base
BASE="$(git rev-parse HEAD)"

PYTHON3='{"dependency": "python3", "image": "centos:7"},{"dependency": "python3", "image": "ubuntu:24.04"}'
OTHER='{"dependency": "other", "image": "debian:12"}'
BOTH_DEPENDENCIES='["other","python3"]'
BOTH_CHECKS="[$OTHER,$PYTHON3]"
failed=0

# expect <case> <dependencies> <checks> <environment...>: the selection succeeds and prints these lines
expect() {
    local case="$1" dependencies="$2" checks="$3" out got_dependencies got_checks
    shift 3
    if ! out="$(env "$@" bash deploy/dependencies/select-changed.sh 2> "$REPO.err")"; then
        echo "[FAIL] $case: exit code $?: $(cat "$REPO.err")"
        failed=1
        return
    fi
    got_dependencies="$(sed -n 's/^dependencies=//p' <<< "$out")"
    got_checks="$(sed -n 's/^checks=//p' <<< "$out")"
    if [ "$got_dependencies" != "$dependencies" ] || [ "$got_checks" != "$checks" ]; then
        echo "[FAIL] $case: dependencies=$got_dependencies checks=$got_checks, expected $dependencies $checks"
        failed=1
    elif ! python3 -c 'import json, sys; [json.loads(line.split("=", 1)[1]) for line in sys.stdin]' <<< "$out"; then
        echo "[FAIL] $case: not JSON: $out"
        failed=1
    else
        echo "[OK]   $case: $got_dependencies"
    fi
}

# expect_failure <case> <environment...>: the selection fails
expect_failure() {
    local case="$1"
    shift
    if env "$@" bash deploy/dependencies/select-changed.sh > /dev/null 2> "$REPO.err"; then
        echo "[FAIL] $case: exit code 0"
        failed=1
    else
        echo "[OK]   $case: fails with $(tail -1 "$REPO.err")"
    fi
}

# change <file...>: a commit on top of the base that appends a comment line to each file
change() {
    git checkout -q "$BASE" && git checkout -q -B "change-$RANDOM"
    for f in "$@"; do echo "# $RANDOM" >> "$f"; done
    git commit -qam change
}

change deploy/dependencies/python3/build.sh
expect "push, recipe of python3" '["python3"]' "[$PYTHON3]" EVENT_NAME=push BEFORE_SHA="$BASE"
change deploy/dependencies/python3/check-images.txt
expect "push, images of python3" '["python3"]' "[$PYTHON3]" EVENT_NAME=push BEFORE_SHA="$BASE"
change deploy/dependencies/python3/README.md
expect "push, docs of python3 only" '[]' '[]' EVENT_NAME=push BEFORE_SHA="$BASE"
change deploy/dependencies/README.md
expect "push, docs of deploy/dependencies only" '[]' '[]' EVENT_NAME=push BEFORE_SHA="$BASE"
change deploy/dependencies/select-changed.sh
expect "push, script of deploy/dependencies" "$BOTH_DEPENDENCIES" "$BOTH_CHECKS" EVENT_NAME=push BEFORE_SHA="$BASE"
change .github/workflows/build-dependencies.yml
expect "push, workflow" "$BOTH_DEPENDENCIES" "$BOTH_CHECKS" EVENT_NAME=push BEFORE_SHA="$BASE"
change api/Main.java
expect "push, file outside deploy/dependencies" '[]' '[]' EVENT_NAME=push BEFORE_SHA="$BASE"
change deploy/dependencies/helpers/common.sh
expect "push, folder that is no dependency" '[]' '[]' EVENT_NAME=push BEFORE_SHA="$BASE"
change deploy/dependencies/other/build.sh
expect "pull request, recipe of other" '["other"]' "[$OTHER]" EVENT_NAME=pull_request BASE_SHA="$BASE"
expect "pull request, no base commit" "$BOTH_DEPENDENCIES" "$BOTH_CHECKS" EVENT_NAME=pull_request BASE_SHA=

git checkout -q "$BASE" && git checkout -q -B move
git mv deploy/dependencies/python3/sources.txt api/sources.txt && git commit -qm move
expect "push, file moved out of python3" '["python3"]' "[$PYTHON3]" EVENT_NAME=push BEFORE_SHA="$BASE"

change deploy/dependencies/python3/build.sh
expect "push, new branch" "$BOTH_DEPENDENCIES" "$BOTH_CHECKS" \
    EVENT_NAME=push BEFORE_SHA=0000000000000000000000000000000000000000
expect "push, commit before the push not in the history" "$BOTH_DEPENDENCIES" "$BOTH_CHECKS" \
    EVENT_NAME=push BEFORE_SHA=1111111111111111111111111111111111111111

expect "manual run, all" "$BOTH_DEPENDENCIES" "$BOTH_CHECKS" EVENT_NAME=workflow_dispatch SELECTED=all
expect "manual run, python3" '["python3"]' "[$PYTHON3]" EVENT_NAME=workflow_dispatch SELECTED=python3
expect_failure "manual run, misspelled folder" EVENT_NAME=workflow_dispatch SELECTED=pyhton3
expect_failure "manual run, trailing space" EVENT_NAME=workflow_dispatch "SELECTED=python3 "
expect_failure "manual run, folder that is no dependency" EVENT_NAME=workflow_dispatch SELECTED=helpers

git checkout -q "$BASE" && git checkout -q -B images
printf 'centos:7\r\n# comment\r\nubuntu:24.04' > deploy/dependencies/python3/check-images.txt
git commit -qam images
expect "images with Windows line endings and no last newline" '["python3"]' "[$PYTHON3]" \
    EVENT_NAME=push BEFORE_SHA="$BASE"

git checkout -q "$BASE" && git checkout -q -B no-images
printf '# no image yet\n' > deploy/dependencies/python3/check-images.txt
git commit -qam no-images
expect_failure "dependency without an image" EVENT_NAME=push BEFORE_SHA="$BASE"

git checkout -q "$BASE"
if EVENT_NAME=workflow_dispatch SELECTED=all bash deploy/dependencies/select-changed.sh 2>&1 >/dev/null \
        | grep -q 'helpers has no build.sh'; then
    echo "[OK]   warning for the folder that is no dependency"
else
    echo "[FAIL] no warning for the folder that is no dependency"
    failed=1
fi

rm -f "$REPO.err"
[ "$failed" -eq 0 ] && echo "==== passed" || echo "==== FAILED"
exit "$failed"
