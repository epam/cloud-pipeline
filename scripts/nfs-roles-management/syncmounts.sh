#!/bin/bash
# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
    jq -r '.payload[] | .fileShareMounts | select(.!=null)[] | "\(.id)|\(.regionId)|\(.mountRoot)|\(.mountType)|\(.mountOptions)"')"

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
    if [ "$mount_type" == "NFS" ] || [ "$mount_type" == "SMB" ] || [ "$mount_type" == "LUSTRE" ]; then

        # In a SMB paths we have the next structure : {mount_root_srv}/{mount_root_path} without any ':',
        # that is why we need to do such conditional processing
        mount_root_srv="$( [ ${mount_type} != "SMB" ] && echo "$mount_root" | cut -f1 -d":" || echo "$mount_root" | cut -f1 -d"/")"
        mount_root_path="/"
        if [[ "$mount_type" != "SMB" ]] && [[ "$mount_root" == *":"* ]]; then
            mount_root_path="$(echo "$mount_root" | cut -f2 -d":")"
        elif [[ "$mount_type" == "SMB" ]]; then
            mount_root_path="$(echo "$mount_root" | cut -f2 -d"/")"
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
        mount_protocol="nfs"
        remote_nfs_path="${mount_root_srv}:${mount_root_path}"
        if [ "$mount_type" == "SMB" ]; then
            mount_protocol="cifs"
            remote_nfs_path="//${mount_root_srv}/${mount_root_path}"
            region_cred_file="/root/.cloud/regioncreds/${region_id}"
            if [ ! -f ${region_cred_file} ]; then
                echo "[ERROR] Cred file for Azure region ${region_id}, not found! CIFS storage ${mount_root_srv}/${mount_root_path} won't be mounted."
                continue
            fi
            username=$(cat ${region_cred_file} | jq .storage_account)
            password=$(cat ${region_cred_file} | jq .storage_key)
            if [ "$mount_options_opt" ] && [ ! -z "$mount_options_opt" ]; then
              mount_options_opt="${mount_options_opt},username=${username:1:-1},password=${password:1:-1}"
            else
              mount_options_opt="-o username=${username:1:-1},password=${password:1:-1}"
            fi
        elif [ "$mount_type" == "LUSTRE" ]; then
          mount_protocol="lustre"
        fi

        mkdir -p "$mount_root_srv_dir"
        echo "mount -t ${mount_protocol} $remote_nfs_path $mount_root_srv_dir $mount_options_opt"
        timeout -s 9 $_MOUNT_TIMEOUT_SEC mount -t ${mount_protocol} "$remote_nfs_path" "$mount_root_srv_dir" $mount_options_opt
        if [ $? -ne 0 ]; then
            echo "[ERROR] Unable to mount $mount_root to $mount_root_srv_dir (id: ${mount_id}, type: ${mount_type}), skipping"
            continue
        fi
        echo "[DONE] $mount_root is mounted to $mount_root_srv_dir (id: ${mount_id}, type: ${mount_type})"
        mounted_dirs+=($(remove_trailing_slashes "$mount_root_srv_dir"))
    else
        echo "[ERROR] Unsupported mount type $mount_type is specified. $mount_root (id: $mount_id), skipping"
        continue
    fi
done <<< "$fs_mounts"
echo

storages="$(curl -sfk -H "Authorization: Bearer ${_API_TOKEN}" ${_API_URL%/}/datastorage/loadAll |    \
             jq -r '.payload[] |  select(.type!="NFS") | "\(.id)|\(.name)|\(.path)|\(.type)|\(.regionId)"')"

if [ $? -ne 0 ]; then
    echo "[ERROR] Cannot get list of the storages, exiting"
    exit 1
fi

while IFS='|' read -r id name path type region_id; do
     echo
     echo "[INFO] Staring processing: $mount_root (id: ${id}, name: ${name}, region: ${region_id} type: ${type}, path: ${path})"

     if [[ "${type}" == "AZ" ]]; then
         mount_root_srv_dir="${_MOUNT_ROOT}/AZ/${name}"
     elif [[ "${type}" == "S3" ]]; then
         mount_root_srv_dir="${_MOUNT_ROOT}/S3/${name}"
     elif [[ "${type}" == "GCP" ]]; then
         mount_root_srv_dir="${_MOUNT_ROOT}/GCP/${name}"
     else
         echo "[INFO] Storage with id: ${id} and path: ${path} is not a AZ/GCP/S3 storage, skipping."
         continue
     fi

     if mountpoint -q "$mount_root_srv_dir"; then
        echo "[DONE] $mount_root_srv_dir is already mounted, skipping"
        mounted_dirs+=($(remove_trailing_slashes "$mount_root_srv_dir"))
        continue
     fi

     mkdir -p ${mount_root_srv_dir}

     if [[ "${type}" == "AZ" ]]; then
         CP_TMP_DIR_PATH="/mnt/blobfusetmp/${path}"
         mkdir -p "$CP_TMP_DIR_PATH"
         region_cred_file="/root/.cloud/regioncreds/${region_id}"
         if [[ ! -f ${region_cred_file} ]]; then
            echo "[ERROR] Cred file for Azure region ${region_id}, not found!"
            continue
         fi
         storage_account=$(cat ${region_cred_file} | jq -r .storage_account)
         storage_key=$(cat ${region_cred_file} | jq -r .storage_key)
         export AZURE_STORAGE_ACCOUNT="${storage_account}"
         export AZURE_STORAGE_ACCESS_KEY="${storage_key}"
         blobfuse ${mount_root_srv_dir} --container-name=${path} --tmp-path=$CP_TMP_DIR_PATH
         mount_result=$?
     elif [[ "${type}" == "S3" ]] || [[ "${type}" == "GCP" ]]; then
         pipe storage mount ${mount_root_srv_dir} -b ${path} -t --mode 775 -w ${CP_PIPE_FUSE_TIMEOUT:-500} -o allow_other -l /var/log/fuse_${id}.log
         mount_result=$?
     else
         echo "[ERROR] Storage with id: ${id} and path: ${path} is not a AZ/GCP/S3 storage, skipping."
         continue
     fi



     if [[ $mount_result -ne 0 ]]; then
        echo "[ERROR] Unable to mount $mount_root to $mount_root_srv_dir (id: ${id}, type: ${type}), skipping"
        continue
     fi
     echo "[DONE] $mount_root is mounted to $mount_root_srv_dir (id: ${id}, type: ${type})"
     mounted_dirs+=($(remove_trailing_slashes "$mount_root_srv_dir"))
done <<< "$storages"
echo

echo "Cleaning outdated"
clean_outdated "$_MOUNT_ROOT" "true"
echo
