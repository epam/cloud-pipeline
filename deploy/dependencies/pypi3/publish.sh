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

# Publishes a checked build of the pypi3 mirror to s3://cloud-pipeline-oss-builds/tools/python/pypi3/, which
# the runs, the node hosts and the platform images install from. Every run of pip there reads this index, so:
#   1. the index of the build is merged again with the one published now: a link published since the build stays
#   2. it publishes nothing when the new index would lose a link of the published one, or would link a file that
#      neither the bucket nor the build has
#   3. it uploads the files that the bucket lacks, each only if the bucket still lacks it then (a conditional
#      write): it replaces no file of the bucket
#   4. it copies the published index to simple-backups/<UTC time>/, then uploads the pages that changed: the
#      package pages first, simple/index.html last
# It deletes nothing. Needs the aws CLI and python3, s3:ListBucket on the bucket, and s3:GetObject and
# s3:PutObject on the prefix.
#
# Usage: publish.sh <output dir of build.sh>
# CP_PYPI3_PUBLISH_BUCKET, CP_PYPI3_PUBLISH_PREFIX: where to (cloud-pipeline-oss-builds, tools/python/pypi3)
# CP_PYPI3_PUBLISH_DRY_RUN=1 (any value but 0): reads the bucket and prints what it would upload, and writes nothing

set -euo pipefail
# comm needs its inputs sorted in the byte order that sort uses here
export LC_ALL=C

BUILD_DIR="$(cd "${1:?usage: $0 <output dir of build.sh>}" && pwd)"
RECIPE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUCKET="${CP_PYPI3_PUBLISH_BUCKET:-cloud-pipeline-oss-builds}"
PREFIX="${CP_PYPI3_PUBLISH_PREFIX:-tools/python/pypi3}"
PREFIX="${PREFIX%/}"
DRY_RUN="${CP_PYPI3_PUBLISH_DRY_RUN:-0}"
TARGET="s3://$BUCKET/$PREFIX"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT

die() {
    echo "[ERROR] $*. Nothing was published." >&2
    exit 1
}
index() {
    python3 -I "$RECIPE_DIR/mirror.py" "$@"
}
package_files() {
    find "$1" -maxdepth 1 -type f \( -name '*.whl' -o -name '*.tar.gz' -o -name '*.zip' \) -exec basename {} \; | sort -u
}

[ -f "$BUILD_DIR/simple/index.html" ] || die "$BUILD_DIR has no simple/index.html"

echo "Reading $TARGET/"
aws s3 cp --recursive --only-show-errors "$TARGET/simple/" "$WORK/published/" || die "cannot read $TARGET/simple/"
[ -f "$WORK/published/index.html" ] || die "$TARGET/simple/index.html does not exist: is the prefix right?"
aws s3api list-objects-v2 --bucket "$BUCKET" --prefix "$PREFIX/" --delimiter / \
    --query 'Contents[].Key' --output text > "$WORK/keys.txt" || die "cannot list $TARGET/"
tr '\t' '\n' < "$WORK/keys.txt" | while read -r key; do
    [ -n "$key" ] && [ "$key" != None ] && echo "${key#"$PREFIX"/}"
done | sort -u > "$WORK/bucket-files.txt" || true
package_files "$BUILD_DIR" > "$WORK/build-files.txt"

index merge "$WORK/simple" "$BUILD_DIR" "$WORK/published" "$BUILD_DIR/simple" > /dev/null || die "the merge failed"

index links "$WORK/published" | sort -u > "$WORK/links-published.txt"
index links "$WORK/simple" | sort -u > "$WORK/links-new.txt"
lost="$(comm -23 "$WORK/links-published.txt" "$WORK/links-new.txt")"
[ -z "$lost" ] || die "the new index would lose these links: $lost"
sed 's|^[^/]*/||' "$WORK/links-new.txt" | sort -u > "$WORK/linked.txt"
sort -u "$WORK/bucket-files.txt" "$WORK/build-files.txt" > "$WORK/available.txt"
dangling="$(comm -23 "$WORK/linked.txt" "$WORK/available.txt")"
[ -z "$dangling" ] || die "the new index would link files that neither the bucket nor the build has: $dangling"

comm -23 "$WORK/build-files.txt" "$WORK/bucket-files.txt" > "$WORK/upload-files.txt"
: > "$WORK/upload-pages.txt"
for page_dir in "$WORK"/simple/*/; do
    page="$(basename "$page_dir")"
    cmp -s "$page_dir/index.html" "$WORK/published/$page/index.html" || echo "$page" >> "$WORK/upload-pages.txt"
done
root_changed=0
cmp -s "$WORK/simple/index.html" "$WORK/published/index.html" || root_changed=1

if [ ! -s "$WORK/upload-files.txt" ] && [ ! -s "$WORK/upload-pages.txt" ] && [ "$root_changed" -eq 0 ]; then
    echo "The mirror has every file and link of this build: nothing to publish"
    exit 0
fi

echo "Files to upload ($(wc -l < "$WORK/upload-files.txt" | tr -d ' ')):"
sed 's/^/  /' "$WORK/upload-files.txt"
echo "Package pages to update ($(wc -l < "$WORK/upload-pages.txt" | tr -d ' ')), then simple/index.html: $root_changed"
sed 's/^/  /' "$WORK/upload-pages.txt"
if [ -n "$DRY_RUN" ] && [ "$DRY_RUN" != 0 ]; then
    echo "Dry run: nothing was written"
    exit 0
fi

# A file that another publication uploaded since the listing is kept: S3 refuses the write (PreconditionFailed)
while read -r file; do
    if error="$(aws s3api put-object --bucket "$BUCKET" --key "$PREFIX/$file" --body "$BUILD_DIR/$file"             --if-none-match '*' 2>&1 > /dev/null)"; then
        echo "upload: $file"
    elif echo "$error" | grep -q PreconditionFailed; then
        echo "kept: $file, which the bucket has now"
    else
        echo "$error" >&2
        echo "[ERROR] uploading $file failed. The index is unchanged: the files uploaded before it are not linked." >&2
        exit 1
    fi
done < "$WORK/upload-files.txt"

BACKUP="$TARGET/simple-backups/$(date -u +%Y%m%dT%H%M%SZ)"
aws s3 cp --recursive --only-show-errors --content-type text/html "$WORK/published/" "$BACKUP/"

while read -r page; do
    aws s3 cp --only-show-errors --content-type text/html "$WORK/simple/$page/index.html" \
        "$TARGET/simple/$page/index.html"
done < "$WORK/upload-pages.txt"
aws s3 cp --only-show-errors --content-type text/html "$WORK/simple/index.html" "$TARGET/simple/index.html"

echo "Published to $TARGET/. The index before it is in $BACKUP/. To put it back:"
echo "  aws s3 cp --recursive --content-type text/html $BACKUP/ $TARGET/simple/"
