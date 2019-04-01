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

export CP_AWS="aws"
export CP_AZURE="az"
export CP_GOOGLE="gcp"
export CP_NA="NA"
export CP_DOLLAR='$'

export OTHER_PACKAGES_PATH="${INSTALL_SCRIPT_PATH}/../../other-packages"
export VERSION_FILE_PATH="${INSTALL_SCRIPT_PATH}/../../version.txt"

function realpath { 
    echo $(cd $(dirname $1); pwd)/$(basename $1); 
}

function escape_string {
    echo "$1" | sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g' | sed 's/"/\\"/g'
}

function id_from_arn {
    local arn="$1"
    echo $arn | cut -d/ -f2
}

function escape_comma_separated_values {
    local value="$1"
    IFS="," read -ra v_arr <<< "$value"
    value=""
    for v_item in "${v_arr[@]}"; do
        value="\"$(echo $v_item)\",$value"
    done
    # Remove last comma
    value="${value: : -1}"
    echo "$value"
}

function run_preflight {
    local args_number=$1
    if [ $args_number == 0 ]; then
        print_err "No arguments provided, refusing to perform any action"
        return 1
    fi
    if [[ $EUID -ne 0 ]]; then
        print_err "Installation shall be run as a root user"
        return 1
    fi
    if ! grep -q centos /etc/os-release; then
        print_err "Unsopported Linux distribution. Centos 7 and above shall be used"
        return 1
    fi
    return 0
}

function check_params_present {
    local update_config_on_success="$1"
    shift

    local all_params_set=0
    for param_name in $@; do
        local param_value="${!param_name}"
        if [ -z "$param_value" ]; then
            print_warn "$param_name is not set"
            all_params_set=1
        elif [ "$update_config_on_success" == "update_config" ]; then
            update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                "$param_name" \
                                "$param_value"
        fi
    done

    if [ $all_params_set -eq 0 ] && [ "$update_config_on_success" == "update_config" ]; then
        init_kube_config_map
    fi
    return $all_params_set
}

