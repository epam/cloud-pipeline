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

function check_api_response_status {
    local response_json="$1"
    local response_status=$(echo "$response_json" | jq -r ".status")
    local result=0
    if [ "$response_status" == "ERROR" ] || [[ "$response_status" == "40"* ]]; then
        result=1
    fi
    return $result
}

function call_api {
    local api_endpoint="$1"
    local jwt_token="$2"
    local payload="$3"
    local is_file="$4"

    local api_url="https://$CP_API_SRV_INTERNAL_HOST:$CP_API_SRV_INTERNAL_PORT/pipeline/restapi"
    local response=""
    if [ "$is_file" ]; then
        response=$(curl -X POST -k -s -H "Authorization: Bearer $jwt_token" -F "file=@$payload" "${api_url}${api_endpoint}")
    else
        if [ "$payload" ]; then
            response=$(curl -X POST -k -s -H 'Content-Type: application/json' -H "Authorization: Bearer $jwt_token" -d "$payload" "${api_url}${api_endpoint}")
        else
            response=$(curl -X GET -k -s -H "Authorization: Bearer $jwt_token" "${api_url}${api_endpoint}")
        fi
    fi
    echo "$response"
    check_api_response_status "$response"
    return $?
}

function api_set_entity_attribute {
    local entity_id="$1"
    local entity_class="$2"
    local attr_name="$3"
    local attr_value="$4"

    local payload="{}"
read -r -d '' payload <<-EOF
{
    "entity": {
        "entityId":"$entity_id",
        "entityClass":"$entity_class"
    },
    "data": {
        "$attr_name": {
            "value":"$attr_value",
            "type":"string"
        }
    }
}
EOF

    call_api_set_entity_attribute_response=$(call_api "/metadata/update" "$CP_API_JWT_ADMIN" "$payload")
    call_api_set_entity_attribute_result=$?
    if [ $call_api_set_entity_attribute_result -ne 0 ]; then
        print_err "Error occured while setting attribute $attr_name of the $entity_class entity:"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_set_entity_attribute_response"
        echo "========"
    else
        print_ok "Attribute $attr_name of the $entity_class entity ($entity_id) is set to \"$attr_value\""
    fi
    return $call_api_set_entity_attribute_result
}

function api_entity_grant {
    local entity_id="$1"
    local entity_class="$2"
    local entity_mask="$3"
    local entity_principal="$4"
    local entity_user="$5"

    local payload="{}"
read -r -d '' payload <<-EOF
{
    "aclClass":"$entity_class",
    "id":$entity_id,
    "mask":$entity_mask,
    "principal":$entity_principal,
    "userName":"$entity_user"}
EOF

    call_api_entity_grant_response=$(call_api "/grant" "$CP_API_JWT_ADMIN" "$payload")
    call_api_entity_grant_result=$?
    if [ $call_api_entity_grant_result -ne 0 ]; then
        print_err "Error occured while granting $entity_user permissions ($entity_mask) to $entity_id ($entity_class) entity:"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_entity_grant_response"
        echo "========"
    else
        print_ok "Permissions ($entity_mask) to $entity_id ($entity_class) entity are granted for $entity_user"
    fi
    return $call_api_entity_grant_result
}

function api_register_fileshare {
    local region_id="$1"
    local mount_point="$2"
    local fs_type="$3"
    local fs_options="$4"

read -r -d '' payload <<-EOF
{
    "regionId":$region_id,
    "mountRoot":"$mount_point",
    "mountType":"$fs_type", 
    "mountOptions":"$fs_options"
}
EOF

    call_api_api_register_fileshare_response=$(call_api "/filesharemount" "$CP_API_JWT_ADMIN" "$payload")
    call_api_api_register_fileshare_result=$?
    if [ $call_api_api_register_fileshare_result -ne 0 ]; then
        print_err "Error occured while registering fileshare $mount_point:"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_api_register_fileshare_response"
        echo "========"
    else
        print_ok "Fileshare $mount_point is registered"
    fi
    return $call_api_api_register_fileshare_result
}

function api_release_pipeline {
    local pipeline_id="$1"
    local pipeline_commit="$2"
    local pipeline_version_name="$3"

    local payload="{}"
read -r -d '' payload <<-EOF
{
    "pipelineId":$pipeline_id,
    "commit":"$pipeline_commit",
    "versionName":"$pipeline_version_name"}
EOF

    call_api_release_pipeline_response=$(call_api "/pipeline/version/register" "$CP_API_JWT_ADMIN" "$payload")
    call_api_release_pipeline_result=$?
    if [ $call_api_release_pipeline_result -ne 0 ]; then
        print_err "Error occured while releasing a pipeline $pipeline_id ($pipeline_commit):"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_release_pipeline_response"
        echo "========"
    else
        print_ok "Pipeline $pipeline_id ($pipeline_commit) is released with tag $pipeline_version_name"
        call_api_release_pipeline_result=0
    fi
    return $call_api_release_pipeline_result
}

function api_create_pipeline {
    local pipeline_name="$1"
    local pipeline_description="$2"
    local pipeline_parent_id="$3"

    if [ "$pipeline_parent_id" ]; then
        local folder_parent_key_value=", \"parentFolderId\": $pipeline_parent_id"
    fi

    local payload="{}"
read -r -d '' payload <<-EOF
{
    "name":"$pipeline_name",
    "description":"$pipeline_description" ${folder_parent_key_value}
}
EOF

    call_api_create_pipeline_response=$(call_api "/pipeline/register" "$CP_API_JWT_ADMIN" "$payload")
    call_api_create_pipeline_result=$?
    if [ $call_api_create_pipeline_result -ne 0 ]; then
        print_err "Error occured while creating a pipeline $pipeline_name:"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_create_pipeline_response"
        echo "========"
    else
        local pipeline_id=$(echo "$call_api_create_pipeline_response" | jq -r ".payload.id")
        if [ "$pipeline_id" ] && [ "$pipeline_id" != "null" ]; then
            print_ok "Pipeline $pipeline_name is created with ID $pipeline_id"
            call_api_create_pipeline_result=0
        else
            print_err "Unable to get id from the pipeline creation response"
            call_api_create_pipeline_result=1
        fi
    fi
    return $call_api_create_pipeline_result
}

function api_create_folder {
    local folder_name="$1"
    local folder_parent_id="$2"

    if [ "$folder_parent_id" ]; then
        local folder_parent_key_value=", \"parentId\": $folder_parent_id"
    fi

    local payload="{}"
read -r -d '' payload <<-EOF
{
    "name": "$folder_name" ${folder_parent_key_value}
}
EOF

    call_api_create_folder_response=$(call_api "/folder/register" "$CP_API_JWT_ADMIN" "$payload")
    call_api_create_folder_result=$?
    if [ $call_api_create_folder_result -ne 0 ]; then
        print_err "Error occured while creating a folder $folder_name:"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_create_folder_response"
        echo "========"
    else
        local folder_id=$(echo "$call_api_create_folder_response" | jq -r ".payload.id")
        if [ "$folder_id" ] && [ "$folder_id" != "null" ]; then
            print_ok "Folder $folder_name is created with ID $folder_id"
            call_api_create_folder_result=0
        else
            print_err "Unable to get id from the folder creation response"
            call_api_create_folder_result=1
        fi
    fi
    return $call_api_create_folder_result
}

