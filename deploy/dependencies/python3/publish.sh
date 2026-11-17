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

# Publishes a checked Python 3.12 distribution where the runs, the node hosts and the platform images download
# it: python3.12.tar.gz in s3://cloud-pipeline-oss-builds/tools/python/3/, with its .sha256 and
# python3.12.BUILD-INFO.txt (the commit and the versions it was built from).
#
# - It does not replace a build of a newer commit: the commit of the published build must be an ancestor of
#   the commit of this one (or the same), in the full git history of this repository. A published build of a
#   commit that is not in it, or without a commit, is replaced, with a warning.
# - The first publication keeps the file it replaces as python3.12.0.tar.gz (CPython 3.12.0 with OpenSSL
#   1.1.1w), so it can be put back. The backup is taken only while no published build is there.
#
# Needs the aws CLI with credentials: s3:ListBucket on the bucket (so that a missing file reads as missing),
# s3:GetObject and s3:PutObject on the prefix.
#
# Usage: publish.sh <folder with python3.12.tar.gz>
# CP_PYTHON3_PUBLISH_BUCKET and CP_PYTHON3_PUBLISH_PREFIX override the target, CP_PUBLISH_GIT_DIR the repository.

set -euo pipefail

DIST_DIR="$(cd "${1:?usage: $0 <folder with python3.12.tar.gz>}" && pwd)"
BUCKET="${CP_PYTHON3_PUBLISH_BUCKET:-cloud-pipeline-oss-builds}"
PREFIX="${CP_PYTHON3_PUBLISH_PREFIX:-tools/python/3}"
GIT_DIR_OF_REPO="${CP_PUBLISH_GIT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")" && git rev-parse --show-toplevel)}"
TARGET="s3://$BUCKET/$PREFIX"
INFO_KEY="$PREFIX/python3.12.BUILD-INFO.txt"
BACKUP_KEY="$PREFIX/python3.12.0.tar.gz"

(cd "$DIST_DIR" && sha256sum -c python3.12.tar.gz.sha256)
COMMIT="$(sed -n 's/^commit: //p' "$DIST_DIR/BUILD-INFO.txt")"
[ -n "$COMMIT" ] || { echo "[ERROR] no commit in $DIST_DIR/BUILD-INFO.txt" >&2; exit 1; }
# Without the commit of this build, or with a shallow history, an older build could not be told from a newer one
if ! git -C "$GIT_DIR_OF_REPO" cat-file -e "$COMMIT^{commit}" 2>/dev/null; then
    echo "[ERROR] the commit of this build, $COMMIT, is not in the history of $GIT_DIR_OF_REPO" >&2
    exit 1
fi
if [ "$(git -C "$GIT_DIR_OF_REPO" rev-parse --is-shallow-repository)" != false ]; then
    echo "[ERROR] $GIT_DIR_OF_REPO has a shallow history: the published build cannot be compared with this one" >&2
    exit 1
fi

# Prints "exists" or "missing" for a key; stops on any other answer, as a guess could lose the backup
state_of() {
    local answer
    if answer="$(aws s3api head-object --bucket "$BUCKET" --key "$1" 2>&1)"; then
        echo exists
    elif grep -qE '\(404\)|Not Found' <<< "$answer"; then
        echo missing
    else
        echo "[ERROR] cannot check s3://$BUCKET/$1: $answer" >&2
        exit 1
    fi
}

info_state="$(state_of "$INFO_KEY")"
if [ "$info_state" = exists ]; then
    published="$(aws s3 cp --quiet "s3://$BUCKET/$INFO_KEY" - | sed -n 's/^commit: //p')"
    if [ -z "$published" ]; then
        echo "::warning::s3://$BUCKET/$INFO_KEY has no commit: replacing the build it describes"
    elif [ "$published" != "$COMMIT" ]; then
        set +e
        git -C "$GIT_DIR_OF_REPO" merge-base --is-ancestor "$published" "$COMMIT" 2>/dev/null
        ancestry=$?
        set -e
        case "$ancestry" in
            0) echo "Replacing the build of $published, an ancestor of $COMMIT" ;;
            1) echo "::notice::not published: the published build of $published is not older than $COMMIT"
               exit 0 ;;
            *) echo "::warning::the published build of $published is not in the history of this repository (as this commit is): replacing it" ;;
        esac
    fi
fi

backup_state="$(state_of "$BACKUP_KEY")"
if [ "$backup_state" = exists ]; then
    echo "Backup s3://$BUCKET/$BACKUP_KEY is there already"
elif [ "$info_state" = exists ]; then
    echo "::warning::no backup s3://$BUCKET/$BACKUP_KEY, and the current file is a published build: none is taken"
else
    echo "Backup of the current distribution to s3://$BUCKET/$BACKUP_KEY"
    aws s3 cp --no-progress --copy-props metadata-directive "$TARGET/python3.12.tar.gz" "s3://$BUCKET/$BACKUP_KEY"
fi

aws s3 cp --no-progress "$DIST_DIR/python3.12.tar.gz" "$TARGET/python3.12.tar.gz"
aws s3 cp --no-progress "$DIST_DIR/python3.12.tar.gz.sha256" "$TARGET/python3.12.tar.gz.sha256"
aws s3 cp --no-progress "$DIST_DIR/BUILD-INFO.txt" "$TARGET/python3.12.BUILD-INFO.txt"
echo "Published $TARGET/python3.12.tar.gz:"
cat "$DIST_DIR/BUILD-INFO.txt"