function init_cloud_config {
    export CP_CLOUD_EXTERNAL_HOST=""
    export CP_CLOUD_INTERNAL_HOST=""
    export CP_CLOUD_INSTANCE_TYPE=""
    export CP_CLOUD_REGION_ID=""
    export CP_CLOUD_PLATFORM=""

    local azure_meta_url="-H Metadata:true \"http://169.254.169.254/metadata/instance?api-version=2017-12-01\""
    local aws_meta_url="http://169.254.169.254/latest/meta-data/"
    local aws_meta_url_dynamic="http://169.254.169.254/latest/dynamic/instance-identity/document"

    [ $(eval "curl -s $aws_meta_url -o /dev/null -w \"%{http_code}\"") == "200" ] && CP_CLOUD_PLATFORM=$CP_AWS
    [ $(eval "curl -s $azure_meta_url -o /dev/null -w \"%{http_code}\"") == "200" ] && CP_CLOUD_PLATFORM=$CP_AZURE

    if [ -z "$CP_CLOUD_PLATFORM" ]; then
        print_err "Current cloud provider cannot be detected"
        CP_CLOUD_PLATFORM=$CP_NA
        return 1
    fi

    if [ "$CP_CLOUD_PLATFORM" == "$CP_AZURE" ]; then
        local metadata=$(eval "curl -s $azure_meta_url | jq -r '[.compute.location,.compute.vmSize,.network.interface[0].ipv4.ipAddress[0].privateIpAddress,.network.interface[0].ipv4.ipAddress[0].publicIpAddress] | @tsv'")
        CP_CLOUD_REGION_ID=$(echo $metadata | cut -f1 -d' ')
        CP_CLOUD_INSTANCE_TYPE=$(echo $metadata | cut -f2 -d' ')
        CP_CLOUD_INTERNAL_HOST=$(echo $metadata | cut -f3 -d' ')
        CP_CLOUD_EXTERNAL_HOST=$(echo $metadata | cut -f4 -d' ')
    fi
    if [ "$CP_CLOUD_PLATFORM" == "$CP_AWS" ]; then
        CP_CLOUD_REGION_ID=$(curl -s $aws_meta_url_dynamic | grep region | cut -d\" -f4)
        CP_CLOUD_INSTANCE_TYPE=$(curl -s $aws_meta_url/instance-type)
        CP_CLOUD_INTERNAL_HOST=$(curl -s $aws_meta_url/local-ipv4)
        CP_CLOUD_EXTERNAL_HOST=$(curl -s $aws_meta_url/public-ipv4)
    fi
    if [ "$CP_CLOUD_PLATFORM" == "$CP_GOOGLE" ]; then
        print_err "Metadata is NOT supported for $CP_GOOGLE, cannot auto initialize cloud config"
        return 1
    fi

    # Check cloud images manifest file and use it if exists
    export CP_CLOUD_IMAGES_MANIFEST_FILE="$INSTALL_SCRIPT_PATH/../../cloud-images-manifest.txt"
    if [ -f "$CP_CLOUD_IMAGES_MANIFEST_FILE" ]; then
        while IFS=, read -r cloud_provider cloud_region cloud_image_id cloud_image_name cloud_image_date
        do
            # Check if current entry corresponds to the detected cloud provider/region
            if [ "$cloud_provider" == "$CP_CLOUD_PLATFORM" ] && [ "$cloud_region" ==  "$CP_CLOUD_REGION_ID" ]; then
                # Check if it is GPU or Common image (we rely here on a convention that GPU or Common suffix will be present in the image name)
                if [[ "$cloud_image_name" == *"Common" ]]; then
                    export CP_PREF_CLUSTER_INSTANCE_IMAGE="$cloud_image_id"
                fi
                if [[ "$cloud_image_name" == *"GPU" ]]; then
                    export CP_PREF_CLUSTER_INSTANCE_IMAGE_GPU="$cloud_image_id"
                fi
            fi
        done < $CP_CLOUD_IMAGES_MANIFEST_FILE

        if [ -z "$CP_PREF_CLUSTER_INSTANCE_IMAGE" ]; then
            print_warn "Common image is not defined in the manifest: $CP_CLOUD_IMAGES_MANIFEST_FILE"
        fi

        if [ -z "$CP_PREF_CLUSTER_INSTANCE_IMAGE_GPU" ]; then
            print_warn "GPU image is not defined in the manifest: $CP_CLOUD_IMAGES_MANIFEST_FILE"
        fi
    else
        print_warn "Cloud images manifest file not found at $CP_CLOUD_IMAGES_MANIFEST_FILE"
    fi

    echo    "Cloud config is initialized with:"
    echo    "Platform: $CP_CLOUD_PLATFORM"
    echo    "Region: $CP_CLOUD_REGION_ID"
    echo    "Instance type: $CP_CLOUD_INSTANCE_TYPE"
    echo    "Internal host address: $CP_CLOUD_INTERNAL_HOST"
    echo    "External host address: $CP_CLOUD_EXTERNAL_HOST"
    echo    "Common image ID: $CP_PREF_CLUSTER_INSTANCE_IMAGE"
    echo    "GPU image ID: $CP_PREF_CLUSTER_INSTANCE_IMAGE_GPU"

}

function validate_cloud_config {
    if [ "$CP_CLOUD_PLATFORM" == "$CP_AWS" ]; then
         # Validate mandatory parameters for AWS
        if [ -z "$CP_AWS_KMS_ARN" ]; then
            print_err "Default encryption key ARN for AWS KMS service is not defined, but it is required for the configuration. Please specify it using \"--env CP_AWS_KMS_ARN=\" option"
            return 1
        fi
        if [ "$CP_AWS_ACCESS_KEY_ID" ] && [ "$CP_AWS_SECRET_ACCESS_KEY" ]; then
            export CP_CLOUD_CREDENTIALS_FILE=${CP_CLOUD_CREDENTIALS_FILE:-$CP_CLOUD_CREDENTIALS_LOCATION}
            print_info "AWS access keys are defined via parameters"
            mkdir -p $(dirname $CP_CLOUD_CREDENTIALS_FILE)

cat > $CP_CLOUD_CREDENTIALS_FILE <<EOF
[default]
aws_access_key_id = $CP_AWS_ACCESS_KEY_ID
aws_secret_access_key = $CP_AWS_SECRET_ACCESS_KEY
EOF

        elif [ -f "$CP_CLOUD_CREDENTIALS_FILE" ]; then
            print_info "AWS access keys are defined via CP_CLOUD_CREDENTIALS_FILE"
        elif [ -f ~/.aws/credentials ]; then
            print_info "AWS access keys are defined via ~/.aws/credentials"
            export CP_CLOUD_CREDENTIALS_FILE=~/.aws/credentials
        else
            echo "AWS access keys are not defined, please use -env option to define:"
            echo "1. CP_AWS_ACCESS_KEY_ID and CP_AWS_SECRET_ACCESS_KEY variables pair"
            echo "2. OR CP_CLOUD_CREDENTIALS_FILE path to the aws credentials"
            echo "3. OR create ~/.aws/credentials to be used by default"
            return 1
        fi

        # These variables are exposed to the pods - to point SDKs to the custom credentials location
        # For java sdk v1 and v2 - different variables were used: https://docs.amazonaws.cn/en_us/sdk-for-java/v2/migration-guide/client-credential.html
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                "AWS_SHARED_CREDENTIALS_FILE" \
                                "$CP_CLOUD_CREDENTIALS_LOCATION"

        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                                "AWS_CREDENTIAL_PROFILES_FILE" \
                                "$CP_CLOUD_CREDENTIALS_LOCATION"

        if [ -z "$CP_PREF_CLUSTER_SSH_KEY_NAME" ]; then
            print_err "Name of the SSH key, used to access cluster nodes is not defined, but it is required for the configuration. Please specify it using \"-env CP_PREF_CLUSTER_SSH_KEY_NAME=\" option"
            return 1
        fi
        if [ -z "$CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE" ]; then
            print_warn "Name of temporary credentials role is  NOT set. \"pipe storage ...\" commands will NOT work correctly. Please specify it using \"-env CP_PREF_STORAGE_TEMP_CREDENTIALS_ROLE=\" option"
        fi
        

    elif [ "$CP_CLOUD_PLATFORM" == "$CP_AZURE" ]; then
        if [ -f "$CP_CLOUD_CREDENTIALS_FILE" ]; then
            print_info "Azure access keys are defined via CP_CLOUD_CREDENTIALS_FILE"
        else
            print_err "Azure access keys are not defined, please use -env option to define CP_CLOUD_CREDENTIALS_FILE path to the azure credentials"
            return 1
        fi
        # FIXME: this can be generated automatically for Azure/GCP (ssh private/public) if not specified on the command line
        if [ -z "$CP_CLUSTER_SSH_PUB" ]; then
            print_err "Path to the SSH public key (used to access cloud nodes) is not defined, but it is required for the configuration. Please specify it using \"-env CP_CLUSTER_SSH_PUB=\" option"
            return 1
        fi
        if [ -z "$CP_AZURE_STORAGE_ACCOUNT" ] || [ -z "$CP_AZURE_STORAGE_KEY" ]; then
            print_err "Azure storage account name or key is not defined, but it is required for the configuration. Please specify it using \"-env CP_AZURE_STORAGE_ACCOUNT=\" and \"-env CP_AZURE_STORAGE_KEY=\" option"
            return 1
        fi
        if [ -z "$CP_AZURE_DEFAULT_RESOURCE_GROUP" ]; then
            print_err "Default Azure resource group is not defined, but it is required for the configuration. Please specify it using \"-env CP_AZURE_DEFAULT_RESOURCE_GROUP=\" option"
            return 1
        fi
        if [ -z "$CP_AZURE_SUBSCRIPTION_ID" ]; then
            print_err "Azure subscription ID is not defined, but it is required for the configuration. Please specify it using \"-env CP_AZURE_SUBSCRIPTION_ID=\" option"
            return 1
        fi
        if [ -z "$CP_AZURE_OFFER_DURABLE_ID" ]; then
            print_err "Azure durable ID is not defined, but it is required for the configuration. Please specify it using \"-env CP_AZURE_OFFER_DURABLE_ID=\" option"
            return 1
        fi
    else
        print_err "Unsupported Cloud Provider ($CP_CLOUD_PLATFORM)"
        return 1
    fi

    # Cloud common
    if [ -z "$CP_PREF_CLUSTER_INSTANCE_IMAGE" ]; then
        print_err "Default cluster nodes image ID is not defined, but it is required for the configuration. Please specify it using \"-env CP_PREF_CLUSTER_INSTANCE_IMAGE=\" option"
        return 1
    fi
    if [ -z "$CP_PREF_CLUSTER_INSTANCE_IMAGE_GPU" ]; then
        print_warn "Default GPU cluster nodes image ID is not defined, $CP_PREF_CLUSTER_INSTANCE_IMAGE will be used by default. If it shall be different - please specify it using \"-env CP_PREF_CLUSTER_INSTANCE_IMAGE_GPU=\" option"
        export CP_PREF_CLUSTER_INSTANCE_IMAGE_GPU=$CP_PREF_CLUSTER_INSTANCE_IMAGE
    fi
    if [ -z "$CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS" ]; then
        print_err "Default list of security groups is not defined, but it is required for the configuration. Please specify it using \"-env CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS=\" option (comma separated list is accepted)"
        return 1
    else
        export CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS="$(escape_comma_separated_values "$CP_PREF_CLUSTER_INSTANCE_SECURITY_GROUPS")"
    fi
    if [ -z "$CP_CLOUD_REGION_FILE_STORAGE_HOSTS" ]; then
        print_warn "File storage hosts parameter is not set. File storage mounts WILL NOT be available. Please specify it using \"-env CP_CLOUD_REGION_FILE_STORAGE_HOSTS=\" option (comma separated list is accepted)"
    fi

    mkdir -p $(dirname $CP_CLOUD_CREDENTIALS_LOCATION)

    # \ in the beginning of cp - bypasses any alias (e.g. cp -i) that may introduce interactive prompt for overwiting the destination
    \cp "$CP_CLOUD_CREDENTIALS_FILE" "$CP_CLOUD_CREDENTIALS_LOCATION" &>/dev/null

    return 0
}

function init_kube_config_map {
    print_info "Trying to delete existing config map, if it exists"
    kubectl delete configmap cp-config-global

    print_info "Creating a new config map"
    kubectl create configmap cp-config-global \
                            --from-env-file=$CP_INSTALL_CONFIG_FILE
}   

function init_kube_secrets {
    # Cloud creds
    print_info "Trying to delete existing cloud secret, if it exists"
    kubectl delete secret cp-cloud-credentials

    print_info "Creating a new cloud secret"
    kubectl create secret generic cp-cloud-credentials \
                                --from-file=$CP_CLOUD_CREDENTIALS_LOCATION

    rm -f $CP_CLOUD_CREDENTIALS_LOCATION

    # SSH creds
    print_info "Trying to delete existing cluster SSH secret, if it exists"
    kubectl delete secret cp-cluster-ssh-key

    print_info "Creating cluster SSH secret"
    local _ssh_key_name=/tmp/$(basename $CP_PREF_COMMIT_DEPLOY_KEY)
    local _ssh_pub_name=/tmp/$(basename $CP_PREF_CLUSTER_SSH_KEY_PATH)
    \cp $CP_CLUSTER_SSH_KEY $_ssh_key_name

    if [ -f "$CP_CLUSTER_SSH_PUB" ]; then
        \cp $CP_CLUSTER_SSH_PUB $_ssh_pub_name
    else
        print_warn "CP_CLUSTER_SSH_PUB is not defined correctly, but as the upstream passed - considering this as \"fine\". CP_CLUSTER_SSH_PUB will be stubbed"
        echo "CP_CLUSTER_SSH_PUB: STUB" > $_ssh_pub_name
    fi
    

    kubectl create secret generic cp-cluster-ssh-key \
                                --from-file=$_ssh_key_name \
                                --from-file=$_ssh_pub_name

    rm -f $_ssh_key_name
    rm -f $_ssh_pub_name
}

function update_config_value {
    local config_file="$1"
    local name="$2"
    local value="$3"
    sed -i "/${name}=/d" $config_file
    echo "${name}=${value}" >> $config_file
}

function load_install_config {
    if [ -z $1 ] || [ ! -f $1 ]; then
        print_err "Unable to load config from \"$1\" as it does not exist"
        return 1
    fi

    set -o allexport

    source $1

    set +o allexport
}

function append_install_config {
    local file_to_append="$1"
    local main_install_config="${2:-$CP_INSTALL_CONFIG_FILE}"
    if [ -z "$file_to_append" ] || [ ! -f "$file_to_append" ]; then
        print_error "File to append to the installation config is provided or does not exist. Provided value is \"$file_to_append\""
        return 1
    fi

    while IFS="=" read -r append_key append_value; do
        update_config_value "$main_install_config" \
                            "$append_key" \
                            "$append_value" 
    done < "$file_to_append"
}

function parse_env_option {
    local key_value="$1"
    IFS="=" read -r key value <<< "${key_value}"
    
    key=${key//[^a-zA-Z_0-9]/_}
    if [ -z $value ]; then
        value="1"
    fi

    declare -xg "${key}"="${value}"
    export __parse_env_option_result__="${key}"
}

function set_service_host {
    local service_host_name="$1"
    local service_host_internal_name="$2"
    local service_host_value="${!service_host_var}"
    local service_host_internal_value=""
    if [ "$service_host_internal_name" ]; then
        service_host_internal_value="${!service_host_internal_name}"
    fi

    if [ "$service_host_value" ]; then
        print_info "$service_host_name is set explicitly to $service_host_value"
    elif   [ "$CP_GLOBAL_EXTERNAL_HOST" ]; then
        print_info "$service_host_name is set explicitly to the global value $service_host_value"
        service_host_value=$CP_GLOBAL_EXTERNAL_HOST
    elif   [ "$CP_CLOUD_EXTERNAL_HOST" ]; then
        print_info "$service_host_name is set to the default cloud instance's external address $service_host_value"
        service_host_value=$CP_CLOUD_EXTERNAL_HOST
    elif   [ "$CP_CLOUD_INTERNAL_HOST" ]; then
        print_info "$service_host_name is set to the default cloud instance's internal address $service_host_value"
        service_host_value=$CP_CLOUD_INTERNAL_HOST
    elif [ "$service_host_internal_value" ]; then
        print_warn "$service_host_name cannot be determined - cluster internal name ($service_host_internal_value) will be used by default. This may cause network access issues"
        service_host_value=$service_host_internal_value
    else
        print_warn "$service_host_name cannot be determined and internal name is not set as well, \"localhost\" will be used. This may cause isntallation and network access issues"
        service_host_value="localhost"
    fi

    if [ "$service_host_value" ]; then
        print_info "$service_host_name is set to $service_host_value"
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                "$service_host_name" \
                "$service_host_value"
        declare -xg "${service_host_name}"="${service_host_value}"
    else
        print_err "$service_host_name cannot be determined"
        return 1
    fi
}

function array_contains_or_empty () {
    local search_string="$1"
    shift
    local arr=($@)
    
    if [ -z "$arr" ] || [ ${#arr[@]} == 0 ]; then
        return 0
    fi
    
    for item in "${arr[@]}"; do [[ "$item" == "$search_string" ]] && return 0; done
    return 1
}

function parse_options {
    local services_count=0
    POSITIONAL=()
    EXPLICIT_ENV_OPTIONS=()
    export CP_DOCKERS_TO_INIT=
    while [[ $# -gt 0 ]]
    do
    key="$1"

    case $key in
        -id|--deployment-id)
        export CP_DEPLOYMENT_ID="$2"
        shift # past argument
        shift # past value
        ;;
        -c|--install-config)
        export CP_INSTALL_CONFIG_FILE="$2"
        shift # past argument
        shift # past value
        ;;
        -m|--install-kube-master)
        export CP_INSTALL_KUBE_MASTER=1
        shift # past argument
        ;;
        -e|--erase-data)
        export CP_FORCE_DATA_ERASE=1
        shift # past argument
        ;;
        -d|--docker)
        CP_DOCKERS_TO_INIT="$CP_DOCKERS_TO_INIT $2"
        shift # past argument
        shift # past value
        ;;
        -demo|--deploy-demo)
        export CP_DEPLOY_DEMO=1
        shift # past argument
        ;;
        -s|--service)
        parse_env_option "$2"
        services_count=$((services_count+1))
        shift # past argument
        shift # past value
        ;;
        -env|--environment)
        # Here we only collect list of var=value options. The will applied (exported) after the config file (CP_INSTALL_CONFIG_FILE) will be sourced
        # so command line options will have higher priority compared to the file
        EXPLICIT_ENV_OPTIONS+=("$2")
        shift # past argument
        shift # past value
        ;;
        *)    # unknown option
        POSITIONAL+=("$1") # save it in an array for later
        shift # past argument
        ;;
    esac
    done
    set -- "${POSITIONAL[@]}" # restore positional parameters
    
    local cp_bad_command_msg="\"install\" or \"remove\" command shall be specified"
    if [[ "$#" == 0 ]] || [[ "$#" > 1 ]]; then
        cp_bad_command=1 
    elif [ "$1" != "install" ] && [ "$1" != "remove" ]; then
        cp_bad_command=1 
    fi

    if [ "$cp_bad_command" ]; then
        echo $bad_command_msg
        return 1
    fi

    export CP_INSTALLER_COMMAND="$1"

    if [ -f "$VERSION_FILE_PATH" ]; then
        export CP_VERSION=$(cat "$VERSION_FILE_PATH")
        print_info "Version $CP_VERSION is being configured"
    else
        print_err "Unable to find an installation version file spec at $VERSION_FILE_PATH"
        return 1
    fi

    if [ -z $CP_INSTALL_CONFIG_FILE ]; then
        print_warn "-c|--install-config : path to the installation config not set - default configuration will be used"
        export CP_INSTALL_CONFIG_FILE="$INSTALL_SCRIPT_PATH/../install-config"
        mkdir -p $(dirname $CP_INSTALL_CONFIG_FILE)
    fi
    load_install_config "$CP_INSTALL_CONFIG_FILE"
    if [ $? -ne 0 ]; then
        print_err "Unable to load config from $CP_INSTALL_CONFIG_FILE"
        return 1
    fi

    if [ -z "$CP_CLOUD_CONFIG_PATH" ]; then
        print_warn "-a|--cloud-config : path to the cloud specific config not set - default configuration will be used"
        export CP_CLOUD_CONFIG_PATH="$INSTALL_SCRIPT_PATH/../cloud-configs/$CP_CLOUD_PLATFORM"
        export CP_CLOUD_CONFIG_FILE="$CP_CLOUD_CONFIG_PATH/cloud-config"
    fi
    # Cloud-specific config file is loaded to the environment and also appended to the $CP_INSTALL_CONFIG_FILE to make it's content available in the pods
    load_install_config "$CP_CLOUD_CONFIG_FILE" && \
    append_install_config "$CP_CLOUD_CONFIG_FILE"
    if [ $? -ne 0 ]; then
        print_err "Unable to load config from $CP_CLOUD_CONFIG_FILE"
        return 1
    fi

    # Add cloud platform information
    update_config_value "$CP_INSTALL_CONFIG_FILE" \
                        "CP_CLOUD_PLATFORM" \
                        "$CP_CLOUD_PLATFORM"
    update_config_value "$CP_INSTALL_CONFIG_FILE" \
                        "CP_CLOUD_REGION_ID" \
                        "$CP_CLOUD_REGION_ID"
    update_config_value "$CP_INSTALL_CONFIG_FILE" \
                        "CP_CLOUD_INSTANCE_TYPE" \
                        "$CP_CLOUD_INSTANCE_TYPE"

     # Once all config files are loaded - apply command line parameters to override config files values
    for exp_env_option in "${EXPLICIT_ENV_OPTIONS[@]}"; do
        parse_env_option "$exp_env_option"
        if [ -z "$__parse_env_option_result__" ]; then continue; fi
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                            "$__parse_env_option_result__" \
                            "${!__parse_env_option_result__}"
    done

    if [ -z "$CP_CLUSTER_SSH_KEY" ] || [ ! -f "$CP_CLUSTER_SSH_KEY" ]; then
        print_err "Cluster SSH private key (used to access cloud nodes) is not defined or does not exist. Please specify a valid location using \"-env CP_CLUSTER_SSH_KEY=\""
        return 1
    fi

    set_service_host "CP_API_SRV_EXTERNAL_HOST" "CP_API_SRV_INTERNAL_HOST" && \
    set_service_host "CP_IDP_EXTERNAL_HOST" "CP_IDP_INTERNAL_HOST"  && \
    set_service_host "CP_DOCKER_EXTERNAL_HOST" "CP_DOCKER_INTERNAL_HOST" && \
    set_service_host "CP_EDGE_EXTERNAL_HOST" "CP_EDGE_INTERNAL_HOST"  && \
    set_service_host "CP_GITLAB_EXTERNAL_HOST" "CP_GITLAB_INTERNAL_HOST"

    if [ $? -ne 0 ]; then
        print_err "Unrecoverable error occured while setting services hosts, exiting"
        return 1
    fi

    if [ -z $CP_API_SRV_EXTERNAL_PORT ]; then
        print_warn "CP_API_SRV_EXTERNAL_PORT : API Service external port is not set - cluster internal port will be used by default"
    else
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                            "CP_API_SRV_EXTERNAL_PORT" \
                            "$CP_API_SRV_EXTERNAL_PORT"
    fi

    if [ -z $CP_IDP_EXTERNAL_PORT ]; then
        print_warn "CP_IDP_EXTERNAL_HOST : IdP Service external port is not set - cluster internal port will be used by default"
    else
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                            "CP_IDP_EXTERNAL_PORT" \
                            "$CP_IDP_EXTERNAL_PORT"
    fi

    if [ -z $CP_EDGE_EXTERNAL_PORT ]; then
        print_warn "CP_EDGE_EXTERNAL_PORT : EDGE Service external port is not set - cluster internal port will be used by default"
    else
        update_config_value "$CP_INSTALL_CONFIG_FILE" \
                            "CP_EDGE_EXTERNAL_PORT" \
                            "$CP_EDGE_EXTERNAL_PORT"
    fi

    if [ $services_count == 0 ]; then
        print_warn "No specific services (-s|--service) are specified, ALL will be installed"
        export CP_INSTALL_SERVICES_ALL=1
    fi

    if [ -z "$CP_DEPLOYMENT_ID" ]; then
        export CP_DEPLOYMENT_ID=$(head /dev/urandom | tr -dc a-z | head -c 10)
        print_warn "Deployment ID is not set. Random ID will be used: ${CP_DEPLOYMENT_ID}. Please specify it using \"-id | --deployment-id\" option"
    fi
    update_config_value "$CP_INSTALL_CONFIG_FILE" \
                        "CP_DEPLOYMENT_ID" \
                        "$CP_DEPLOYMENT_ID"
    
    return 0
}

