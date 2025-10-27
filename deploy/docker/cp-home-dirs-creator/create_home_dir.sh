#!/bin/bash

# Prerequisites:
# ADDR_FILE_SHARE: EFS address, e.g. fs-xxxxxxxx.efs.us-east-1.amazonaws.com
# ID_FILE_SHARE: fileshare id from the cloud region, e.g. 1
# ID_ROOT_FOLDER: ID of the root folder in CP Library where home dirs are created. e.g. 1

function verify_required_parameter {
    _PARAM_NAME=$1
    if [ -z "${!_PARAM_NAME}" ]; then
       echo "[ERROR] Required parameter $_PARAM_NAME is not set. Exiting."
       exit 128
    fi
}

verify_required_parameter "ID_ROOT_FOLDER"
verify_required_parameter "ID_FILE_SHARE"
verify_required_parameter "ADDR_FILE_SHARE"

api_list_users_uri="${API}users"
response_users_without_storage=$(curl -k -s -H "Authorization: Bearer $API_TOKEN" $api_list_users_uri | jq -r '.payload[] | select(.defaultStorageId == null) | (.id|tostring)+","+(.userName | sub(" ";"_"))')
CP_HOME_DIRS_SERVICE_ACCOUNTS="${CP_HOME_DIRS_SERVICE_ACCOUNTS:-""}"
list_service_accounts=($CP_HOME_DIRS_SERVICE_ACCOUNTS)

echo "# Statistics"
echo "-> Users without storages: $(echo "$response_users_without_storage" | wc -l)"
echo "-> Service accounts (will be skipped): ${#list_service_accounts[@]}"
echo ""

