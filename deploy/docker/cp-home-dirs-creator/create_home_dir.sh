#!/bin/bash

# Prerequisites:
# ADDR_FILE_SHARE: EFS address, e.g. fs-xxxxxxxx.efs.us-east-1.amazonaws.com
# ID_FILE_SHARE: fileshare id from the cloud region, e.g. 1
# ID_ROOT_FOLDER: ID of the root folder in CP Library where home dirs are created. e.g. 1

function verify_required_parameter {
    local param_name="$1"
    if [ -z "${!param_name}" ]; then
        echo "[ERROR] Required parameter $param_name is not set. Exiting."
        exit 128
    fi
}

function verify_optional_parameter {
    local param_name="$1"
    [ -n "${!param_name}" ]
}

function api_get {
    local uri="$1"
    curl -k -s -H "Authorization: Bearer $API_TOKEN" -H "Content-Type: application/json" "$uri"
}

function api_post {
    local uri="$1"
    local data="$2"
    curl -k -s -X POST -H "Authorization: Bearer $API_TOKEN" -H "Content-Type: application/json" \
        -d "$data" "$uri"
}

function api_put {
    local uri="$1"
    local data="$2"
    curl -k -s -X PUT -H "Authorization: Bearer $API_TOKEN" -H "Content-Type: application/json" \
        -d "$data" "$uri"
}

function find_storage_id_by_name {
    local name="$1"
    local response=$(api_get "${API}datastorage/list?folderId=${ID_ROOT_FOLDER}")
    echo "$response" | jq -r --arg n "$name" '( .payload // [] | if type == "array" then . else [.] end )[] | select(.name == $n) | .id // empty'
}

function grant_storage_owner {
    local storage_id="$1"
    local user_name_full="$2"
    api_post "${API}grant/owner?id=${storage_id}&aclClass=DATA_STORAGE&userName=${user_name_full}" ""
}

function set_user_default_storage {
    local user_id="$1"
    local storage_id="$2"
    api_put "${API}user/${user_id}" "{\"defaultStorageId\":${storage_id}}"
}

function set_storage_chmod_metadata {
    local storage_id="$1"
    local chmod_val="$2"
    api_post "${API}metadata/updateKeys" \
        "{\"entity\":{\"entityId\":\"$storage_id\",\"entityClass\":\"DATA_STORAGE\"},\"data\":{\"chmod\":{\"value\":\"$chmod_val\",\"type\":\"string\"}}}"
}

function verify_user_exists {
    local user_id="$1"
    local user_name_full="$2"
    local check_id
    check_id=$(api_get "${API}user?name=$user_name_full" | jq -r '.payload.id')
    [ "$check_id" = "$user_id" ]
}