function create_kube_resource {
    local spec_file="$1"
    local updated_spec_file="/tmp/$(basename $spec_file)"
    envsubst < $spec_file > "$updated_spec_file"
    kubectl create -f "$updated_spec_file"
    rm -f "$updated_spec_file"
}

function check_pod_is_ready {
    local pod_name="$1"
    stuck_pod=$(kubectl get pods $pod_name -o json | jq -r 'select(.status.phase != "Running" or ([ .status.conditions[] | select(.type == "Ready" and .status == "False") ] | length ) == 1 ) | .metadata.name')
    if [ -z "$stuck_pod" ]; then
        return 0
    else
        return 1
    fi
}

function check_pod_exists {
    local pod_name="$1"
    kubectl get pods $pod_name >/dev/null 2>&1
    return $?
}

function wait_for_deletion {
    local DEPLOYMENT_NAME=$1

    pods=$(kubectl get po | grep $DEPLOYMENT_NAME | cut -f1 -d' ')
    for p in $pods; do
        print_info "Waiting for pod \"$p\" final deletion and cleanup..."
        while check_pod_exists "$p"; do
            sleep 10
        done
        print_info "Pod $p is deleted"
    done
}

function wait_for_deployment {
    local DEPLOYMENT_NAME=$1
    sleep 5

    print_info "Waiting for deployment/pod $DEPLOYMENT_NAME creation..."
    set -o pipefail
    while "true"; do
        pods=$(kubectl get po 2>/dev/null | grep $DEPLOYMENT_NAME | cut -f1 -d' ')
        [ $? -eq 0 ] && [ "$pods" ] && break
    done
    set +o pipefail

    print_info "Deployment/pod $DEPLOYMENT_NAME created"
    for p in $pods; do
        print_info "Waiting for pod \"$p\" readiness..."
        while ! check_pod_is_ready "$p"; do
            sleep 10
        done
        print_ok "Pod $p is ready"
    done
}