for user_info in $response_users_without_storage; do
    user_id=$(echo $user_info | cut -f1 -d',')
    user_name_full=$(echo $user_info | cut -f2 -d',')
    user_name=${user_name_full%@*}
    user_name_lower=$(echo "$user_name" | tr '[:upper:]' '[:lower:]')

    if [[ " ${list_service_accounts[@]} " =~ " ${user_id} " ]]; then
        echo "Skipping service account $user_name"
        continue
    fi
    echo "# Processing $user_name"

    echo "-> Checking if $user_name exists and is valid"
    api_check_user_uri="${API}user?name=$user_name_full"
    check_user_id=$(curl  --header "Content-Type: application/json" \
                            -s -k  \
                            -H "Authorization: Bearer $API_TOKEN" \
                            $api_check_user_uri | jq -r '.payload.id')

    if [ "$check_user_id" != "$user_id" ]; then
        echo "[ERROR] Cannot find $user_name or a name contains invalid characters"
        continue
    fi
    echo "-> User $user_name verified: $user_id"

    echo "-> Creating File datastorage"
    api_create_storage_uri="${API}datastorage/save?cloud=true"
    request_create_storage="{\"parentFolderId\":${ID_ROOT_FOLDER},\"name\":\"HOME.${user_name}\",\"path\":\"${ADDR_FILE_SHARE}:/${user_name}\",\"shared\":false,\"storagePolicy\":{\"versioningEnabled\":false},\"serviceType\":\"FILE_SHARE\",\"mountPoint\":\"/home/${user_name}\",\"fileShareMountId\":${ID_FILE_SHARE}$TOOLS_TO_MOUNT_JSON}"
    response_create_storage=$(curl  --header "Content-Type: application/json" \
                                    --request POST \
                                    --data "$request_create_storage" \
                                    -s -k  \
                                    -H "Authorization: Bearer $API_TOKEN" \
                                    $api_create_storage_uri)
    if [ $? -ne 0 ]; then
        echo "[ERROR] Create file storage request has failed for $user_name"
        continue
    fi
   
    storage_create_status=$(echo $response_create_storage | jq -r '.status')
    if [ "$storage_create_status" != "OK" ]; then
        echo "[ERROR] Create File storage request has failed for $user_name"
        echo "$(echo $response_create_storage | jq -r '.message')"
        continue
    fi  

    storage_id=$(echo $response_create_storage | jq -r '.payload.id')
    echo "-> File storage created: $storage_id"
    echo "-> Granting File datastorage permissions to $user_name"
    api_grant_owner_uri="${API}grant/owner?id=${storage_id}&aclClass=DATA_STORAGE&userName=${user_name_full}"
    response_grant_owner=$(curl --header "Content-Type: application/json" \
                            --request POST \
                            -s -k  \
                            -H "Authorization: Bearer $API_TOKEN" \
                            $api_grant_owner_uri)
    if [ $? -ne 0 ]; then
        echo "[ERROR] Failed to grant OWNER access for $user_name to File data storage: $storage_id"
        continue
    fi
    echo "-> File datastorage permissions granted"
    echo "-> Assigning $storage_id to $user_name"
    api_set_default_storage_uri="${API}user/${user_id}"
    request_set_default_storage="{\"defaultStorageId\":$storage_id}"
    response_set_default_storage=$(curl --header "Content-Type: application/json" \
                                    --request PUT \
                                    --data "$request_set_default_storage" \
                                    -s -k  \
                                    -H "Authorization: Bearer $API_TOKEN" \
                                    $api_set_default_storage_uri)
    if [ $? -ne 0 ]; then
        echo "[ERROR] Failed setting $storage_id as a default to $user_name"
        continue
    fi
    echo "-> Default storage assigned"
    echo "-> Creating dir structure for $user_name"
    if [ "$user_name" ]; then
        umount /cloud-home/${user_name}
        rm -rf /cloud-home/${user_name}
        mkdir -p /cloud-home/${user_name}
        mount -t nfs ${ADDR_FILE_SHARE}:/${user_name} /cloud-home/${user_name}
        mkdir -p /cloud-home/${user_name}/.cora/micromamba/envs
        chown 1000:1000 /cloud-home/${user_name} -R
        chmod 775 /cloud-home/${user_name} -R
        umount /cloud-home/${user_name}
        rm -rf /cloud-home/${user_name}
        echo "-> Dir structure created"
    else
        echo "[ERROR] user_name is empty"
        continue
    fi

    echo "-> Creating Object datastorage"
    api_create_s3_storage_uri="${API}datastorage/save?cloud=true&skipPolicy=false"
    request_create_s3_storage="{\"parentFolderId\":${ID_ROOT_FOLDER},\"name\":\"HOME.${user_name}.S3\",\"path\":\"home-${user_name_lower}-s3\",\"shared\":false,\"storagePolicy\":{\"versioningEnabled\":true},\"serviceType\":\"OBJECT_STORAGE\",\"mountDisabled\":false,\"regionId\":1, \"sensitive\":false}"
    response_create_s3_storage=$(curl  --header "Content-Type: application/json" \
                                    --request POST \
                                    --data "$request_create_s3_storage" \
                                    -s -k  \
                                    -H "Authorization: Bearer $API_TOKEN" \
                                    $api_create_s3_storage_uri)
    if [ $? -ne 0 ]; then
        echo "[ERROR] Create Object storage request has failed for $user_name"
        continue
    fi

    storage_create_s3_status=$(echo $response_create_s3_storage | jq -r '.status')
    if [ "$storage_create_status" != "OK" ]; then
        echo "[ERROR] Create Object storage request has failed for $user_name"
        echo "$(echo $response_create_s3_storage | jq -r '.message')"
        continue
    fi  

    s3_storage_id=$(echo $response_create_s3_storage | jq -r '.payload.id')
    echo "-> Object storage created: $s3_storage_id"
    echo "-> Granting Object datastorage permissions to $user_name"
    api_grant_owner_uri="${API}grant/owner?id=${s3_storage_id}&aclClass=DATA_STORAGE&userName=${user_name_full}"
    response_grant_owner=$(curl --header "Content-Type: application/json" \
                            --request POST \
                            -s -k  \
                            -H "Authorization: Bearer $API_TOKEN" \
                            $api_grant_owner_uri)
    if [ $? -ne 0 ]; then
        echo "[ERROR] Failed to grant OWNER access for $user_name to Object data storage: $storage_id"
        continue
    fi
    echo "-> Object datastorage permissions granted"

done

echo "Finish to synchronize user's home directories"
