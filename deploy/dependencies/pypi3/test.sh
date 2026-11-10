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

# Tests the scripts of this folder; the workflow runs it before the build:
#   - mirror.py, with test_mirror.py
#   - fetch_platform_python of platform-python.sh: a Python that is not published, or a wrong checksum, stops it
#   - publish.sh, against a stub aws CLI that keeps the bucket in a local folder: the new index loses no link,
#     a file of the bucket is never replaced, the files go first and simple/index.html last, the index is backed
#     up, and a failure publishes nothing
#   - workflows/pipe-common/test/test_pypi3_mirror_list.py: requirements.txt has every Linux pin of pipe-common,
#     in a python:3.12 container (it runs setup.py with Python 3.12)
# Needs python3, and Docker for the last one.
#
# Usage: test.sh

set -uo pipefail
export LC_ALL=C

RECIPE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_DIR="$(cd "$RECIPE_DIR/../../.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
failed=0

ok() { echo "[OK] $*"; }
ko() { echo "[FAIL] $*"; failed=1; }

echo "==== mirror.py"
if (cd "$RECIPE_DIR" && PYTHONDONTWRITEBYTECODE=1 python3 -m unittest -q test_mirror > "$WORK/unittest.log" 2>&1); then
    ok "test_mirror.py"
else
    cat "$WORK/unittest.log"
    ko "test_mirror.py"
fi

echo "==== fetch_platform_python"
mkdir -p "$WORK/python/published" "$WORK/python/out"
echo distribution > "$WORK/python/published/python3.12.tar.gz"
(cd "$WORK/python/published" && python3 -c "
import hashlib; d = open('python3.12.tar.gz', 'rb').read()
open('python3.12.tar.gz.sha256', 'w').write(hashlib.sha256(d).hexdigest() + '  python3.12.tar.gz\n')")
# fetch <url> -> output and exit code of fetch_platform_python
fetch() {
    (export CP_PYTHON3_DISTRO_URL="$1"; . "$RECIPE_DIR/platform-python.sh"; fetch_platform_python "$WORK/python/out") 2>&1
}
out="$(fetch "file://$WORK/python/published/python3.12.tar.gz")" && [ -f "$WORK/python/out/python3.12.tar.gz" ] \
    && ok "a published Python is fetched and checked" || ko "a published Python is fetched and checked: $out"
out="$(fetch "file://$WORK/python/missing/python3.12.tar.gz")"
[ $? -ne 0 ] && echo "$out" | grep -q 'Build and publish it first' \
    && ok "a Python that is not published stops it, asking to build and publish it first" \
    || ko "a Python that is not published stops it: $out"
echo tampered > "$WORK/python/published/python3.12.tar.gz"
out="$(fetch "file://$WORK/python/published/python3.12.tar.gz")"
[ $? -ne 0 ] && echo "$out" | grep -q 'does not match its .sha256' \
    && ok "a wrong checksum stops it" || ko "a wrong checksum stops it: $out"

echo "==== publish.sh"
# The stub: s3://<bucket>/<key> is $AWS_STUB_BUCKETS/<bucket>/<key>. Each write is logged as "WRITE <key>".
# AWS_STUB_FAIL=read|list fails that call. AWS_STUB_LIST_STYLE=pages lists one key per line with "None" lines,
# as the CLI prints several pages. AWS_STUB_HIDE=<file> leaves that file out of the listing: it was uploaded since
mkdir -p "$WORK/bin"
cat > "$WORK/bin/aws" <<'EOF'
#!/bin/bash
set -euo pipefail
echo "aws $*" >> "$AWS_STUB_CALLS"
local_path() { local p="${1#s3://}"; echo "$AWS_STUB_BUCKETS/$p"; }
args=() recursive=0 bucket="" prefix="" key="" body="" if_none_match=""
while [ $# -gt 0 ]; do
    case "$1" in
        --recursive) recursive=1 ;;
        --only-show-errors|--no-progress) ;;
        --content-type|--delimiter|--query|--output) shift ;;
        --bucket) bucket="$2"; shift ;;
        --prefix) prefix="$2"; shift ;;
        --key) key="$2"; shift ;;
        --body) body="$2"; shift ;;
        --if-none-match) if_none_match="$2"; shift ;;
        *) args+=("$1") ;;
    esac
    shift
done
if [ "${args[0]} ${args[1]}" = "s3api list-objects-v2" ]; then
    [ "${AWS_STUB_FAIL:-}" = list ] && { echo "An error occurred (AccessDenied)" >&2; exit 254; }
    keys="$(cd "$AWS_STUB_BUCKETS/$bucket" 2>/dev/null && find "${prefix%/}" -maxdepth 1 -type f 2>/dev/null \
        | grep -v "/${AWS_STUB_HIDE:-^}$" | sort)" || true
    if [ "${AWS_STUB_LIST_STYLE:-}" = pages ]; then
        printf '%s\nNone\n' $keys
    else
        echo "$keys" | tr '\n' '\t' | sed 's/\t$//'
        [ -n "$keys" ] && echo || echo None
    fi
    exit 0
