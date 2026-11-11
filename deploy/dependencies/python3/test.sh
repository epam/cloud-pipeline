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

# Tests publish.sh against a stub aws CLI, which records its calls and answers as told, and a scratch git
# history (older -> newer): a build of an older commit does not replace a newer one; the backup of the current
# distribution is taken once, never from a published build; an unclear answer of S3 or a wrong checksum
# publish nothing. The workflow runs it before the build. Needs git.
#
# Usage: test.sh

set -uo pipefail

PUBLISH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/publish.sh"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@localhost GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@localhost

mkdir -p "$WORK/bin" "$WORK/dist" "$WORK/repo"
git -C "$WORK/repo" init -q
git -C "$WORK/repo" commit -q --allow-empty -m older && OLDER="$(git -C "$WORK/repo" rev-parse HEAD)"
git -C "$WORK/repo" commit -q --allow-empty -m newer && NEWER="$(git -C "$WORK/repo" rev-parse HEAD)"
git clone -q --depth 1 "file://$WORK/repo" "$WORK/shallow"
UNKNOWN=1111111111111111111111111111111111111111

# The stub: AWS_STUB_INFO and AWS_STUB_BACKUP answer head-object (exists, missing or denied) for
# python3.12.BUILD-INFO.txt and python3.12.0.tar.gz; AWS_STUB_PUBLISHED is the commit of the published build
cat > "$WORK/bin/aws" <<'EOF'
#!/bin/bash
echo "aws $*" >> "$AWS_STUB_CALLS"
if [ "$1 $2" = "s3api head-object" ]; then
    case "$*" in
        *BUILD-INFO.txt*) answer="$AWS_STUB_INFO" ;;
        *python3.12.0.tar.gz*) answer="$AWS_STUB_BACKUP" ;;
        *) answer=exists ;;
    esac
    case "$answer" in
        exists) echo '{"ContentLength": 1}' ;;
        missing) echo 'An error occurred (404) when calling the HeadObject operation: Not Found' >&2; exit 254 ;;
        *) echo 'An error occurred (403) when calling the HeadObject operation: Forbidden' >&2; exit 254 ;;
    esac
elif [ "$1 $2" = "s3 cp" ] && [ "${*: -1}" = - ]; then
    printf 'Python 3.12 distribution for /usr/local/python3.12\ncommit: %s\n' "$AWS_STUB_PUBLISHED"
fi
EOF
chmod +x "$WORK/bin/aws"
echo distribution > "$WORK/dist/python3.12.tar.gz"
(cd "$WORK/dist" && sha256sum python3.12.tar.gz > python3.12.tar.gz.sha256)

TARGET="s3://cloud-pipeline-oss-builds/tools/python/3"
BACKUP="aws s3 cp --no-progress --copy-props metadata-directive $TARGET/python3.12.tar.gz $TARGET/python3.12.0.tar.gz"
UPLOAD="aws s3 cp --no-progress $WORK/dist/python3.12.tar.gz $TARGET/python3.12.tar.gz"
UPLOAD_SHA256="aws s3 cp --no-progress $WORK/dist/python3.12.tar.gz.sha256 $TARGET/python3.12.tar.gz.sha256"
UPLOAD_INFO="aws s3 cp --no-progress $WORK/dist/BUILD-INFO.txt $TARGET/python3.12.BUILD-INFO.txt"
failed=0
calls=""

