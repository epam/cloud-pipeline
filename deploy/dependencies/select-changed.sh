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

# Selects the dependencies to build, for the outputs of a GitHub Actions step. A dependency is a folder of
# deploy/dependencies/ with build.sh, check.sh and check-images.txt. It is built when the push or pull request
# changed its folder, the files of deploy/dependencies/ itself or the workflow, or when a manual run selects it.
# Prints two lines:
#   dependencies=["<folder>", ...]                                   the dependencies to build
#   checks=[{"dependency": "<folder>", "image": "<image>"}, ...]     each with each image of its check-images.txt
#
# Environment (set by the workflow):
#   EVENT_NAME  push, pull_request or workflow_dispatch
#   SELECTED    workflow_dispatch: the folder of the dependency to build, or "all"
#   BEFORE_SHA  push: the commit before the push
#   BASE_SHA    pull_request: the commit of the base branch

set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
ROOT=deploy/dependencies
WORKFLOW=.github/workflows/build-dependencies.yml
NO_COMMIT=0000000000000000000000000000000000000000

DEPENDENCIES=""
for folder in "$ROOT"/*/; do
    name="$(basename "$folder")"
    if [ -f "$folder/build.sh" ] && [ -f "$folder/check.sh" ] && [ -f "$folder/check-images.txt" ]; then
        DEPENDENCIES="$DEPENDENCIES $name"
    else
        echo "::warning::$ROOT/$name has no build.sh, check.sh and check-images.txt: it is not built" >&2
    fi
done

# Which dependencies to build: "ALL", or the changed files to match
changed=""
case "${EVENT_NAME:-}" in
    workflow_dispatch)
        if [ "${SELECTED:-all}" = all ]; then
            changed=ALL
        elif [[ " $DEPENDENCIES " == *" $SELECTED "* ]]; then
            changed="$ROOT/${SELECTED}/"
        else
            echo "::error::'$SELECTED' is not a dependency of $ROOT/: choose one of${DEPENDENCIES}, or all" >&2
            exit 1
        fi
        ;;
    pull_request|push)
        if [ "$EVENT_NAME" = pull_request ]; then base="${BASE_SHA:-}"; else base="${BEFORE_SHA:-}"; fi
        if [ -n "$base" ] && [ "$base" != "$NO_COMMIT" ] && git cat-file -e "$base^{commit}" 2>/dev/null; then
            # A change to documentation alone builds nothing. A rename counts for both its folders.
            all_changed="$(git diff --name-only --no-renames "$base" HEAD)"
            changed="$(grep -vE '\.md$' <<< "$all_changed" || true)"
        else
            # A new branch, or a history that no longer has the old commit: everything is built
            changed=ALL
        fi
        ;;
    *)
        changed=ALL
        ;;
esac

dependencies=()
checks=()
for name in $DEPENDENCIES; do
    if [ "$changed" = ALL ] \
            || grep -qE "^($ROOT/$name/|$ROOT/[^/]+$|${WORKFLOW//./\\.}$)" <<< "$changed"; then
        dependencies+=("\"$name\"")
        images=0
        # A last line without a newline counts too; Windows line endings do not end up in the image name
        while read -r image _ || [ -n "$image" ]; do
            image="${image%$'\r'}"
            case "$image" in ''|'#'*) continue ;; esac
            checks+=("{\"dependency\": \"$name\", \"image\": \"$image\"}")
            images=$((images + 1))
        done < "$ROOT/$name/check-images.txt"
        if [ "$images" -eq 0 ]; then
            echo "::error::$ROOT/$name/check-images.txt has no image: a dependency is checked before it is published" >&2
            exit 1
        fi
    fi
done

join() { local IFS=,; echo "$*"; }
echo "dependencies=[$(join "${dependencies[@]+"${dependencies[@]}"}")]"
echo "checks=[$(join "${checks[@]+"${checks[@]}"}")]"