function api_create_folder_path {
    local folder_path="$1"

    if [ -z "$folder_path" ]; then
        return 1
    fi
    local path_items=""
    IFS="/" read -ra path_items <<< "$folder_path"
    
    local current_folder_path=""
    local current_folder_id=""
    local parent_folder_id=""
    for path_item in "${path_items[@]}"; do
        current_folder_path="${current_folder_path}/${path_item}"
        current_folder_path=${current_folder_path#/}    # Remove leading slash, if any
        
        current_folder_id=$(api_get_entity_id "$current_folder_path" "folder")
        if [ $? -eq 0 ] && [ "$current_folder_id" ]; then
            print_ok "Folder $current_folder_path already exists, skipping creation"
            parent_folder_id=$current_folder_id
            continue
        fi

        api_create_folder "$path_item" "$parent_folder_id"
        if [ $? -ne 0 ]; then
            print_warn "Unable to create a folder $path_item with parent id \"$parent_folder_id\""
            return 1
        fi

        parent_folder_id=$(api_get_entity_id "$current_folder_path" "folder")
        if [ $? -ne 0 ] && [ -z "$parent_folder_id" ]; then
            print_warn "Unable to get id of the created folder $current_folder_path"
            return 1
        fi

    done

    print_ok "Folder path $folder_path created"

    return 0
}

function api_get_entity_id {
    local entity_name="$1"
    local entity_type="$2"
    if [ -z "$entity_type" ]; then
        return 1
    fi

    # Convert entity_type to lower case, as otherwise api call will fail
    entity_type="$(echo "$entity_type" | tr '[:upper:]' '[:lower:]')"

    local entity_json=$(call_api "/$entity_type/find?id=$entity_name" "$CP_API_JWT_ADMIN")
    local entity_id=$(echo "$entity_json" | jq -r ".payload.id")
    if [ "$entity_id" ] && [ "$entity_id" != "null" ]; then
        echo "$entity_id"
        return 0
    else
        return 1
    fi
}

function api_get_docker_registry_id {
    local docker_path="$1"
    local docker_registries_json=$(call_api "/entities?identifier=$docker_path&aclClass=DOCKER_REGISTRY" "$CP_API_JWT_ADMIN")
    local docker_id=$(echo "$docker_registries_json" | jq -r ".payload.id")
    if [ "$docker_id" ] && [ "$docker_id" != "null" ]; then
        echo "$docker_id"
        return 0
    else
        return 1
    fi
}

function api_get_cluster_instance_details {
    local instance_type="$1"
    local region_id="$2"

    local get_cluster_instance_details_json=$(call_api "/cluster/instance/loadAll?regionId=$region_id" "$CP_API_JWT_ADMIN")
    local get_cluster_instance_details_result="$(echo "$get_cluster_instance_details_json" | jq -r ".payload[] | select(.name == \"$instance_type\")")"
    if [ "$get_cluster_instance_details_result" ] && [ "$get_cluster_instance_details_result" != "null" ]; then
        echo "$get_cluster_instance_details_result"
        return 0
    else
        return 1
    fi
}

function api_get_role_id {
    local role_name="$1"
    local role_json=$(call_api "/role/loadAll" "$CP_API_JWT_ADMIN")
    local role_id=$(echo "$role_json" | jq -r ".payload[] | select(.name == \"$role_name\") | .id")
    if [ "$role_id" ] && [ "$role_id" != "null" ]; then
        echo "$role_id"
        return 0
    else
        return 1
    fi        
}

            
function api_register_user {
    local username="$1"
    local roles_csv="$2"

    local payload="{}"
read -r -d '' payload <<-EOF
{
    "userName": "$username",
    "roleIds": [ $roles_csv ]
}
EOF

    call_api_register_user_response=$(call_api "/user" "$CP_API_JWT_ADMIN" "$payload")
    call_api_register_user_result=$?
    if [ $call_api_register_user_result -ne 0 ]; then
        print_err "Error occured while creating a user $username:"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_register_user_response"
        echo "========"
    else
        print_ok "User $username is registered in API Service with roles $roles_csv"
    fi
    return $call_api_register_user_result
}


function api_register_docker_registry {
    local docker_role_grant="${1:-ROLE_USER}"

     local docker_path=$CP_DOCKER_INTERNAL_HOST:$CP_DOCKER_INTERNAL_PORT
     local docker_externalUrl=$CP_DOCKER_EXTERNAL_HOST:$CP_DOCKER_EXTERNAL_PORT
     local docker_caCert=$CP_DOCKER_CERT_DIR/docker-public-cert.pem
     local api_caCert=$CP_API_SRV_CERT_DIR/ssl-public-cert.pem
     local docker_securityScanEnabled="true"
     local docker_description=${CP_DOCKER_DEFAULT_NAME:-"Default registry"}
     local docker_pipelineAuth="true"
     local docker_userName="$CP_DEFAULT_ADMIN_NAME"
     local docker_password="$CP_API_JWT_ADMIN"
     # api + docker registry certificates are added as trusted (api is required to pipeline auth)
     local public_certificate=$(cat $docker_caCert $api_caCert | awk 'NF {sub(/\r/, ""); printf "%s\\n",$0;}')
     local payload="{    
                        \"path\": \"$docker_path\", 
                        \"description\": \"$docker_description\",
                        \"caCert\": \"$public_certificate\",
                        \"externalUrl\":\"$docker_externalUrl\",
                        \"securityScanEnabled\":$docker_securityScanEnabled,
                        \"userName\":\"$docker_userName\",
                        \"password\":\"$docker_password\",
                        \"pipelineAuth\": $docker_pipelineAuth
                    }"

    local docker_id=$(api_get_docker_registry_id "$docker_path")
    if [ "$docker_id" ]; then
        print_warn "Docker registry \"$docker_path\" with id \"$docker_id\" already exists, it will NOT be deleted and a new registry will NOT be registered"
        export CP_DOCKER_REGISTRY_ID=$docker_id
        return 0
    fi

    call_api_register_response=$(call_api "/dockerRegistry/register" "$CP_API_JWT_ADMIN" "$payload")
    call_api_register_result=$?
    if [ $call_api_register_result -ne 0 ]; then
        print_err "Error occured while registering a docker registry ($docker_path):"
        echo "$call_api_register_response"
    else
        docker_id=$(api_get_docker_registry_id "$docker_path")
        export CP_DOCKER_REGISTRY_ID=$docker_id
        print_ok "Docker registry added with id \"${docker_id}\""

        api_entity_grant "$docker_id" \
                         "DOCKER_REGISTRY" \
                         "25" \
                         "false" \
                         "$docker_role_grant"

        call_api_grant_registry_result=$?
        if [ $call_api_grant_registry_result -ne 0 ]; then
            print_err "Error occured while granting permissions for $docker_role_grant to $docker_path docker registry (id: $docker_id):"
            return 1
        else
            print_ok "$docker_role_grant was granted access to region $docker_path (id: $docker_id)"
        fi
    fi
    return $call_api_register_result
}

function api_preference_drop_array {
    unset __PREFERENCES_ARRAY_CURRENT__
    export __PREFERENCES_ARRAY_CURRENT__=""
}

function api_preference_get_array {
    echo "$__PREFERENCES_ARRAY_CURRENT__"
}

function api_preference_append_array {
    local pref_payload="$1"
    if [ -z "$pref_payload" ]; then
        return
    fi

    delimiter=""
    if [ "$__PREFERENCES_ARRAY_CURRENT__" ]; then
        delimiter=","
    fi

    __PREFERENCES_ARRAY_CURRENT__="${__PREFERENCES_ARRAY_CURRENT__}${delimiter}${pref_payload}"
}

function api_preference_get_templated {
    local pref_name="$1"
    local pref_value="$2"
    local pref_visible="$3"

cat <<EOF
{    
"name": "$pref_name",
"value": "$pref_value",
"visible": "$pref_visible"
}
EOF
}

function api_set_preference {
    local pref_name="$1"
    local pref_value="$2"
    local pref_visible="$3"

    if [ -z "$pref_value" ]; then
        payload="$pref_name"
        pref_name="(array)"
        pref_value="(array)"
    else
        payload=$(api_preference_get_templated "$pref_name" "$pref_value" "$pref_visible")
    fi

    payload="[ $payload ]"

    print_info "Setting API preference \"$pref_name\" with value \"$pref_value\""
    call_api_set_pref_response=$(call_api "/preferences" "$CP_API_JWT_ADMIN" "$payload")
    call_api_set_pref_result=$?
    if [ $call_api_set_pref_result -ne 0 ]; then
        print_err "Error occured while setting API preference \"$pref_name\""
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_set_pref_response"
        echo "========"
    else
        print_ok "API preference \"$pref_name\" is set"
    fi
    return $call_api_set_pref_result
}