function wait_for_service {
    local service_endpoint="$1"
    local expected_code="${2:-200}"
    sleep 5

    print_info "Waiting for service $service_endpoint and expecting HTTP $expected_code ..."
    while "true"; do
        service_response_code=$(curl -k -s -o /dev/null -I -w "%{http_code}" "$service_endpoint")
        if [ "$service_response_code" == "$expected_code" ]; then
            print_ok "Service $service_endpoint started (returned HTTP $service_response_code)"
            return
        fi
        sleep 10
    done
}

function execute_deployment_command {
    local DEPLOYMENT_NAME=$1
    local CMD="$2"

    pods=$(kubectl get po | grep $DEPLOYMENT_NAME | cut -f1 -d' ')
    for p in $pods; do
        bash -c "kubectl exec -i $p -- $CMD"
    done
}

function create_user_and_db {
    local DEPLOYMENT_NAME=$1
    local USERNAME=$2
    local PASSWORD=$3
    local DBNAME=$4

    if [ "$CP_FORCE_DATA_ERASE" ]; then
        print_info "Dropping user \"$USERNAME\" and database \"$DBNAME\""
        execute_deployment_command $DEPLOYMENT_NAME "psql -U postgres -c \"DROP DATABASE $DBNAME;\""
        execute_deployment_command $DEPLOYMENT_NAME "psql -U postgres -c \"DROP OWNED BY $USERNAME;\""
        execute_deployment_command $DEPLOYMENT_NAME "psql -U postgres -c \"DROP USER $USERNAME;\""
    fi

    execute_deployment_command $DEPLOYMENT_NAME "psql -U postgres -c \"CREATE EXTENSION IF NOT EXISTS pg_trgm;\""
    
    print_info "Creating user $USERNAME"
    execute_deployment_command $DEPLOYMENT_NAME "psql -U postgres -c \"CREATE USER $USERNAME CREATEDB;\""
    execute_deployment_command $DEPLOYMENT_NAME "psql -U postgres -c \"ALTER USER $USERNAME WITH SUPERUSER;\""

    print_info "Setting password for user $USERNAME"
    execute_deployment_command $DEPLOYMENT_NAME "psql -U postgres -c \"ALTER USER $USERNAME WITH PASSWORD '$PASSWORD';\""

    print_info "Creating database $DBNAME"
    execute_deployment_command $DEPLOYMENT_NAME "psql -U postgres -c \"CREATE DATABASE $DBNAME OWNER $USERNAME;\""
}