fi
if [ "${args[0]} ${args[1]}" = "s3api put-object" ]; then
    target="$AWS_STUB_BUCKETS/$bucket/$key"
    if [ "$if_none_match" = '*' ] && [ -e "$target" ]; then
        echo "An error occurred (PreconditionFailed) when calling the PutObject operation" >&2
        exit 254
    fi
    mkdir -p "$(dirname "$target")"
    cp "$body" "$target"
    echo "WRITE $key" >> "$AWS_STUB_CALLS"
    exit 0
fi
[ "${args[0]} ${args[1]}" = "s3 cp" ] || { echo "stub: unexpected aws ${args[*]}" >&2; exit 2; }
src="${args[2]}" dst="${args[3]}"
case "$src" in
    s3://*)
        [ "${AWS_STUB_FAIL:-}" = read ] && { echo "An error occurred (AccessDenied)" >&2; exit 1; }
        mkdir -p "$dst"
        [ -d "$(local_path "$src")" ] && cp -R "$(local_path "$src")/." "$dst/"
        ;;
    *)
        if [ "$recursive" = 1 ]; then
            (cd "$src" && find . -type f | sed 's|^\./||' | sort) | while read -r f; do
                mkdir -p "$(dirname "$(local_path "$dst$f")")"
                cp "$src/$f" "$(local_path "$dst$f")"
                echo "WRITE ${dst#s3://*/}$f" >> "$AWS_STUB_CALLS"
            done
        else
            mkdir -p "$(dirname "$(local_path "$dst")")"
            cp "$src" "$(local_path "$dst")"
            echo "WRITE ${dst#s3://*/}" >> "$AWS_STUB_CALLS"
        fi
        ;;
esac
EOF
chmod +x "$WORK/bin/aws"
PYPI3=tools/python/pypi3
MIRROR="python3 -I $RECIPE_DIR/mirror.py"

# scenario: a bucket with b-2.0 and c-1.0 published; a build of a-1.0 (new) and b-2.0, merged with the index of then
setup() {
    rm -rf "$WORK/case" && mkdir -p "$WORK/case/buckets/cloud-pipeline-oss-builds/$PYPI3" "$WORK/case/build" "$WORK/case/none"
    BUCKET_DIR="$WORK/case/buckets/cloud-pipeline-oss-builds/$PYPI3"
    BUILD="$WORK/case/build"
    echo bucket > "$BUCKET_DIR/b-2.0.tar.gz"
    echo bucket > "$BUCKET_DIR/c-1.0-py3-none-any.whl"
    $MIRROR merge "$BUCKET_DIR/simple" "$BUCKET_DIR" > /dev/null
    echo build > "$BUILD/a-1.0-py3-none-any.whl"
    echo build > "$BUILD/b-2.0.tar.gz"
    $MIRROR merge "$BUILD/simple" "$BUILD" "$BUCKET_DIR/simple" > /dev/null
    : > "$WORK/case/calls"
}
publish() {
    PATH="$WORK/bin:$PATH" AWS_STUB_BUCKETS="$WORK/case/buckets" AWS_STUB_CALLS="$WORK/case/calls" \
        bash "$RECIPE_DIR/publish.sh" "$BUILD" > "$WORK/case/out" 2>&1
}
writes() { grep '^WRITE ' "$WORK/case/calls" | sed 's/^WRITE //'; }
links() { $MIRROR links "$BUCKET_DIR/simple" | sed 's|^[^/]*/||' | sort | tr '\n' ' '; }

setup
publish && rc=0 || rc=$?
[ "$rc" -eq 0 ] && [ "$(links)" = "a-1.0-py3-none-any.whl b-2.0.tar.gz c-1.0-py3-none-any.whl " ] \
    && ok "publishes the new file, and the index links it with every file linked before" \
    || ko "publishes the new file: rc=$rc, links $(links), $(cat "$WORK/case/out")"
[ "$(cat "$BUCKET_DIR/b-2.0.tar.gz")" = bucket ] && ! writes | grep -qx "$PYPI3/b-2.0.tar.gz" \
    && ok "a file the bucket has is not uploaded again" || ko "a file the bucket has is not uploaded again"
order="$(writes | sed -E "s|^$PYPI3/simple-backups/[^/]+/.*|backup|; s|^$PYPI3/simple/index.html$|root|; s|^$PYPI3/simple/.*|page|; s|^$PYPI3/[^/]*$|file|" | uniq | tr '\n' ' ')"
[ "$order" = "file backup page root " ] \
    && ok "order: files, backup of the index, package pages, simple/index.html" || ko "order: $order"