function api_register_gitlab {
    local gitlab_root_token="$1"

    api_preference_drop_array
    api_preference_append_array "$(api_preference_get_templated "git.external.url"   "https://$CP_GITLAB_EXTERNAL_HOST:$CP_GITLAB_EXTERNAL_PORT" "true")"
    api_preference_append_array "$(api_preference_get_templated "git.user.name"      "$GITLAB_ROOT_USER"                                         "false")"
    api_preference_append_array "$(api_preference_get_templated "git.token"          "$gitlab_root_token"                                        "false")"
    api_preference_append_array "$(api_preference_get_templated "git.user.id"        "1"                                                         "false")"
    api_preference_append_array "$(api_preference_get_templated "git.host"           "https://$CP_GITLAB_INTERNAL_HOST:$CP_GITLAB_INTERNAL_PORT" "true")"
    api_set_preference "$(api_preference_get_array)"
    api_preference_drop_array
}

function api_find_docker_image {
    local docker_image_name="$1"

    local docker_image_json=$(call_api "/tool/load?image=$docker_image_name" "$CP_API_JWT_ADMIN")
    local docker_image_id=$(echo "$docker_image_json" | jq -r ".payload.id")
    if [ "$docker_image_id" ] && [ "$docker_image_id" != "null" ]; then
        echo "$docker_image_id"
        return 0
    else
        return 1
    fi
}

function api_register_docker_image {
    local registry_id="$1"
    local registry_path="$2"
    local image_name="$3"
    local disk_size="$4"
    local instance_type="$5"
    local default_command="$6"
    local short_description="$7"
    local full_description="$8"
    local endpoints="$9"
    if [ -z "$endpoints" ]; then
        endpoints="[]"
    fi

    if [ "$short_description" == "NA" ]; then
        short_description=""
    fi
    if [ "$full_description" == "NA" ]; then
        full_description=""
    fi

    local image_name_no_version="$(echo $image_name | cut -d: -f1)"
    local instance_type_json=""
    if [ "$instance_type" ] && [ "$instance_type" != "NA" ]; then
        instance_type_json=", \"instanceType\": \"$instance_type\""
    else
        print_warn "Instance type for $image_name is not set (actual value: \"$instance_type\"). Image will be registered without a default instance type"
    fi

    local payload="{}"
read -r -d '' payload <<-EOF
{
    "image": "$image_name_no_version",
    "registry":"$registry_path",
    "registryId":$registry_id,
    "shortDescription":"$short_description",
    "description": "$full_description",
    "disk": $disk_size,
    "endpoints": $endpoints,
    "defaultCommand": "$default_command" $instance_type_json
}
EOF

    call_api_register_image_response=$(call_api "/tool/update" "$CP_API_JWT_ADMIN" "$payload")
    call_api_register_image_result=$?
    if [ $call_api_register_image_result -ne 0 ]; then
        print_err "Error occured while registering a docker image ($image_name):"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_register_image_response"
        echo "========"
    else
        print_ok "Docker image $image_name added"
    fi
    return $call_api_register_image_result
}

function api_set_docker_image_icon {
    local docker_image_id="$1"
    local docker_image_icon="$2"

    if [ ! -f "$docker_image_icon" ]; then
        print_err "Icon does not exist at \"$docker_image_icon\" for docker image id \"$docker_image_id\""
        return 1
    fi

    call_api_set_icon_response=$(call_api "/tool/$docker_image_id/icon" "$CP_API_JWT_ADMIN" "$docker_image_icon" "file")
    call_api_set_icon_result=$?
    if [ $call_api_register_image_result -ne 0 ]; then
        print_err "Error occured while setting an icon ($docker_image_icon) for a docker image id ($docker_image_id):"
        echo "$call_api_set_icon_response"
    else
        print_ok "Docker image $image_name added"
    fi
    return $call_api_set_icon_result
}

function api_get_region_id {
    local region_name="$1"
    local regions_json=$(call_api "/entities?identifier=$region_name&aclClass=CLOUD_REGION" "$CP_API_JWT_ADMIN")
    local region_id=$(echo "$regions_json" | jq -r ".payload.id")
    if [ "$region_id" ] && [ "$region_id" != "null" ]; then
        echo "$region_id"
        return 0
    else
        return 1
    fi
}

function api_register_region {
    local region_name="${1:-$CP_CLOUD_REGION_ID}"
    local region_role_grant="${2:-ROLE_USER}"

    # TODO: move all "get smth id" to a single method with name and class options
    local region_id=$(api_get_region_id "$region_name")
    if [ "$region_id" ]; then
        print_warn "Cloud region \"$region_name\" with id \"$region_id\" already exists, it will NOT be deleted and a new region will NOT be added"
        export CP_CLOUD_REGION_INTERNAL_ID=$region_id
        return 0
    fi

    local cors_rules="$(get_file_based_preference storage.cors.policy other $CP_CLOUD_PLATFORM escape)"
    if [ $? -ne 0 ] || [ -z "$cors_rules" ]; then
        print_err "CORS rules cannot be retrieved for $CP_CLOUD_PLATFORM cloud provider, you will have to specify them manually via GUI/API"
        cors_rules="[]"
    fi

    if [ "$CP_CLOUD_REGION_FILE_STORAGE_HOSTS" ]; then
        local region_fs_targets=""
        IFS=', ' read -r -a region_fs_targets <<< "$CP_CLOUD_REGION_FILE_STORAGE_HOSTS"
    fi

    if [ "$CP_CLOUD_PLATFORM" == "$CP_AWS" ]; then
        if [ -z "$CP_AWS_KMS_ARN" ]; then
            print_err "Default encryption key for AWS KMS service is not defined, refusing to proceed with the Cloud region configuration (it shall be specified via CP_AWS_KMS_ARN parameter)"
            return 1
        fi
        local encryption_key_arn="$CP_AWS_KMS_ARN"
        local encryption_key_id=$(id_from_arn $encryption_key_arn)

read -r -d '' payload <<-EOF
{
    "regionId":"$region_name",
    "provider":"AWS",
    "name":"$region_name",
    "default":true,
    "sshKeyName":"$CP_PREF_CLUSTER_SSH_KEY_NAME",
    "tempCredentialsRole":"$CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE",
    "versioningEnabled":true,
    "backupDuration":${CP_PREF_STORAGE_BACKUP_DURATION:-20},
    "kmsKeyId":"$encryption_key_id",
    "kmsKeyArn":"$encryption_key_arn",
    "corsRules":"$cors_rules"
}
EOF
    elif [ "$CP_CLOUD_PLATFORM" == "$CP_AZURE" ]; then
        local azure_meter_names_json="$(get_file_based_preference azure.meter.names other $CP_AZURE)"
        if [ $? -ne 0 ] || [ -z "$azure_meter_names_json" ]; then
            print_err "Azure meter names cannot be retrieved, but this is required to setup a region, you will have to specify them manually via GUI/API"
            return 1
        fi

        local azure_meter_name="$(echo "$azure_meter_names_json" | jq -r ".${CP_CLOUD_REGION_ID}")"
        if [ -z "$azure_meter_name" ] || [ "$azure_meter_name" == "null" ]; then
            print_err "Unable to get azure meter name for region $CP_CLOUD_REGION_ID, but this is required to setup a region, you will have to specify them manually via GUI/API"
            return 1
        fi
read -r -d '' payload <<-EOF
{
    "regionId":"$region_name",
    "provider":"AZURE",
    "name":"$region_name",
    "default":true,
    "storageAccount": "$CP_AZURE_STORAGE_ACCOUNT",
    "storageAccountKey": "$CP_AZURE_STORAGE_KEY",
    "resourceGroup": "$CP_AZURE_DEFAULT_RESOURCE_GROUP",
    "subscription": "$CP_AZURE_SUBSCRIPTION_ID",
    "authFile": "$CP_CLOUD_CREDENTIALS_LOCATION",
    "sshPublicKeyPath": "$CP_PREF_CLUSTER_SSH_KEY_PATH",
    "meterRegionName": "$azure_meter_name",
    "azureApiUrl": "$CP_AZURE_API_URL",
    "priceOfferId": "$CP_AZURE_OFFER_DURABLE_ID",
    "corsRules": "$cors_rules"
}
EOF
    elif [ "$CP_CLOUD_PLATFORM" == "$CP_GOOGLE" ]; then
        api_setup_file_based_preferences "$INSTALL_SCRIPT_PATH/../cloud-configs/$CP_GOOGLE/prerequisites"
        local gcp_custom_instance_types_json="$(get_file_based_preference gcp.custom.instance.types other $CP_GOOGLE)"
read -r -d '' payload <<-EOF
{
    "regionId":"$region_name",
    "provider":"GCP",
    "name":"$region_name",
    "default":true,
    "authFile": "$CP_CLOUD_CREDENTIALS_LOCATION",
    "sshPublicKeyPath": "$CP_PREF_CLUSTER_SSH_KEY_PATH",
    "corsRules": "$cors_rules",
    "project": "$CP_GCP_PROJECT",
    "applicationName": "$CP_GCP_APPLICATION_NAME",
    "tempCredentialsRole": "$CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE",
    "customInstanceTypes": $gcp_custom_instance_types_json
}
EOF
    else
        print_err "Cloud Provider $CP_CLOUD_PLATFORM is not supported at a full scale, region $region_name WILL NOT be registered. You will have to conifgure it manually from the GUI"
        return 1
    fi

    call_api_register_region_response=$(call_api "/cloud/region" "$CP_API_JWT_ADMIN" "$payload")
    call_api_register_region_result=$?
    if [ $call_api_register_region_result -ne 0 ]; then
        print_err "Error occured while registering a region ($region_name):"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_register_region_response"
        echo "========"
        return 1
    else
        local region_id=$(api_get_region_id "$region_name")
        export CP_CLOUD_REGION_INTERNAL_ID=$region_id
        print_ok "Region $region_name added with id $region_id"
    fi

    # Grant ROLE_USER access to the new region
    api_entity_grant "$CP_CLOUD_REGION_INTERNAL_ID" \
                     "CLOUD_REGION" \
                     "1" \
                     "false" \
                     "$region_role_grant"

    call_api_grant_region_result=$?
    if [ $call_api_grant_region_result -ne 0 ]; then
        print_err "Error occured while granting permissions for $region_role_grant to $region_name region (id: $CP_CLOUD_REGION_INTERNAL_ID), this shall be done manually using GUI/API"
    else
        print_ok "$region_role_grant was granted access to region $region_name (id: $CP_CLOUD_REGION_INTERNAL_ID)"
    fi

    # Register Fileshare mountpoint
    for region_mount_point in ${region_fs_targets[@]}; do
        api_register_fileshare  "$region_id" \
                                "$region_mount_point" \
                                "$CP_FSMOUNT_TYPE" \
                                "$CP_FSMOUNT_OPTIONS"
        if [ $? -ne 0 ]; then
            print_err "Error occured while registering fileshare $region_mount_point for region id $region_id, this shall be done manually using GUI/API"
        else
            print_ok "Fileshare $region_mount_point for region id $region_id is registered"
        fi
    done

}

