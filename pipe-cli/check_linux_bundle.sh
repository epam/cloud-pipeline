#!/bin/bash

# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# Checks the Linux pipe CLI bundles, that build_linux.sh makes, in a clean container of the given image:
# - every ELF file of the one-folder bundle (pipe.tar.gz), the embedded pipe-fuse and pipe-omics included, finds
#   the shared libraries and the glibc symbol versions it needs, in the bundle or in the image
# - the executables of the one-folder bundle (pipe, pipe-fuse, pipe-omics) start and print their help
# - the one-file binary and the one-folder bundle print their version
# - the one-file binary unpacks itself into ~/.pipe/tmp, and removes its files there on exit; it falls back to the
#   default temporary folder, if it cannot create ~/.pipe/tmp
#
# Usage: check_linux_bundle.sh <dist dir> [docker image]
#   <dist dir>     - the folder with dist-file/pipe and dist-folder/pipe.tar.gz
#   [docker image] - the image to check in, centos:7 (glibc 2.17) by default

_CHECK_DOCKER_IMAGE="${2:-centos:7}"

if [ ! -f "$1/dist-file/pipe" ] || [ ! -f "$1/dist-folder/pipe.tar.gz" ]; then
    echo "[ERROR] No pipe bundles in $1: dist-file/pipe or dist-folder/pipe.tar.gz is missing"
    exit 1
fi
# docker run -v takes an absolute path only
_DIST_DIR="$(cd "$1" && pwd)"

echo "Checking the pipe bundles of $_DIST_DIR in $_CHECK_DOCKER_IMAGE"

# The bundles are x86_64 builds, so the check runs on linux/amd64 whatever the platform of the Docker host
docker pull --platform linux/amd64 "$_CHECK_DOCKER_IMAGE" &> /dev/null
docker run -i --rm \
           --platform linux/amd64 \
           -v "$_DIST_DIR":/dist:ro \
           "$_CHECK_DOCKER_IMAGE" \
           bash -s <<'EOL'
_check_dir=$(mktemp -d)
tar -xzf /dist/dist-folder/pipe.tar.gz -C "$_check_dir" || exit 1

_elf_count=0
_failed_count=0
_bundle_executables=()
while IFS= read -r -d '' _file; do
    [ "$(head -c 4 "$_file" | tr -d '\0')" == $'\x7fELF' ] || continue
    _elf_count=$((_elf_count + 1))
    # As the bootloader of a PyInstaller bundle does, look for the libraries in the _internal folder of the bundle
    # the file belongs to: pipe, or the pipe-fuse and pipe-omics bundles inside it. The other folders are found
    # by the RPATH of the libraries.
    # The executable of a bundle lies next to its _internal folder, the other files of the bundle lie in it.
    _bundle_lib_path=""
    if [ -d "$(dirname "$_file")/_internal" ]; then
        _bundle_lib_path="$(dirname "$_file")/_internal"
        # An executable that lost its execute permission fails to run below
        _bundle_executables+=("$_file")
    elif [[ "$_file" == */_internal/* ]]; then
        _bundle_lib_path="${_file%/_internal/*}/_internal"
    fi
    # ldd reports both a missing library (=> not found) and a missing glibc symbol version (version `GLIBC_2.28' not found)
    _missing=$(LD_LIBRARY_PATH="$_bundle_lib_path" ldd "$_file" 2>&1 | grep 'not found')
    if [ "$_missing" ]; then
        echo "[ERROR] ${_file#$_check_dir/}:"
        echo "$_missing"
        _failed_count=$((_failed_count + 1))
    fi
done < <(find "$_check_dir" -type f -print0)

echo "Checked $_elf_count ELF files of the one-folder bundle, $_failed_count failed"
if [ "$_elf_count" == 0 ] || [ "$_failed_count" != 0 ]; then
    exit 1
fi

# The bootloader of a bundle loads libpython and the modules at run time, which ldd does not see
for _executable in "${_bundle_executables[@]}"; do
    if ! _output=$(timeout 300 "$_executable" --help 2>&1); then
        echo "[ERROR] ${_executable#$_check_dir/} --help failed:"
        echo "$_output" | tail -20
        exit 1
    fi
done
echo "Ran ${#_bundle_executables[@]} executables of the one-folder bundle: ${_bundle_executables[*]#$_check_dir/}"
if [ "${#_bundle_executables[@]}" == 0 ]; then
    exit 1
fi

"$_check_dir/pipe/pipe" --version || exit 1
cp /dist/dist-file/pipe "$_check_dir/pipe-one-file" && "$_check_dir/pipe-one-file" --version || exit 1

_check_home=$(mktemp -d)
HOME="$_check_home" "$_check_dir/pipe-one-file" --version > /dev/null || exit 1
if [ ! -d "$_check_home/.pipe/tmp" ] || [ "$(ls -A "$_check_home/.pipe/tmp")" ]; then
    echo "[ERROR] The one-file binary did not unpack itself into ~/.pipe/tmp, or left its files there"
    exit 1
fi
# ~/.pipe keeps the configuration, so only ~/.pipe/tmp is made unusable: a file in its place
_check_home=$(mktemp -d)
mkdir "$_check_home/.pipe" && touch "$_check_home/.pipe/tmp"
if ! HOME="$_check_home" "$_check_dir/pipe-one-file" --version > /dev/null; then
    echo "[ERROR] The one-file binary does not fall back to the default temporary folder without ~/.pipe/tmp"
    exit 1
fi
echo "The one-file binary unpacks itself into ~/.pipe/tmp, or into the default temporary folder without it"
EOL
_CHECK_EXIT_CODE=$?

if [ "$_CHECK_EXIT_CODE" != 0 ]; then
    echo "[ERROR] The pipe bundles of $_DIST_DIR do not run in $_CHECK_DOCKER_IMAGE"
    exit "$_CHECK_EXIT_CODE"
fi
echo "The pipe bundles of $_DIST_DIR run in $_CHECK_DOCKER_IMAGE"
