#!/bin/bash
# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

function contains_element {
  local e match="$1"
  shift
  for e; do [[ "$e" == "$match" ]] && return 0; done
  return 1
}

function remove_trailing_slashes {
    echo $(realpath -s $1)
}

function clean_outdated {
    shopt -s nullglob
    local dir="$1"
    local check_children="$2"

    if [ ! -d "$dir" ]; then
        echo "$dir is not a directory, skipping"
        shopt -u nullglob
        return
    fi

    local item=
    for item in $dir/*; do
        item="$(remove_trailing_slashes $item)"
        if [ -f "$item" ]; then
            echo "$item file will be deleted from the mount root directory"
            \rm -f "$item"
            continue
        fi

        if contains_element "$item" "${mounted_dirs[@]}"; then
            continue
        fi
        echo "$item was not found in the mounted storages list"

        if mountpoint -q "$item"; then
            echo "$item is a mount point, unmounting"
            timeout -s 9 $_MOUNT_TIMEOUT_SEC umount "$item"
            if [ $? -ne 0 ]; then
                echo "Unable to unmount ${item}, skipping"
                continue
            fi
            
            echo "$item is unmounted, deleting directory"
            timeout -s 9 $_MOUNT_TIMEOUT_SEC \rm -r "$item"
            if [ $? -ne 0 ]; then
                echo "Unable to delete ${item}, skipping"
                continue
            fi
            continue
        fi

        if [ "$check_children" == "true" ]; then
            # Subdirectories are checked, as the GCP mounts always have the root directory, e.g. nfs://<srv>/<root>/. Which is not a case for AWS, e.g. nfs://<srv>
            echo "$item is NOT a mount point, checking sub-directories"
            clean_outdated "$item" "false"
            if [ -d "$item" ] && [ -z "$(ls -A $item)" ]; then
                echo "$item is empty after sub-directories check, deleting"
                timeout -s 9 $_MOUNT_TIMEOUT_SEC \rm -r "$item"
            fi
        else
            echo "$item is NOT a mount point and children check was NOT requested, deleting directory"
            timeout -s 9 $_MOUNT_TIMEOUT_SEC \rm -r "$item"
            if [ $? -ne 0 ]; then
                echo "Unable to delete ${item}, skipping"
                continue
            fi
        fi
    done
    shopt -u nullglob
}

_MOUNT_TIMEOUT_SEC=5

set -o pipefail

_API_URL="$1"
_API_TOKEN="$2"
_MOUNT_ROOT="$3"

if [ -z "$_API_URL" ] || [ -z "$_API_TOKEN" ] || [ -z "$_MOUNT_ROOT" ]; then
    echo "[ERROR] Not enough input parameters, exiting"
    exit 1
fi

if [ "$_MOUNT_ROOT" == "/" ]; then
    echo "Mount root directory shall not point to '/', exiting"
    exit 1
fi

if ! command -v "jq" >/dev/null 2>&1 ; then
    echo "[ERROR] 'jq' is not installed, exiting"
    exit 1
fi

fs_mounts="$(curl -sfk -H "Authorization: Bearer ${_API_TOKEN}" ${_API_URL%/}/cloud/region | \
    jq -r '.payload[] | .fileShareMounts | select(.!=null)[] | "\(.id)|\(.regionId)|\(.mountRoot)|\(.mountType)"')"

if [ $? -ne 0 ]; then
    echo "[ERROR] Cannot get list of the file shares, exiting"
    exit 1
fi

echo "Mounting storages to $_MOUNT_ROOT"
mounted_dirs=()
mkdir -p "$_MOUNT_ROOT"
while IFS='|' read -r mount_id region_id mount_root mount_type mount_options; do
    echo
    echo "[INFO] Staring processing: $mount_root (id: ${mount_id}, region: ${region_id} type: ${mount_type}, options: ${mount_options})"
    if [ "$mount_type" == "NFS" ]; then
        mount_root_srv="$(echo "$mount_root" | cut -f1 -d":")"
        mount_root_path="/"
        if [[ "$mount_root" == *":"* ]]; then
            mount_root_path="$(echo "$mount_root" | cut -f2 -d":")"
        fi
        
        mount_root_srv_dir="${_MOUNT_ROOT}/${mount_root_srv}/${mount_root_path}"
        if mountpoint -q "$mount_root_srv_dir"; then
            echo "[DONE] $mount_root_srv_dir is already mounted, skipping"
            mounted_dirs+=($(remove_trailing_slashes "$mount_root_srv_dir"))
            continue
        fi
        
        mount_options_opt=
        if [ "$mount_options" ] && [ "$mount_options" != "null" ]; then
            mount_options_opt="-o $mount_options"
        fi

        mkdir -p "$mount_root_srv_dir"
        timeout -s 9 $_MOUNT_TIMEOUT_SEC mount -t nfs "${mount_root_srv}:${mount_root_path}" "$mount_root_srv_dir" $mount_options_opt
        if [ $? -ne 0 ]; then
            echo "[ERROR] Unable to mount $mount_root to $mount_root_srv_dir (id: ${mount_id}, type: ${mount_type}), skipping"
            continue
        fi
        echo "[DONE] $mount_root is mounted to $mount_root_srv_dir (id: ${mount_id}, type: ${mount_type})"
        mounted_dirs+=($(remove_trailing_slashes "$mount_root_srv_dir"))
    # FIXME: Add SMB type handling
    else
        echo "[ERROR] Unsupported mount type $mount_type is specified. $mount_root (id: $mount_id), skipping"
        continue
    fi
done <<< "$fs_mounts"
echo

echo "Cleaning outdated"
clean_outdated "$_MOUNT_ROOT" "true"
echo