function api_preference_proxy_get {
    local proxy_name="$1"
    local proxy_value="$2"

    read -r -d '' __tmp__ <<-EOF
{
     "name": "$proxy_name",
     "path": "$proxy_value"
}
EOF
    echo $__tmp__
    unset __tmp__
}

function get_file_based_preference {
    local pref_name="$1"
    local pref_type="${2:-preferences}"
    local cloud_provider="$3"
    local escape="$4"

    local pref_dir=${CP_PREFERENCES_CONFIG_PATH:-"$INSTALL_SCRIPT_PATH/../$pref_type"}
    if [ "$cloud_provider" ]; then
        pref_dir=${CP_CLOUD_PREFERENCES_CONFIG_PATH:-"$CP_CLOUD_CONFIG_PATH/$pref_type"}
    fi
    
    local pref_file="$(realpath ${pref_dir}/${pref_name}.json)"
    if [ ! -f "$pref_file" ]; then
        return 1
    fi

    if [ "$escape" ]; then
        echo "$(escape_string "$(<$pref_file)")"
    else
        cat $pref_file
    fi
}

function api_setup_file_based_preferences {
    # We will iterate over *.json files in the $root_path and set preference name as the filename and the value as a file content
    # e.g. launch.system.parameters.json will result into launch.system.parameters been set
    local root_path="$1"

    print_info "Setting json preferences from $root_path"
    if [ ! -d "$root_path" ]; then
        print_err "Preferences directory $root_path does not exist, NO parameters applied"
        return 1
    fi
    for entry in $(find $root_path -type f -name "*.json"); do 
        local pref_file=$(realpath $entry)
        local pref_name=$(basename -- "$pref_file")
        pref_name="${pref_name%.*}"
        
        local pref_value="$(escape_string "$(envsubst < $pref_file)")"
        api_set_preference "$pref_name" "$pref_value" "true"
    done
}