function delete_deployment_and_service {
    local NAME=$1
    local DATA_DIRS="$2"

    if kubectl get deployments $NAME &> /dev/null; then
        kubectl delete deployments $NAME
    fi

    if kubectl get po $NAME &> /dev/null; then
        kubectl delete po $NAME
    fi

    wait_for_deletion $NAME

    if kubectl get svc $NAME &> /dev/null; then
        kubectl delete svc $NAME
    fi

    local secrets_template=${NAME//[^a-zA-Z_0-9]/-}
    for kube_secret in $(kubectl get secrets  2>/dev/null| grep "$secrets_template" | cut -f1 -d' '); do
        kubectl delete secrets "$kube_secret"
    done

    if [ ! -z "$DATA_DIRS" ] && [ "$CP_FORCE_DATA_ERASE" ]; then
        rm -rf $DATA_DIRS
        print_info "Directory(ies) removed: $DATA_DIRS"
    fi
}

function get_service_cluster_ip {
    local service_name="$1"
    local search_namespace="$2"
    [ ! "$search_namespace" ] && search_namespace="default"

    cluster_ip=$(kubectl get svc --namespace=$search_namespace $service_name 2>/dev/null | tail -n +2 | awk '$1=$1' | cut -f2 -d' ')
    echo "$cluster_ip"
}

function is_ip {
    [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]] && return 0
    return 1
}

