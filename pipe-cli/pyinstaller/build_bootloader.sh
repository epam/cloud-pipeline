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

# Rebuilds the Linux bootloader of the installed PyInstaller with runtime-tmpdir.patch.
#
# A one-file bundle unpacks itself into its runtime-tmpdir (--runtime-tmpdir), ~/.pipe/tmp for the pipe CLI.
# PyInstaller takes that folder literally on Linux, and does not create it. The patched bootloader expands "~"
# and the environment variables in it and creates it, as the bootloader of the earlier pipe CLI builds did.
# If the folder cannot be used, it falls back to the default temporary folder ($TMPDIR, /tmp).
#
# It runs in the Linux builder image (build_linux.sh, mount/build_mount.sh), which has gcc, patch and
# zlib-devel. The PyInstaller sources of the installed version are downloaded from PyPI.

set -e

_PATCH_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/runtime-tmpdir.patch"
_PYINSTALLER_VERSION="$(python3 -c 'import PyInstaller; print(PyInstaller.__version__)')"
_PYINSTALLER_PLATFORM="$(python3 -c 'import PyInstaller; print(PyInstaller.PLATFORM)')"
_PYINSTALLER_BOOTLOADER_DIR="$(python3 -c 'import os, PyInstaller; print(os.path.dirname(PyInstaller.__file__))')/bootloader/$_PYINSTALLER_PLATFORM"

_SOURCES_DIR="$(mktemp -d)"
trap 'rm -rf "$_SOURCES_DIR"' EXIT

echo "Rebuilding the $_PYINSTALLER_PLATFORM bootloader of PyInstaller $_PYINSTALLER_VERSION with $_PATCH_FILE"
python3 -m pip download --disable-pip-version-check -q --no-deps --no-binary :all: \
        -d "$_SOURCES_DIR" "pyinstaller==$_PYINSTALLER_VERSION"
tar -xzf "$_SOURCES_DIR/pyinstaller-$_PYINSTALLER_VERSION.tar.gz" -C "$_SOURCES_DIR"
cd "$_SOURCES_DIR/pyinstaller-$_PYINSTALLER_VERSION"
patch -p1 < "$_PATCH_FILE"

cd bootloader
python3 ./waf all > "$_SOURCES_DIR/waf.log" 2>&1 || { tail -50 "$_SOURCES_DIR/waf.log"; exit 1; }

for _bootloader in run run_d; do
    cp "../PyInstaller/bootloader/$_PYINSTALLER_PLATFORM/$_bootloader" "$_PYINSTALLER_BOOTLOADER_DIR/$_bootloader"
done
echo "Installed the rebuilt bootloaders into $_PYINSTALLER_BOOTLOADER_DIR"