function api_setup_base_preferences {
    # Set "scalar" preferences
    ## Base
    api_set_preference "base.api.host" "https://${CP_API_SRV_INTERNAL_HOST}:${CP_API_SRV_INTERNAL_PORT}/pipeline/restapi/" "true"
    api_set_preference "base.pipe.distributions.url" "https://${CP_API_SRV_INTERNAL_HOST}:${CP_API_SRV_INTERNAL_PORT}/pipeline/" "true"
    api_set_preference "base.api.host.external" "https://${CP_API_SRV_EXTERNAL_HOST}:${CP_API_SRV_EXTERNAL_PORT}/pipeline/restapi/" "true"

    ## Cluster
    api_set_preference "cluster.allowed.instance.types" "$CP_PREF_CLUSTER_ALLOWED_INSTANCE_TYPES" "true"
    api_set_preference "cluster.allowed.instance.types.docker" "$CP_PREF_CLUSTER_ALLOWED_INSTANCE_TYPES" "true"
    api_set_preference "cluster.autoscale.rate" "${CP_PREF_CLUSTER_AUTOSCALE_RATE:-20000}" "false"
    api_set_preference "cluster.instance.hdd" "${CP_PREF_CLUSTER_INSTANCE_HDD:-50}" "true"
    api_set_preference "cluster.instance.type" "${CP_PREF_CLUSTER_INSTANCE_TYPE}" "true"
    api_set_preference "cluster.allowed.price.types" "${CP_PREF_CLUSTER_ALLOWED_PRICE_TYPES}" "true"
    api_set_preference "cluster.spot" "${CP_PREF_CLUSTER_SPOT:-"true"}" "true"

    ## Git
    api_set_preference "git.repository.indexing.enabled" "false" "false"

    ## Launch
    api_set_preference "launch.task.status.update.rate" "${CP_PREF_LAUNCH_TASK_STATUS_UPDATE_RATE:-20000}" "false"
    api_set_preference "launch.cmd.template" "${CP_PREF_LAUNCH_CMD_TEMPLATE}" "true"

    ## Docker
    api_set_preference "security.tools.jwt.token.expiration" "${CP_PREF_SECURITY_TOOLS_JWT_TOKEN_EXPIRATION:-3600}" "false"

    ## UI templates
    api_set_preference "ui.pipeline.deployment.name" "${CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME:-"Cloud Pipeline"}" "true"

    ## System
    api_set_preference "system.max.idle.timeout.minutes" "${CP_PREF_SYSTEM_MAX_IDLE_TIMEOUT_MINUTES:-1440}" "false"         # How long we tolerate utilization < system.idle.cpu.threshold (default: 24 hours)
    api_set_preference "system.idle.action.timeout.minutes" "${CP_PREF_SYSTEM_IDLE_ACTION_TIMEOUT_MINUTES:-480}" "false"    # How long we wait before performing action, defined by system.idle.action (default: 8 hours)
    api_set_preference "system.resource.monitoring.period" "${CP_PREF_SYSTEM_RESOURCE_MONITORING_PERIOD:-300000}" "false"   # How often we poll utilization stats (default: 5 minutes)
    api_set_preference "system.resource.monitoring.stats.retention.period" "${CP_PREF_SYSTEM_RESOURCE_MONITORING_STATS_RETENTION_PERIOD:-5}" "false"    # How often we drop elastic indices (default: each 5 days)
    api_set_preference "system.idle.action" "${CP_PREF_SYSTEM_IDLE_ACTION:-"NOTIFY"}" "false"                               # Which action to perform if a run is idle for system.max.idle.timeout.minutes + system.idle.action.timeout.minutes (default: notify only)
    api_set_preference "system.idle.cpu.threshold" "${CP_PREF_SYSTEM_IDLE_CPU_THRESHOLD:-1}" "false"                        # %% of CPU utilization, which is considered idle (default: all runs with utilization below 1% are idle)
    api_set_preference "system.memory.consume.threshold" "${CP_PREF_SYSTEM_MEMORY_CONSUME_THRESHOLD:-95}" "false"           # %% of memory utilization that is considered "HIGH" (default: runs with RAM utilization above 95% are under pressure)
    api_set_preference "system.disk.consume.threshold" "${CP_PREF_SYSTEM_DISK_CONSUME_THRESHOLD:-95}" "false"               # %% of disk utilization that is considered "HIGH" (default: runs with Disk utilization above 95% are under pressure)
    api_set_preference "system.monitoring.time.range" "${CP_PREF_SYSTEM_MONITORING_TIME_RANGE:-30}" "false"                 # Period of time (in seconds) used to calculate average of the RAM/Disk utilization (default: 30 seconds)

    ## Commit
    api_set_preference "commit.username" "${CP_PREF_COMMIT_USERNAME:-"pipeline"}" "false"
    if [ "$CP_PREF_COMMIT_DEPLOY_KEY" ]; then
        api_set_preference "commit.deploy.key" "${CP_PREF_COMMIT_DEPLOY_KEY}" "false"
    else
        print_warn "\"commit.deploy.key\" preference is NOT set. Runs COMMIT will NOT be available. Specify it using \"-env CP_PREF_COMMIT_DEPLOY_KEY=\" option"
    fi
    api_set_preference "commit.timeout" "${CP_PREF_COMMIT_TIMEOUT:-18000}" "true"

    # Set "file-based" preferences
    ### General
    CP_PREFERENCES_CONFIG_PATH=${CP_PREFERENCES_CONFIG_PATH:-"$INSTALL_SCRIPT_PATH/../preferences"}
    api_setup_file_based_preferences "$CP_PREFERENCES_CONFIG_PATH"

    ### Cloud provider's
    CP_CLOUD_PREFERENCES_CONFIG_PATH=${CP_CLOUD_PREFERENCES_CONFIG_PATH:-"$CP_CLOUD_CONFIG_PATH/preferences"}
    api_setup_file_based_preferences "$CP_CLOUD_PREFERENCES_CONFIG_PATH"

    # Other preferences that requires specific handling
    ## Prepare proxies list (if provided) for the cluster.networks.config
    api_preference_drop_array
    if [ "$CP_PREF_CLUSTER_PROXIES_DNS" ]; then
        api_preference_append_array "$(api_preference_proxy_get "dns_proxy" "$CP_PREF_CLUSTER_PROXIES_DNS")"
    fi
    if [ "$CP_PREF_CLUSTER_PROXIES_DNS_POST" ]; then
        api_preference_append_array "$(api_preference_proxy_get "dns_proxy_post" "$CP_PREF_CLUSTER_PROXIES_DNS_POST")"
    fi
    if [ "$CP_PREF_CLUSTER_PROXIES_HTTP" ]; then
        api_preference_append_array "$(api_preference_proxy_get "http_proxy" "$CP_PREF_CLUSTER_PROXIES_HTTP")"
    fi
    if [ "$CP_PREF_CLUSTER_PROXIES_HTTPS" ]; then
        api_preference_append_array "$(api_preference_proxy_get "https_proxy" "$CP_PREF_CLUSTER_PROXIES_HTTPS")"
    fi
    if [ "$CP_PREF_CLUSTER_PROXIES_NO" ]; then
        api_preference_append_array "$(api_preference_proxy_get "no_proxy" "$CP_PREF_CLUSTER_PROXIES_NO")"
    fi
    export CP_PREF_CLUSTER_PROXIES="$(api_preference_get_array)"
    api_preference_drop_array

    ## Set cluster.networks.config preference
    local cloud_config_network_file="$CP_CLOUD_CONFIG_PATH/cluster.networks.config.json"
    if [ -f "$cloud_config_network_file" ]; then
        local cluster_networks_config_json="$(escape_string "$(envsubst '${CP_CLOUD_REGION_ID} ${CP_PREF_CLUSTER_INSTANCE_IMAGE_GPU} ${CP_PREF_CLUSTER_INSTANCE_IMAGE} ${CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS} ${CP_PREF_CLUSTER_PROXIES} ${CP_VM_MONITOR_INSTANCE_TAG_NAME} ${CP_VM_MONITOR_INSTANCE_TAG_VALUE} ${CP_PREF_CLUSTER_INSTANCE_NETWORK} ${CP_PREF_CLUSTER_INSTANCE_SUBNETWORK}' < "$cloud_config_network_file")")"
        
        # cluster.networks.config shall be visible, or otherwise node_up.py will NOT be able to get the information from the API Services
        api_set_preference "cluster.networks.config" "$cluster_networks_config_json" "true"
    else
        print_err "Configuration for the Cloud network is not found at ${cloud_config_network_file}. \"cluster.networks.config\" preference WILL NOT be set"
    fi

}

function api_register_search {

    api_set_preference "search.elastic.scheme" "http" "false"
    api_set_preference "search.elastic.allowed.users.field" "allowed_users" "false"
    api_set_preference "search.elastic.denied.users.field" "denied_users" "false"
    api_set_preference "search.elastic.denied.groups.field" "denied_groups" "false"
    api_set_preference "search.elastic.type.field" "doc_type" "false"
    api_set_preference "search.elastic.host" "${CP_SEARCH_ELK_INTERNAL_HOST:-cp-search-elk.default.svc.cluster.local}" "true"
    api_set_preference "search.elastic.port" "${CP_SEARCH_ELK_ELASTIC_INTERNAL_PORT:-30091}" "false"
    api_set_preference "search.elastic.search.fields" "[]" "false"
    api_set_preference "search.elastic.index.common.prefix" "cp-*" "false"
    api_set_preference "search.elastic.allowed.groups.field" "allowed_groups" "false"

local search_elastic_index_type_prefix="{}"
read -r -d '' search_elastic_index_type_prefix <<-EOF
{
    "PIPELINE_RUN": "cp-pipeline-run",
    "S3_FILE": "cp-s3-file*",
    "AZ_BLOB_FILE": "cp-az-file*",
    "NFS_FILE": "cp-nfs-file*",
    "S3_STORAGE": "cp-s3-storage",
    "AZ_BLOB_STORAGE": "cp-az-storage",
    "NFS_STORAGE": "cp-nfs-storage",
    "GS_FILE": "cp-gs-file*",
    "GS_STORAGE": "cp-gs-storage",
    "TOOL": "cp-tool",
    "TOOL_GROUP": "cp-tool-group",
    "DOCKER_REGISTRY": "cp-docker-registry",
    "FOLDER": "cp-folder",
    "METADATA_ENTITY": "cp-metadata-entity",
    "CONFIGURATION": "cp-run-configuration",
    "PIPELINE": "cp-pipeline",
    "ISSUE": "cp-issue",
    "PIPELINE_CODE": "cp-code*"
}
EOF

    search_elastic_index_type_prefix="$(escape_string "$search_elastic_index_type_prefix")"
    api_set_preference "search.elastic.index.type.prefix" "$search_elastic_index_type_prefix" "false"

    if is_service_requested cp-git && is_install_requested; then
        print_info "GitLab WAS installed previously, applying git hooks configuration"
        api_set_preference "git.repository.hook.url" "http://$CP_SEARCH_INTERNAL_HOST:$CP_SEARCH_INTERNAL_PORT/elastic-agent/restapi/githook/event" "false"
        api_set_preference "git.repository.indexing.enabled" "true" "false"
    else
        print_err "GitLab WAS NOT installed previously, git hooks WILL NOT be configured. This can be done manually using GUI/API"
    fi

}