backup="$(ls -d "$BUCKET_DIR"/simple-backups/*/ 2>/dev/null | head -1)"
[ -n "$backup" ] && [ "$($MIRROR links "$backup" | tr '\n' ' ')" = "b/b-2.0.tar.gz c/c-1.0-py3-none-any.whl " ] \
    && ok "the backup is the index before the publication" || ko "the backup: $backup"

setup
echo bucket > "$BUCKET_DIR/d-1.0-py3-none-any.whl"
$MIRROR merge "$BUCKET_DIR/simple" "$BUCKET_DIR" > /dev/null
publish && echo "$(links)" | grep -q 'd-1.0-py3-none-any.whl' && echo "$(links)" | grep -q 'a-1.0-py3-none-any.whl' \
    && ok "a link published after the build stays" || ko "a link published after the build stays: $(links)"

setup
publish && : > "$WORK/case/calls" && publish && grep -q 'nothing to publish' "$WORK/case/out" && [ -z "$(writes)" ] \
    && ok "a second publication of the same build writes nothing" || ko "a second publication: $(cat "$WORK/case/out")"

# no_writes <case>: publish.sh fails and the bucket is untouched
no_writes() {
    local name="$1" rc
    publish && rc=0 || rc=$?
    if [ "$rc" -ne 0 ] && [ -z "$(writes)" ] && grep -q 'Nothing was published' "$WORK/case/out"; then
        ok "$name: fails, writes nothing"
    else
        ko "$name: rc=$rc, writes $(writes | tr '\n' ' '), $(cat "$WORK/case/out")"
    fi
}
setup; AWS_STUB_FAIL='read' no_writes "the index cannot be read"
setup; AWS_STUB_FAIL='list' no_writes "the bucket cannot be listed"
setup; rm -rf "$BUCKET_DIR/simple"; no_writes "no published index"
setup; rm "$BUILD/a-1.0-py3-none-any.whl"; no_writes "the build links a file that neither has"
setup; rm -rf "$BUILD/simple"; no_writes "the build has no index"

setup; rm "$BUCKET_DIR/simple/b/index.html"; mkdir -p "$BUCKET_DIR/simple/b"
printf '<html><body><a href="https://files.example.com/b-2.0.tar.gz">b-2.0.tar.gz</a></body></html>' \
    > "$BUCKET_DIR/simple/b/index.html"
no_writes "a published link that is not ../../<file>, which the merge would drop"

for value in 1 true; do
    setup
    CP_PYPI3_PUBLISH_DRY_RUN="$value" publish && [ -z "$(writes)" ] && grep -q 'a-1.0-py3-none-any.whl' "$WORK/case/out" \
        && ok "a dry run ($value) prints the plan and writes nothing" || ko "a dry run ($value): $(cat "$WORK/case/out")"
done

setup
AWS_STUB_LIST_STYLE=pages publish && [ "$(links)" = "a-1.0-py3-none-any.whl b-2.0.tar.gz c-1.0-py3-none-any.whl " ] \
    && ! writes | grep -qx "$PYPI3/b-2.0.tar.gz" \
    && ok "a listing of several pages, with None lines, reads the same" || ko "a listing of several pages: $(cat "$WORK/case/out")"

setup
AWS_STUB_HIDE=b-2.0.tar.gz publish && [ "$(cat "$BUCKET_DIR/b-2.0.tar.gz")" = bucket ] \
    && grep -q 'kept: b-2.0.tar.gz' "$WORK/case/out" && [ "$(links)" = "a-1.0-py3-none-any.whl b-2.0.tar.gz c-1.0-py3-none-any.whl " ] \
    && ok "a file uploaded since the listing is kept, and the publication goes on" \
    || ko "a file uploaded since the listing: $(cat "$WORK/case/out")"

echo "==== build.sh"
mkdir -p "$WORK/used" && echo old > "$WORK/used/six-1.15.0-py2.py3-none-any.whl"
out="$(bash "$RECIPE_DIR/build.sh" "$WORK/used" 2>&1)"
[ $? -ne 0 ] && echo "$out" | grep -q 'has package files already' && [ ! -d "$WORK/used/simple" ] \
    && ok "an output dir with package files stops it before anything is downloaded" || ko "an output dir with package files: $out"

echo "==== pipe-common pins in requirements.txt"
if docker run --rm -v "$REPO_DIR":/repo:ro -e PYTHONDONTWRITEBYTECODE=1 python:3.12-slim sh -c \
        'pip install -q --disable-pip-version-check --root-user-action=ignore pytest packaging setuptools \
         && cd /tmp && python -m pytest -q -p no:cacheprovider /repo/workflows/pipe-common/test/test_pypi3_mirror_list.py' \
        > "$WORK/pins.log" 2>&1; then
    ok "test_pypi3_mirror_list.py"
else
    cat "$WORK/pins.log"
    ko "test_pypi3_mirror_list.py"
fi

[ "$failed" -eq 0 ] && echo "All tests passed" || echo "Tests failed"
exit "$failed"