# publish <case> <commit of this build> <info> <backup> <published commit> <expected exit code>
# GIT_DIR_OF_TEST: the history publish.sh compares commits in (default: the scratch history)
publish() {
    local case="$1" code
    printf 'Python 3.12 distribution for /usr/local/python3.12\ncommit: %s\n' "$2" > "$WORK/dist/BUILD-INFO.txt"
    : > "$WORK/calls"
    PATH="$WORK/bin:$PATH" AWS_STUB_CALLS="$WORK/calls" AWS_STUB_INFO="$3" AWS_STUB_BACKUP="$4" \
        AWS_STUB_PUBLISHED="$5" CP_PUBLISH_GIT_DIR="${GIT_DIR_OF_TEST:-$WORK/repo}" bash "$PUBLISH" "$WORK/dist" \
        > "$WORK/out" 2>&1
    code=$?
    calls="$(cat "$WORK/calls")"
    if [ "$code" -ne "$6" ]; then
        echo "[FAIL] $case: exit code $code, expected $6: $(tail -2 "$WORK/out")"
        failed=1
        return 1
    fi
}
# expect <case> <what> <command...>: the command succeeds
expect() {
    local case="$1" what="$2"
    shift 2
    if "$@"; then echo "[OK]   $case: $what"; else echo "[FAIL] $case: not $what"; failed=1; fi
}
called() { grep -qxF "$1" <<< "$calls"; }
not_called() { ! grep -qxF "$1" <<< "$calls"; }
backup_before_upload() {
    [ "$(grep -nxF "$BACKUP" <<< "$calls" | cut -d: -f1)" -lt "$(grep -nxF "$UPLOAD" <<< "$calls" | cut -d: -f1)" ]
}
info_and_sha256_published() { called "$UPLOAD_SHA256" && called "$UPLOAD_INFO"; }
warned() { grep -q "::warning::$1" "$WORK/out"; }
no_upload() { ! grep -q "s3 cp --no-progress" <<< "$calls"; }

if publish "first publication" "$NEWER" missing missing "" 0; then
    expect "first publication" "the current distribution is backed up" called "$BACKUP"
    expect "first publication" "the backup comes before the upload" backup_before_upload
    expect "first publication" "the checksum and the build info are published" info_and_sha256_published
fi
if publish "build of a newer commit" "$NEWER" exists exists "$OLDER" 0; then
    expect "build of a newer commit" "it replaces the older build" called "$UPLOAD"
    expect "build of a newer commit" "with its checksum and build info" info_and_sha256_published
    expect "build of a newer commit" "the backup is left as it is" not_called "$BACKUP"
fi
if publish "build of an older commit" "$OLDER" exists exists "$NEWER" 0; then
    expect "build of an older commit" "the newer build stays" no_upload
fi
if publish "same commit again" "$NEWER" exists exists "$NEWER" 0; then
    expect "same commit again" "it is published again" called "$UPLOAD"
fi
if publish "published commit not in the history" "$NEWER" exists exists "$UNKNOWN" 0; then
    expect "published commit not in the history" "it is replaced" called "$UPLOAD"
fi
if publish "backup there, no build info" "$NEWER" missing exists "" 0; then
    expect "backup there, no build info" "the backup is left as it is" not_called "$BACKUP"
    expect "backup there, no build info" "the distribution and its build info are published" info_and_sha256_published
fi
if publish "published build info without a commit" "$NEWER" exists exists "" 0; then
    expect "published build info without a commit" "it is replaced, with a warning" warned ".*has no commit"
    expect "published build info without a commit" "the distribution is published" called "$UPLOAD"
fi
if publish "commit of this build not in the history" "$UNKNOWN" exists exists "$OLDER" 1; then
    expect "commit of this build not in the history" "aws is not called" test -z "$calls"
fi
if GIT_DIR_OF_TEST="$WORK/shallow" publish "shallow history" "$NEWER" exists exists "$OLDER" 1; then
    expect "shallow history" "aws is not called" test -z "$calls"
fi
if publish "no backup, published build there" "$NEWER" exists missing "$OLDER" 0; then
    expect "no backup, published build there" "no backup is taken from the published build" not_called "$BACKUP"
    expect "no backup, published build there" "the distribution is published" called "$UPLOAD"
fi
if publish "unclear answer about the build info" "$NEWER" denied missing "" 1; then
    expect "unclear answer about the build info" "nothing is copied" no_upload
fi
if publish "unclear answer about the backup" "$NEWER" missing denied "" 1; then
    expect "unclear answer about the backup" "nothing is copied" no_upload
fi
echo corrupted >> "$WORK/dist/python3.12.tar.gz"
if publish "wrong checksum" "$NEWER" exists exists "$OLDER" 1; then
    expect "wrong checksum" "aws is not called" test -z "$calls"
fi

[ "$failed" -eq 0 ] && echo "==== passed" || echo "==== FAILED"
exit "$failed"