function api_register_docker_comp {
    api_set_preference "security.tools.docker.comp.scan.root.url" "http://${CP_DOCKER_COMP_INTERNAL_HOST}:${CP_DOCKER_COMP_INTERNAL_PORT}/dockercompscan/" "false"
}

function api_register_clair {
    api_set_preference "security.tools.scan.clair.root.url" "http://${CP_CLAIR_INTERNAL_HOST}:${CP_CLAIR_INTERNAL_PORT}" "false"
    api_set_preference "security.tools.grace.hours" "48" "true"
    api_set_preference "security.tools.scan.all.registries" "true" "true"
    api_set_preference "security.tools.scan.enabled" "true" "true"
}

function api_register_share_service {
    api_set_preference "data.sharing.base.api" "https://${CP_SHARE_SRV_EXTERNAL_HOST}:${CP_SHARE_SRV_EXTERNAL_PORT}/proxy/?id=%d" "false"

    local share_service_configuration_preference="{}"
read -r -d '' share_service_configuration_preference <<-EOF
[{
    "endpointId": "https://${CP_SHARE_SRV_EXTERNAL_HOST}:${CP_SHARE_SRV_EXTERNAL_PORT}${CP_SHARE_SRV_SAML_ID_TRAIL:-/proxy}",
    "metadataPath": "${CP_API_SRV_FED_META_DIR}/cp-share-srv-fed-meta.xml",
    "external": "true"
}]
EOF
    share_service_configuration_preference="$(escape_string "${share_service_configuration_preference}")"
    api_set_preference "system.external.services.endpoints" "${share_service_configuration_preference}" "false"
}

function idp_register_app {
    local issuer="$1"
    local cert="$2"
    
    print_info "Creating IdP connection for $issuer with cert $cert"
    idp_register_app_response=$(execute_deployment_command cp-idp "saml-idp add-connection $issuer -c $cert")
    if [ $? -ne 0 ]; then
        print_err "Error ocurred registering IdP connection for $issuer with cert $cert"
        echo "========"
        echo "Response:"
        echo "$idp_register_app_response"
        echo "========"
    else
        print_ok "IdP connection is created for $issuer with cert $cert"
    fi
}

function idp_register_user {
    local username="$1"
    local password="$2"
    local firstname="$3"
    local lastname="$4"
    local email="$5"

    print_info "Registering IdP user $username"
    idp_register_user_response=$(execute_deployment_command cp-idp "saml-idp add-user $username $password --firstName $firstname --lastName $lastname --email $email")
    if [ $? -ne 0 ]; then
        print_err "Error ocurred registering user $username"
        echo "========"
        echo "Response:"
        echo "$idp_register_user_response"
        echo "========"
        return 1
    else
        print_ok "IdP user $username is added"
        return 0
    fi
}

function api_register_system_folder {
    api_create_folder "$CP_API_SRV_SYSTEM_FOLDER_NAME"
    if [ $? -ne 0 ]; then
        print_err "Unable to create system folder \"$CP_API_SRV_SYSTEM_FOLDER_NAME\""
        return 1
    fi

    system_folder_id="$(api_get_entity_id "$CP_API_SRV_SYSTEM_FOLDER_NAME" "folder")"
    if [ $? -ne 0 ] || [ ! "$system_folder_id" ]; then
        print_err "Unable to determine ID of the system folder ${CP_API_SRV_SYSTEM_FOLDER_NAME}"
        return 1
    fi
    
    api_set_entity_attribute "$system_folder_id" \
                             "FOLDER" \
                             "Description" \
                             "$CP_API_SRV_SYSTEM_FOLDER_DESCRIPTION"
    if [ $? -ne 0 ]; then
        print_warn "Unable to set system folder's (${CP_API_SRV_SYSTEM_FOLDER_NAME}, $system_folder_id) description to $CP_API_SRV_SYSTEM_FOLDER_DESCRIPTION"
        return 0
    fi
    return 0
}

function api_register_system_storage {
    if [ -z "$CP_PREF_STORAGE_SYSTEM_STORAGE_NAME" ]; then
        print_err "\"storage.system.storage.name\" preference is NOT set. Issues attachments will NOT work correctly. Specify it using \"-env CP_PREF_STORAGE_SYSTEM_STORAGE_NAME=\" option"
        return 1
    fi

    local parent_folder_id="$(api_get_entity_id "$CP_API_SRV_SYSTEM_FOLDER_NAME" "folder")"
    if [ $? -ne 0 ] || [ ! "$parent_folder_id" ]; then
        print_err "Unable to determine ID of the system folder ${CP_API_SRV_SYSTEM_FOLDER_NAME}, system storage WILL NOT be created"
        return 1
    fi

    if [ -z "$CP_API_SRV_SYSTEM_FOLDER_TYPE" ]; then
        if [ "$CP_CLOUD_PLATFORM" == "$CP_AWS" ]; then
            export CP_API_SRV_SYSTEM_FOLDER_TYPE="S3"
        elif [ "$CP_CLOUD_PLATFORM" == "$CP_AZURE" ]; then
            export CP_API_SRV_SYSTEM_FOLDER_TYPE="AZ"
        elif [ "$CP_CLOUD_PLATFORM" == "$CP_GOOGLE" ]; then
            export CP_API_SRV_SYSTEM_FOLDER_TYPE="GS"
        else
            print_err "Type of the system storage is not set (CP_API_SRV_SYSTEM_FOLDER_TYPE) and it is not possible to determine it from the environment"
            return 1
        fi
    fi

    local payload="{}"
read -r -d '' payload <<-EOF
{ 
    "parentFolderId":$parent_folder_id,
    "name":"${CP_API_SRV_SYSTEM_STORAGE_FRIENDLY_NAME:-cloud-pipeline-etc}",
    "path":"$CP_PREF_STORAGE_SYSTEM_STORAGE_NAME",
    "shared":false,
    "storagePolicy": {
        "versioningEnabled":false
    },
    "type":"$CP_API_SRV_SYSTEM_FOLDER_TYPE"
}
EOF

    call_api_register_system_storage_response=$(call_api "/datastorage/save?cloud=false" "$CP_API_JWT_ADMIN" "$payload")
    call_api_register_system_storage_result=$?
    if [ $call_api_register_system_storage_result -ne 0 ]; then
        print_err "Error occured while registering system storage $CP_PREF_STORAGE_SYSTEM_STORAGE_NAME:"
        echo "========"
        echo "Request:"
        echo "$payload"
        echo "========"
        echo "Response:"
        echo "$call_api_register_system_storage_response"
        echo "========"
    else
        print_ok "System storage $CP_PREF_STORAGE_SYSTEM_STORAGE_NAME is registered"
        api_set_preference "storage.system.storage.name" "${CP_PREF_STORAGE_SYSTEM_STORAGE_NAME}" "true"
    fi
    return $call_api_register_system_storage_result
}