function generate_self_signed_ca {
    local ca_cert_path="$CP_COMMON_CERT_DIR/ca-public-cert.pem"
    local ca_key_path="$CP_COMMON_CERT_DIR/ca-private-key.pem"

    if [ -f $ca_cert_path ] && [ -f $ca_key_path ]; then
        print_warn "-> CA Certificate ($ca_cert_path) and private key ($ca_key_path) already exist, new pair will NOT be generated"
        return 0
    fi

    mkdir -p "$CP_COMMON_CERT_DIR"
    openssl req -x509 \
                -new \
                -newkey rsa:2048 \
                -nodes \
                -subj "/CN=Cloud-Pipeline-$CP_DEPLOYMENT_ID" \
                -keyout $ca_key_path \
                -out $ca_cert_path \
                -days 7300
}

function generate_self_signed_key_pair {
    local force_self_sign="$1"
    if [ "$force_self_sign" == "force_self_sign" ]; then
        force_self_sign="true"
        shift
    fi

    local key_path="$1"
    shift

    local cert_path="$1"
    shift

    local subject="$1"
    shift

    local names=""
    if is_ip $subject; then 
        names="IP:$subject"
    else
        names="DNS:$subject"
    fi
    
    for name in "$@"; do
        if is_ip $name; then
            san_name="IP:$name"
        else
            san_name="DNS:$name"
        fi
        names="$names,$san_name"
    done
    
    openssl_config_options="/etc/ssl/openssl.cnf /usr/lib/ssl/openssl.cnf /etc/pki/tls/openssl.cnf"
    for openssl_config in $openssl_config_options; do
        if [ -f $openssl_config ]; then
            openssl_config_file=$openssl_config
            break
        fi
    done

    if [ -z "$openssl_config_file" ]; then
        print_err "OpenSSL config file not found, cannot generate self-signed certificate"
        return 1
    fi

    local key_dir=$(dirname $key_path)
    local cert_dir=$(dirname $cert_path)
    mkdir -p $key_dir
    mkdir -p $cert_dir

    if [ -f $key_path ] && [ -f $cert_path ]; then
        print_warn "-> Certificate ($cert_path) and private key ($key_path) already exist, new pair will NOT be generated"
    else
        if [ "$CP_COMMON_SSL_SELF_SIGNED" == "true" ] || [ "$force_self_sign" == "true" ]; then
            openssl req -x509 -new -newkey rsa:2048 -nodes -subj "/CN=$subject" \
                            -keyout $key_path \
                            -out $cert_path \
                            -days 7300 \
                            -reqexts SAN \
                            -extensions SAN \
                            -config <(cat $openssl_config_file \
                                <(printf "\n[SAN]\nsubjectAltName=$names"))
        else
            generate_self_signed_ca

            local csr_dir=$(dirname $cert_path)
            local csr_path=${csr_dir}/$(basename $cert_path).csr
            openssl req -new -newkey rsa:2048 -nodes -subj "/CN=$subject" \
                            -keyout $key_path \
                            -out $csr_path \
                            -reqexts SAN \
                            -extensions SAN \
                            -config <(cat $openssl_config_file \
                                <(printf "\n[SAN]\nsubjectAltName=$names"))
            openssl x509 -req \
                            -in $csr_path  \
                            -out $cert_path \
                            -days 7300 \
                            -CA "$CP_COMMON_CERT_DIR/ca-public-cert.pem" \
                            -CAkey "$CP_COMMON_CERT_DIR/ca-private-key.pem" \
                            -CAcreateserial \
                            -extensions SAN \
                            -extfile <(cat $openssl_config_file \
                                <(printf "\n[SAN]\nsubjectAltName=$names"))

            # Add CA cert to the generated PEM
            cat "$CP_COMMON_CERT_DIR/ca-public-cert.pem" >> $cert_path
        fi
    fi
}