function ensure_user_ssh_keys {
    local user_id="$1"
    local user_name="$2"
    local user_name_full="$3"
    local response=$(api_post "${API}metadata/load" "[ { \"entityId\": $user_id, \"entityClass\": \"PIPELINE_USER\" } ]")
    local ssh_priv ssh_pub
    ssh_priv=$(echo "$response" | jq -r '.payload[].data.ssh_prv.value // 0')
    ssh_pub=$(echo "$response" | jq -r '.payload[].data.ssh_pub.value // 0')

    if [ -z "$ssh_priv" ] || [ "$ssh_priv" = "0" ]; then
        echo "User $user_name does not have a private ssh key, generating"
        local tmp_dir=$(mktemp -d)
        if ! ssh-keygen -t rsa -f "$tmp_dir/id_rsa" -P "" -C "" 2>/dev/null; then
            echo "[ERROR] Cannot generate a private ssh key for $user_name"
            rm -rf "$tmp_dir"
            return 1
        fi
        ssh_priv=$(sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g' "$tmp_dir/id_rsa")
        ssh_pub=$(cat "$tmp_dir/id_rsa.pub")
        if ! api_post "${API}metadata/updateKeys" \
            "{\"entity\":{\"entityId\":\"$user_id\",\"entityClass\":\"PIPELINE_USER\"},\"data\":{\"ssh_prv\":{\"value\":\"$ssh_priv\"},\"ssh_pub\":{\"value\":\"$ssh_pub\"}}}" >/dev/null; then
            echo "[ERROR] Failed setting ssh key for user $user_name"
            rm -rf "$tmp_dir"
            return 1
        fi
        rm -rf "$tmp_dir"
        echo "-> SSH key is set"
    fi
    return 0
}

# Creates a FS datastorage
function create_file_storage {
    local name="$1"
    local path="$2"
    local mount_point="$3"
    local file_share_mount_id="$4"
    local response
    response=$(api_post "${API}datastorage/save?cloud=true" \
        "{\"parentFolderId\":${ID_ROOT_FOLDER},\"name\":\"${name}\",\"path\":\"${path}\",\"shared\":false,\"storagePolicy\":{\"versioningEnabled\":false},\"serviceType\":\"FILE_SHARE\",\"mountDisabled\":false,\"mountPoint\":\"${mount_point}\",\"mountOptions\":\"nolock\",\"fileShareMountId\":${file_share_mount_id}}")
    if [ "$(echo "$response" | jq -r '.status')" != "OK" ]; then
        echo "$response" | jq -r '.message'
        return 1
    fi
    echo "$response" | jq -r '.payload.id'
    return 0
}

# Mount NFS home, optionally seed .bashrc, unmount (for FS storages when running on a host with NFS client).
function create_nfs_home_dir_structure {
    local user_name="$1"
    local mount_point="/cloud-home/${user_name}"
    local nfs_source="${ADDR_FILE_SHARE}/${user_name}"

    echo "-> Creating dir structure for $user_name"
    if [ -z "$user_name" ]; then
        echo "[ERROR] user_name is empty"
        return 1
    fi

    umount "$mount_point" 2>/dev/null || true
    rm -rf "$mount_point"
    mkdir -p "$mount_point"
    if ! mount -t nfs "$nfs_source" "$mount_point" -o nolock; then
        echo "[ERROR] Failed to mount NFS $nfs_source -> $mount_point"
        rm -rf "$mount_point"
        return 1
    fi
    chmod "$CP_HOME_DIRS_SERVICE_MOUNT_CHMOD" "$mount_point"
    if [[ "${CP_HOME_DIRS_CREATE_BASHRC:-false}" == "true" ]]; then
        if [ ! -f "${mount_point}/.bashrc" ]; then
            \cp /etc/skel/.bashrc "${mount_point}/.bashrc"
        fi
    fi
    umount "$mount_point" 2>/dev/null || true
    rm -rf "$mount_point"
    echo "-> Initial nfs structure created"
    return 0
}

# Creates file home storage for users without default storage: grant → set default → chmod → NFS
function ensure_file_home_storage_for_user {
    local user_name="$1"
    local user_name_full="$2"
    local user_id="$3"
    local storage_id
    local fs_name="${CP_FS_HOME_STORAGE_PREFIX}${user_name}"

    echo "-> File datastorage (name: $fs_name)"
    storage_id=$(find_storage_id_by_name "$fs_name")
    if [ -z "$storage_id" ]; then
        echo "-> Creating FS datastorage"
        local created_storage_id=$(create_file_storage "$fs_name" "${ADDR_FILE_SHARE}/${user_name}" "/home/${user_name}" "$ID_FILE_SHARE" 2>&1)
        if [ $? -ne 0 ]; then
            echo "[ERROR] Create FS failed for $user_name: $created_storage_id"
            return 1
        fi
        storage_id="$created_storage_id"
        echo "-> File storage created: $storage_id"
    else
        echo "-> Using existing FS: $storage_id"
    fi

    if ! grant_storage_owner "$storage_id" "$user_name_full" >/dev/null; then
        echo "[ERROR] Failed to grant OWNER for $user_name to FS: $storage_id"
        return 1
    fi
    echo "-> File datastorage permissions granted"

    local set_default_response=$(set_user_default_storage "$user_id" "$storage_id")
    if ! echo "$set_default_response" | jq -e '.status == "OK"' >/dev/null 2>&1; then
        echo "[ERROR] Failed setting default storage $storage_id for $user_name"
        return 1
    fi
    echo "-> Default storage set"

    if ! set_storage_chmod_metadata "$storage_id" "$CP_HOME_DIRS_SERVICE_MOUNT_CHMOD" >/dev/null; then
        echo "[ERROR] Failed setting chmod metadata for $storage_id"
    else
        echo "-> Chmod attribute is set"
    fi

    if ! create_nfs_home_dir_structure "$user_name"; then
        return 1
    fi
    return 0
}

function create_object_storage {
    local name="$1"
    local path="$2"
    local response=$(api_post "${API}datastorage/save?cloud=true&skipPolicy=false" \
        "{\"parentFolderId\":${ID_ROOT_FOLDER},\"name\":\"${name}\",\"path\":\"${path}\",\"shared\":false,\"storagePolicy\":{\"versioningEnabled\":true},\"serviceType\":\"OBJECT_STORAGE\",\"mountDisabled\":false,\"regionId\":1,\"sensitive\":false}")
    if [ "$(echo "$response" | jq -r '.status')" != "OK" ]; then
        echo "$response" | jq -r '.message'
        return 1
    fi
    echo "$response" | jq -r '.payload.id'
    return 0
}

function ensure_object_home_storage_for_user {
    local user_name="$1"
    local user_name_full="$2"
    local user_name_lower="$3"
    local s3_name="${CP_FS_HOME_STORAGE_PREFIX}${user_name}.${CP_FS_HOME_STORAGE_OBJECT_TYPE}"
    local prefix_lower=$(echo "$CP_FS_HOME_STORAGE_PREFIX" | tr '[:upper:]' '[:lower:]' | sed 's/\./-/g' | sed 's/^-//;s/-$//')
    local object_type_lower=$(echo "$CP_FS_HOME_STORAGE_OBJECT_TYPE" | tr '[:upper:]' '[:lower:]')
    if [ -n "$prefix_lower" ]; then
        s3_path="${prefix_lower}.${user_name_lower}.${object_type_lower}"
    else
        s3_path="${user_name_lower}.${object_type_lower}"
    fi

    echo "-> Object datastorage (name: $s3_name)"
    local s3_id=$(find_storage_id_by_name "$s3_name")
    if [ -z "$s3_id" ]; then
        echo "-> Creating object datastorage"
        local create_err
        create_err=$(create_object_storage "$s3_name" "$s3_path" 2>&1)
        if [ $? -ne 0 ]; then
            echo "[ERROR] Create object storage failed for $user_name: $create_err"
            return 1
        fi
        s3_id="$create_err"
        echo "-> Object storage created: $s3_id"
    else
        echo "-> Using existing object storage: $s3_id"
    fi

    if ! grant_storage_owner "$s3_id" "$user_name_full" >/dev/null; then
        echo "[ERROR] Failed to grant OWNER for $user_name to object storage: $s3_id"
        return 1
    fi
    echo "-> Object datastorage permissions granted"
    return 0
}

function create_fs_quota_for_storage {
    local storage_id="$1"
    local owner_email="$2"
    local notifications_value="{\\\"notifications\\\":[{\\\"type\\\":\\\"GB\\\",\\\"actions\\\":[\\\"EMAIL\\\"],\\\"value\\\":\\\"$CP_FS_QUOTAS_VOLUME_THRESHOLD_GB_DISABLE_MOUNT\\\"},{\\\"type\\\":\\\"GB\\\",\\\"actions\\\":[\\\"EMAIL\\\"],\\\"value\\\":\\\"$CP_FS_QUOTAS_VOLUME_THRESHOLD_GB_READ_ONLY\\\"}],\\\"recipients\\\":[{\\\"principal\\\":true,\\\"name\\\":\\\"${owner_email}\\\"}]}"
    api_post "${API}metadata/updateKeys" \
        "{\"entity\":{\"entityId\":\"$storage_id\",\"entityClass\":\"DATA_STORAGE\"},\"data\":{\"fs_notifications\":{\"value\":\"$notifications_value\",\"type\":\"string\"}}}"
}


function apply_fs_quotas {
    local response_users="$1"
    echo "$response_users" | jq -c '.payload[]' | while read -r user_info; do
        local uid=$(echo "$user_info" | jq -r '.id|tostring')
        if [[ " ${list_service_accounts[*]} " =~ " ${uid} " ]]; then
            continue
        fi
        local default_storage_id=$(echo "$user_info" | jq -r '.defaultStorageId')
        [ -z "$default_storage_id" ] || [ "$default_storage_id" = "null" ] && continue
        local load_response=$(api_post "${API}metadata/load" "[{\"entityId\":\"$default_storage_id\",\"entityClass\":\"DATA_STORAGE\"}]")
        if [ "$(echo "$load_response" | jq -r '.status')" != "OK" ]; then
            echo "[WARN] Failed to get metadata for data storage $default_storage_id."
            continue
        fi
        local owner_email=$(echo "$user_info" | jq -r '.attributes.Email' | grep -v "^null$" | tr '[:lower:]' '[:upper:]')
        [ -z "$owner_email" ] && continue
        local existing=$(echo "$load_response" | jq -r '.payload[].data.fs_notifications.value // empty')
        if [ -n "$existing" ]; then
            echo "FS quotas already defined for $default_storage_id"
            continue
        fi
        echo "FS quotas not defined for $owner_email. Creating..."
        create_fs_quota_for_storage "$default_storage_id" "$owner_email"
        echo "-> FS quotas for $default_storage_id set for $owner_email"
    done
}

# Users who already have a default storage: only update default to FS home to update permission settings in API side
function update_default_fs_storage_for_users_with_default {
    local response_users="$1"
    if [[ "${CP_FS_HOME_STORAGE_ENABLE}" != "true" ]]; then
        return 0
    fi
    if ! verify_optional_parameter "ID_FILE_SHARE" || ! verify_optional_parameter "ADDR_FILE_SHARE"; then
        return 0
    fi

    echo "# Updating default FS storage (users who already had a default storage)"
    echo "$response_users" | jq -c '.payload[] | select(.defaultStorageId != null)' | while read -r row; do
        local uid user_name_full user_name fs_name storage_id
        uid=$(echo "$row" | jq -r '.id|tostring')
        user_name_full=$(echo "$row" | jq -r '.userName')
        user_name="${user_name_full%@*}"
        if [[ " ${list_service_accounts[*]} " =~ " ${uid} " ]]; then
            continue
        fi
        fs_name="${CP_FS_HOME_STORAGE_PREFIX}${user_name}"
        storage_id=$(find_storage_id_by_name "$fs_name")
        if [ -z "$storage_id" ]; then
            echo "[WARN] No FS home storage '$fs_name' for $user_name — skip default update"
            continue
        fi
        local set_default_response=$(set_user_default_storage "$uid" "$storage_id")
        if ! echo "$set_default_response" | jq -e '.status == "OK"' >/dev/null 2>&1; then
            echo "[ERROR] Failed to update default storage for $user_name (storage $storage_id)"
            continue
        fi
        echo "-> Default storage set to FS home ($storage_id) for $user_name"
    done
}

function process_user {
    local user_info="$1"
    local user_id user_name_full user_name user_name_lower
    user_id=$(echo "$user_info" | cut -f1 -d',')
    user_name_full=$(echo "$user_info" | cut -f2 -d',')
    user_name="${user_name_full%@*}"
    user_name_lower=$(echo "$user_name" | tr '[:upper:]' '[:lower:]')

    if [[ " ${list_service_accounts[*]} " =~ " ${user_id} " ]]; then
        echo "Skipping service account $user_name"
        return 0
    fi

    echo "# Processing $user_name"
    echo "-> Checking if $user_name exists and is valid"
    if ! verify_user_exists "$user_id" "$user_name_full"; then
        echo "[ERROR] Cannot find $user_name or name contains invalid characters"
        return 1
    fi
    echo "-> User $user_name verified: $user_id"

    echo "-> Checking ssh key for user $user_name"
    if ! ensure_user_ssh_keys "$user_id" "$user_name" "$user_name_full"; then
        return 1
    fi

    if [[ "${CP_FS_HOME_STORAGE_ENABLE}" == "true" ]] && verify_optional_parameter "ID_FILE_SHARE" && verify_optional_parameter "ADDR_FILE_SHARE"; then
        if ! ensure_file_home_storage_for_user "$user_name" "$user_name_full" "$user_id"; then
            return 1
        fi
    fi

    if [[ "${CP_CREATE_OBJECT_STORAGE}" == "true" ]]; then
        if ! ensure_object_home_storage_for_user "$user_name" "$user_name_full" "$user_name_lower"; then
            return 1
        fi
    fi
    return 0
}

# Required
verify_required_parameter "ID_ROOT_FOLDER"
verify_required_parameter "API"
verify_required_parameter "API_TOKEN"

# Defaults
CP_HOME_DIRS_SERVICE_ACCOUNTS="${CP_HOME_DIRS_SERVICE_ACCOUNTS:-}"
list_service_accounts=($CP_HOME_DIRS_SERVICE_ACCOUNTS)
CP_HOME_DIRS_SERVICE_MOUNT_CHMOD="${CP_HOME_DIRS_SERVICE_MOUNT_CHMOD:-755}"
CP_APPLY_FS_QUOTAS="${CP_APPLY_FS_QUOTAS:-false}"
CP_FS_QUOTAS_VOLUME_THRESHOLD_GB_DISABLE_MOUNT="${CP_FS_QUOTAS_VOLUME_THRESHOLD_GB_DISABLE_MOUNT:-250}"
CP_FS_QUOTAS_VOLUME_THRESHOLD_GB_READ_ONLY="${CP_FS_QUOTAS_VOLUME_THRESHOLD_GB_READ_ONLY:-300}"
CP_CREATE_OBJECT_STORAGE="${CP_CREATE_OBJECT_STORAGE:-false}"
CP_FS_HOME_STORAGE_PREFIX="${CP_FS_HOME_STORAGE_PREFIX:-"HOME."}"
CP_FS_HOME_STORAGE_OBJECT_TYPE="${CP_FS_HOME_STORAGE_OBJECT_TYPE:-S3}"
CP_FS_HOME_STORAGE_ENABLE="${CP_FS_HOME_STORAGE_ENABLE:-true}"
CP_HOME_DIRS_CREATE_BASHRC="${CP_HOME_DIRS_CREATE_BASHRC:-false}"

if [[ "${CP_FS_HOME_STORAGE_ENABLE}" == "true" ]]; then
    if ! verify_optional_parameter "ID_FILE_SHARE" || ! verify_optional_parameter "ADDR_FILE_SHARE"; then
        echo "[ERROR] CP_FS_HOME_STORAGE_ENABLE is true but ID_FILE_SHARE and/or ADDR_FILE_SHARE are not set. FS home storage will be skipped. Exiting..."
        exit 1
    fi
fi

response_users=$(api_get "${API}users")
response_users_without_storage=$(echo "$response_users" | jq -r '.payload[] | select(.defaultStorageId == null) | (.id|tostring)+","+(.userName | sub(" ";"_"))')

echo "# Statistics"
echo "-> Users without default storage: $(echo "$response_users_without_storage" | wc -l)"
echo "-> Service accounts (will be skipped): ${#list_service_accounts[@]}"
echo ""

for user_info in $response_users_without_storage; do
    process_user "$user_info" || true
done

update_default_fs_storage_for_users_with_default "$response_users"

if [[ "${CP_APPLY_FS_QUOTAS}" == "true" ]]; then
    apply_fs_quotas "$response_users"
fi

echo "Finish to synchronize user's home directories"