function api_register_pipeline {
    local parent_folder_name="$1"
    local pipeline_name="$2"
    local pipeline_description="$3"
    local pipeline_sources_dir="$4"
    local pipeline_role_grant="${5:-ROLE_USER}"
    local pipeline_role_premissions="${6:-21}"
    local pipeline_version="${7:-v1}"


    # 0. Preflight and  get parent folder id
    local parent_folder_id="$(api_get_entity_id "$parent_folder_name" "folder")"
    if [ $? -ne 0 ] || [ ! "$parent_folder_id" ]; then
        print_err "Unable to determine ID of the folder ${parent_folder_name}, ${pipeline_name} pipeline will not be registered"
        return 1
    fi

    if [ ! -d "$pipeline_sources_dir" ]; then
        print_err "Pipeline sources cannot be found at $pipeline_sources_dir, pipeline WILL NOT be registered "
    fi
    
    # 1. Create a pipeline from the "Default" template
    api_create_pipeline "$pipeline_name" \
                        "$pipeline_description" \
                        "$parent_folder_id"
        if [ $? -ne 0 ]; then
            print_err "Unable to register a new pipeline object from the default template"
            return 1
        fi

    # 2. Get it's ID
    local pipeline_id="$(api_get_entity_id "$pipeline_name" "pipeline")"
    if [ $? -ne 0 ] || [ ! "$pipeline_id" ]; then
        print_err "Unable to determine ID of the pipeline ${pipeline_name}, pipeline WILL NOT be registered"
        return 1
    fi

    # 3. Grant corresponding permissions
    api_entity_grant "$pipeline_id" \
                     "PIPELINE" \
                     "$pipeline_role_premissions" \
                     "false" \
                     "$pipeline_role_grant"
    if [ $? -ne 0 ]; then
        print_warn "Unable to set corresponding permissions for the pipeline ${pipeline_name}, please fix this manually using GUI/API"
    fi

    # 4. Push the pipeline sources
    local pipeline_repo_name="$(echo "$pipeline_name" | sed 's/[^0-9a-zA-Z]//g' | awk '{print tolower($0)}')"
    local pipeline_url="https://${GITLAB_ROOT_USER}:${GITLAB_ROOT_PASSWORD}@${CP_GITLAB_INTERNAL_HOST}:${CP_GITLAB_INTERNAL_PORT}/${GITLAB_ROOT_USER}/${pipeline_repo_name}.git"
    local prev_dir="$(pwd)"

    cd /tmp

    git clone "$pipeline_url" && \
    cd "$pipeline_repo_name" && \
    rm -rf docs/ src/ config.json && \
    \cp -r $pipeline_sources_dir/* ./ && \
    rm -f $pipeline_sources_dir/spec.json && \
    git add -A && \
    git commit -m "Initial commit" && \
    git push -f

    local pipeline_push_result=$?
    cd "$prev_dir"
    rm -rf "/tmp/$pipeline_repo_name"

    if [ $pipeline_push_result -ne 0 ]; then
        print_err "Unable to update pipeline ${pipeline_name} sources. Pipeline is left in a not functional state, please fix this manually using GUI/API"
        return 1
    fi

    # 5. Get current commit id
    local pipeline_details_json=$(call_api "/pipeline/$pipeline_id/load" "$CP_API_JWT_ADMIN")
        if [ $? -ne 0 ]; then
            print_err "Unable to get pipeline ${pipeline_name} commit id. Pipeline is left in a not functional state, please fix this manually using GUI/API"
            return 1
        fi

    local pipeline_commit_id="$(echo $pipeline_details_json | jq -r '.payload.currentVersion.commitId')"
    if [ -z "$pipeline_commit_id" ] || [ "$pipeline_commit_id" == "null" ]; then
        print_err "Unable to extract pipeline ${pipeline_name} commit id from the response. Pipeline is left in a not functional state, please fix this manually using GUI/API"
        return 1
    fi

    # 6. Set version number
    api_release_pipeline "$pipeline_id" \
                         "$pipeline_commit_id" \
                         "$pipeline_version"
     if [ $? -ne 0 ]; then
        print_err "Unable to set version (${pipeline_id}: ${pipeline_commit_id}). Pipeline ${pipeline_name} is left in a not functional state, please fix this manually using GUI/API"
        return 1
    fi

    print_ok "Pipeline $pipeline_name is registered with ID $pipeline_id"
}

function api_register_demo_pipelines {
    local demo_pipelines_path="${1:-$OTHER_PACKAGES_PATH/pipe-demo}"

    for demo_pipeline_spec_file in $(find $demo_pipelines_path -type f -name "spec.json"); do
        local demo_pipeline_dir=$(dirname $demo_pipeline_spec_file)

        # Validate pipeline structure. At least we need src/ directory and a config.json
        local demo_pipeline_config_json="$demo_pipeline_dir/config.json"
        if [ ! -f "$demo_pipeline_config_json" ] || [ ! -d "$demo_pipeline_dir/src" ]; then
            print_warn "Demo pipeline directory at ${demo_pipeline_dir} is malformed: it shall container src/ directory and a config.json at least. This pipeline will be skipped"
            continue
        fi

        local demo_pipeline_spec_json=$(<$demo_pipeline_spec_file)
        local demo_pipeline_parent_folder_name=$(jq -r '.parent_folder // "Pipelines"' <<< "$demo_pipeline_spec_json")
        local demo_pipeline_name=$(jq -r '.name // "NA"' <<< "$demo_pipeline_spec_json")
        local demo_pipeline_description=$(jq -r '.description // ""' <<< "$demo_pipeline_spec_json")
        local demo_pipeline_version=$(jq -r '.version // "v1"' <<< "$demo_pipeline_spec_json")
        local demo_pipeline_grant_role_name=$(jq -r '.grant_role_name // "ROLE_USER"' <<< "$demo_pipeline_spec_json")
        local demo_pipeline_grant_role_permissions=$(jq -r '.grant_role_permissions // "21"' <<< "$demo_pipeline_spec_json")
        local demo_pipeline_cloud_provider_instance_type=$(jq -r ".${CP_CLOUD_PLATFORM} // \"NA\"" <<< "$demo_pipeline_spec_json")

        if [ -z "$demo_pipeline_description" ]; then
            print_warn "Demo pipeline at $demo_pipeline_dir does not contain a descripton in the spec.json. This pipeline will be skipped"
        fi

        if [ ! "$demo_pipeline_cloud_provider_instance_type" ] || [ "$demo_pipeline_cloud_provider_instance_type" == "NA" ]; then
            print_warn "Demo pipeline at $demo_pipeline_dir is not support for the current cloud environment (${CP_CLOUD_PLATFORM}). This pipeline will be skipped"
            continue
        fi

        # Update the pipeline's config.json with the environment variables (e.g. cloud-specific instance/vm type)
        export CP_CONFIG_JSON_INSTANCE_TYPE="$demo_pipeline_cloud_provider_instance_type"
        local demo_pipeline_config_json_contents="$(envsubst < "$demo_pipeline_config_json")"
        cat <<< "$demo_pipeline_config_json_contents" > "$demo_pipeline_config_json"

        print_info "Creating parent folder $demo_pipeline_parent_folder_name for the pipeline $demo_pipeline_name"
        api_create_folder_path "$demo_pipeline_parent_folder_name"
        if [ $? -ne 0 ]; then
            print_warn "Errors occured while creating parent folder $demo_pipeline_parent_folder_name for the pipeline ${demo_pipeline_name}. This pipeline will be skipped"
            continue
        fi

        api_register_pipeline   "$demo_pipeline_parent_folder_name" \
                                "$demo_pipeline_name" \
                                "$demo_pipeline_description" \
                                "$demo_pipeline_dir" \
                                "$demo_pipeline_grant_role_name" \
                                "$demo_pipeline_grant_role_permissions" \
                                "$demo_pipeline_version"

        if [ $? -ne 0 ]; then
            print_err "Error occured while registering a demo pipeline $demo_pipeline_name (see any output above)"
            return 1
        fi
    done
    unset CP_CONFIG_JSON_INSTANCE_TYPE
}

function api_register_data_transfer_pipeline {
    local dt_role_grant="${1:-ROLE_USER}"
    local dt_role_permissions="26"
    local dt_pipeline_version=${CP_API_SRV_SYSTEM_TRANSFER_PIPELINE_VERSION:-v1}
    
    # 0. Verify and update config.json template
    local dt_pipeline_dir="$OTHER_PACKAGES_PATH/data_loader"
    local dt_pipeline_config_json="$dt_pipeline_dir/config.json"
    if [ ! -f "$dt_pipeline_config_json" ]; then
        print_err "config.json is not found for the data transfer pipeline at ${dt_pipeline_config_json}. Data transfer pipeline will not be registered"
        return 1
    fi
    local dt_pipeline_config_json_content="$(envsubst < "$dt_pipeline_config_json")"
    cat <<< "$dt_pipeline_config_json_content" > "$dt_pipeline_config_json"

    # 1. Register a data transfer pipeline in general
    api_register_pipeline   "$CP_API_SRV_SYSTEM_FOLDER_NAME" \
                            "$CP_API_SRV_SYSTEM_TRANSFER_PIPELINE_FRIENDLY_NAME" \
                            "$CP_API_SRV_SYSTEM_TRANSFER_PIPELINE_DESCRIPTION" \
                            "$dt_pipeline_dir" \
                            "$dt_role_grant" \
                            "$dt_role_permissions" \
                            "$dt_pipeline_version"

    if [ $? -ne 0 ]; then
        print_err "Error occured while registering a data transfer pipeline (see any output above). API will not be configured to use data transfer pipeline"
        return 1
    fi
    
    # 2. Get data transfer pipeline registered id
    local pipeline_id="$(api_get_entity_id "$CP_API_SRV_SYSTEM_TRANSFER_PIPELINE_FRIENDLY_NAME" "pipeline")"
    if [ $? -ne 0 ] || [ ! "$pipeline_id" ]; then
        print_err "Unable to determine ID of the data transfer pipeline. API will not be configured to use data transfer pipeline"
        return 1
    fi

    # 3. Register data transfer pipeline in the preferences
    api_set_preference "storage.transfer.pipeline.id" "$pipeline_id" "true"
    api_set_preference "storage.transfer.pipeline.version" "$dt_pipeline_version" "true"

    print_ok "Data transfer pipeline $CP_API_SRV_SYSTEM_TRANSFER_PIPELINE_FRIENDLY_NAME is registered with ID $pipeline_id and tag $dt_pipeline_version"
}

function api_register_email_templates {
    CP_EMAIL_TEMPLATES_CONFIGS_PATH=${CP_EMAIL_TEMPLATES_CONFIGS_PATH:-"$INSTALL_SCRIPT_PATH/../email-templates/configs"}
    CP_EMAIL_TEMPLATES_CONTENTS_PATH=${CP_EMAIL_TEMPLATES_CONTENTS_PATH:-"$INSTALL_SCRIPT_PATH/../email-templates/contents"}

    if [ ! -d "$CP_EMAIL_TEMPLATES_CONFIGS_PATH" ] || [ ! -d "$CP_EMAIL_TEMPLATES_CONTENTS_PATH" ]; then
        print_err "Email templates directory not found at $CP_EMAIL_TEMPLATES_CONFIGS_PATH or $CP_EMAIL_TEMPLATES_CONTENTS_PATH"
        return 1
    fi

    # Get email notifications ID - type mapping
    local email_notifications_json=$(call_api "/notification/settings" "$CP_API_JWT_ADMIN")
    if [ $? -ne 0 ]; then
        print_err "Unable to get Email notifications ID - Type mapping"
        return 1
    fi

    for entry in $(find $CP_EMAIL_TEMPLATES_CONFIGS_PATH -type f -name "*.json"); do
        local email_template_config_file=$(realpath $entry)
        local email_template_name=$(basename -- "$email_template_config_file")
        email_template_name="${email_template_name%.*}"

        local email_template_contents_file="$(realpath $CP_EMAIL_TEMPLATES_CONTENTS_PATH/$email_template_name.html)"
        if [ ! -f "$email_template_contents_file" ]; then
            print_warn "Email template contents for $email_template_name not found at $email_template_contents_file while corresponding config exists at $email_template_config_file"
            continue
        fi
        email_template_contents="$(escape_string "$(envsubst < $email_template_contents_file)")"

        email_template_id=$(echo "$email_notifications_json" | jq -r ".payload[] | select(.type == \"$email_template_name\") | .id")
        if [ -z "$email_template_id" ] || [ "$email_template_id" == "null" ]; then
            print_warn "Unable to get ID for the template \"$email_template_name\" ($email_template_contents_file)"
            continue
        fi

        email_subject_prefix="[${CP_PREF_UI_PIPELINE_DEPLOYMENT_NAME:-"Cloud Pipeline"}]"
        email_default_subject="Event Notification"
        email_template_keep_admins="$(jq -r '.keepInformedAdmins // "true"' <$email_template_config_file)"
        email_template_keep_owner="$(jq -r '.keepInformedOwner // "true"' <$email_template_config_file)"
        email_template_enabled="$(jq -r '.enabled // "true"' <$email_template_config_file)"
        email_template_threshold="$(jq -r '.threshold // "-1"' <$email_template_config_file)"
        email_template_resend="$(jq -r '.resendDelay // "-1"' <$email_template_config_file)"
        email_template_subject="$(jq -r ".subject // \"$email_default_subject\"" <$email_template_config_file)"
        email_template_subject="$(escape_string "${email_subject_prefix} ${email_template_subject}")"

        # Setting notification template contents
        local payload="{}"

read -r -d '' payload <<-EOF
{
    "id":$email_template_id,
    "name":"$email_template_name",
    "body":"$email_template_contents",
    "subject":"$email_template_subject"
}
EOF

        call_api_set_email_template_response=$(call_api "/notification/template" "$CP_API_JWT_ADMIN" "$payload")
        call_api_set_email_template_result=$?
        if [ $call_api_set_email_template_result -ne 0 ]; then
            print_err "Error occured while setting notification template ($email_template_name):"
            echo "========"
            echo "Request:"
            echo "$payload"
            echo "========"
            echo "Response:"
            echo "$call_api_set_email_template_response"
            echo "========"
            continue
        else
            print_ok "Notification template is set ($email_template_name)"
        fi

        # Setting notification parameters
        payload="{}"

read -r -d '' payload <<-EOF
{
    "id":$email_template_id,
    "informedUserIds":[],
    "keepInformedAdmins":$email_template_keep_admins,
    "keepInformedOwner":$email_template_keep_owner,
    "templateId":$email_template_id,
    "type":"$email_template_name",
    "enabled":$email_template_enabled,
    "resendDelay": $email_template_resend,
    "threshold": $email_template_threshold
}
EOF

        call_api_set_email_settings_response=$(call_api "/notification/settings" "$CP_API_JWT_ADMIN" "$payload")
        call_api_set_email_settings_result=$?
        if [ $call_api_set_email_settings_result -ne 0 ]; then
            print_err "Error occured while configuring notification settings ($email_template_name):"
            echo "========"
            echo "Request:"
            echo "$payload"
            echo "========"
            echo "Response:"
            echo "$call_api_set_email_settings_response"
            echo "========"
            continue
        else
            print_ok "Notification params are set ($email_template_name)"
        fi
    
    done
}

function api_register_custom_users {
    local users_file="${1:-$OTHER_PACKAGES_PATH/prerequisites/users.json}"
    if [ ! -f "$users_file" ]; then
        print_warn "Custom users list not found at ${users_file}. Only default admin user will be registered"
        return 1
    fi
    
    local users_list=$(jq -r '.[].username' < $users_file)
    local username=""
    for username in $users_list; do
        local user_json=$(jq ".[] | select(.username==\"$username\")" < $users_file)
        
        # If any of the user's attribute is empty - $username value will be used
        local pass=$(jq -r ".pass // \"$username\"" <<< "$user_json")
        local firstname=$(jq -r ".firstname // \"$username\"" <<< "$user_json")
        local lastname=$(jq -r ".lastname // \"$username\"" <<< "$user_json")
        local email=$(jq -r ".email // \"$username@nowhere.com\"" <<< "$user_json")
        
        idp_register_user "$username" \
                          "$pass" \
                          "$firstname" \
                          "$lastname" \
                          "$email"
        if [ $? -ne 0 ]; then
            print_err "Unable to register user $username in IdP, user will be skipped"
            continue
        fi

        local roles_ids=""
        local role=""
        while read -r role; do
            role_id=$(api_get_role_id "$role")
            if [ $? -ne 0 ]; then
                print_warn "Unable to get id of \"$role\" role for user ${username}. This role WILL NOT be assigned"
                continue
            fi

            roles_ids="${roles_ids}${role_id},"
        done <<< "$(jq -r '.roles[]' <<< $user_json)"

        # %? - removes last comma from the $roles_ids
        api_register_user "$username" "${roles_ids%?}"
        if [ $? -ne 0 ]; then
            print_err "Unable to register user $username in API Services, user will be skipped"
            continue
        else
            print_ok "User $username is registered in both IdP and in API Services"
        fi

    done
}