function generate_rsa_key_pair {
    local key_path="$1"
    local pub_path="$2"
    local stringify="$3"
    local cert_path="$4"

    if [ -f $key_path ] && [ -f $pub_path ]; then
        print_warn "-> Public key ($pub_path) and private key ($key_path) already exist, new RSA pair will NOT be generated"
    else
        openssl genpkey -algorithm RSA -out $key_path.tmp -pkeyopt rsa_keygen_bits:1024
        openssl rsa -pubout -in $key_path.tmp -out $pub_path.tmp

        if [ "$cert_path" ]; then
            openssl req -x509 -new -nodes -subj "/CN=Cloud pipeline" \
                        -days 7300 \
                        -sha256 \
                        -extensions v3_ca \
                        -key $key_path.tmp \
                        -out $cert_path
        fi

        if [ "$stringify" == "stringify" ]; then
            # Remove first and last line (e.g. BEGIN/END CERTIFICATE) and also remove new lines
            sed '$d' < $key_path.tmp | sed "1d" | tr -d '\n' > $key_path
            sed '$d' < $pub_path.tmp | sed "1d" | tr -d '\n' > $pub_path
            rm -f $key_path.tmp $pub_path.tmp
        else
            mv $key_path.tmp $key_path
            mv $pub_path.tmp $pub_path
        fi
    fi
}

function is_service_requested {
    local service=${1//[^a-zA-Z_0-9]/_}
    [ "${!service}" == "1" ] || [ "$CP_INSTALL_SERVICES_ALL" == "1" ]
    return $?
}

function is_install_requested {
    [ "$CP_INSTALLER_COMMAND" = "install" ]
    return $?
}