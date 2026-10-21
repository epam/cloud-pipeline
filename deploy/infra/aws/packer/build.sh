#!/bin/bash

POSITIONAL=()
while [[ $# -gt 0 ]]; do
    case $1 in
        --region)
        _REGION="$2"
        shift
        shift
        ;;
        --instance-profile)
        _INSTANCE_PROFILE="$2"
        shift
        shift
        ;;
        --source-ami)
        _SOURCE_AMI="$2"
        shift
        shift
        ;;
        --nvidia-driver-version)
        _NVIDIA_DRIVER_VERSION="$2"
        shift
        shift
        ;;
        --nvidia-driver-url-prefix)
        _NVIDIA_DRIVER_URL_PREFIX="$2"
        shift
        shift
        ;;
        --subnet-id)
        _SUBNET_ID="$2"
        shift
        shift
        ;;
        --security-group-ids)
        _SECURITY_GROUP_IDS="$2"
        shift
        shift
        ;;
        --ssh-keypair-name)
        _SSH_KEYPAIR_NAME="$2"
        shift
        shift
        ;;
        --ssh-private-key-file)
        _SSH_PRIVATE_KEY_FILE="$2"
        shift
        shift
        ;;
        --ssh-interface)
        _SSH_INTERFACE="$2"
        shift
        shift
        ;;
        --temporary-sg-source-cidrs)
        _TEMPORARY_SG_SOURCE_CIDRS="$2"
        shift
        shift
        ;;
        --type)
        _TYPE="$2"
        shift
        shift
        ;;
        *)
        POSITIONAL+=("$1")
        shift
        ;;
    esac
done
set -- "${POSITIONAL[@]}"

_SSH_INTERFACE="${_SSH_INTERFACE:-session_manager}"

if [ -z "$_REGION" ] || \
    [ -z "$_SUBNET_ID" ]; then
    echo "Usage: build.sh --region us-east-1 --instance-profile SSM_Role --subnet-id subnet-xxxxxxx --type cpu [--source-ami ami-xxxxxxx] [--nvidia-driver-version VERSION] [--nvidia-driver-url-prefix URL] [--ssh-interface session_manager|private_ip] [--security-group-ids sg-xxxxxxx[,sg-yyyyyyy]] [--temporary-sg-source-cidrs CIDR[,CIDR]] [--ssh-keypair-name NAME --ssh-private-key-file PATH]"
    exit 1
fi

if [ "$_SSH_INTERFACE" != "session_manager" ] && [ "$_SSH_INTERFACE" != "private_ip" ]; then
    echo "[ERROR] --ssh-interface shall be either \"session_manager\" or \"private_ip\""
    exit 1
fi

# SSM requires an instance profile to manage the instance, a direct ssh connection does not
if [ "$_SSH_INTERFACE" == "session_manager" ] && [ -z "$_INSTANCE_PROFILE" ]; then
    echo "[ERROR] --instance-profile is required for the \"session_manager\" ssh interface"
    exit 1
fi

if [[ -n "$_SSH_KEYPAIR_NAME" && -z "$_SSH_PRIVATE_KEY_FILE" ]] || \
    [[ -n "$_SSH_PRIVATE_KEY_FILE" && -z "$_SSH_KEYPAIR_NAME" ]]; then
    echo "[ERROR] Both --ssh-keypair-name and --ssh-private-key-file shall be specified to use an existing key pair"
    exit 1
fi

_types_list=()
if [ -z "$_TYPE" ] || [ "$_TYPE" == "all" ]; then
    _types_list=("cpu" "gpu")
else
    _types_list=($_TYPE)
fi

for _type_to_build in ${_types_list[@]}; do
    _config="$(mktemp --suffix .pkrvars.hcl)"
    \cp "$_type_to_build/ami.pkrvars.hcl" $_config
    sed -i '/region/d' $_config
    sed -i '/subnet_id/d' $_config
    sed -i '/iam_instance_profile/d' $_config
    sed -i '/^ssh_interface[[:space:]]*=/d' $_config

    echo >> $_config
    echo "region = \"$_REGION\"" >> $_config
    echo "iam_instance_profile = \"$_INSTANCE_PROFILE\"" >> $_config
    echo "subnet_id = \"$_SUBNET_ID\"" >> $_config
    echo "ssh_interface = \"$_SSH_INTERFACE\"" >> $_config

    # If not set - the latest Amazon Linux 2023 AMI with the 6.1 kernel is used
    if [ "$_SOURCE_AMI" ]; then
        sed -i '/^source_ami[[:space:]]*=/d' $_config
        echo "source_ami = \"$_SOURCE_AMI\"" >> $_config
    fi

    # If not set - a temporary security group and a temporary key pair are created by packer
    if [ "$_SECURITY_GROUP_IDS" ]; then
        sed -i '/^security_group_ids[[:space:]]*=/d' $_config
        echo "security_group_ids = [$(echo "$_SECURITY_GROUP_IDS" | tr -d '[:space:]' | sed 's/[^,][^,]*/"&"/g')]" >> $_config
    fi
    if [ "$_TEMPORARY_SG_SOURCE_CIDRS" ]; then
        sed -i '/^temporary_security_group_source_cidrs[[:space:]]*=/d' $_config
        echo "temporary_security_group_source_cidrs = [$(echo "$_TEMPORARY_SG_SOURCE_CIDRS" | tr -d '[:space:]' | sed 's/[^,][^,]*/"&"/g')]" >> $_config
    fi
    if [ "$_SSH_KEYPAIR_NAME" ]; then
        sed -i '/^ssh_keypair_name[[:space:]]*=/d' $_config
        sed -i '/^ssh_private_key_file[[:space:]]*=/d' $_config
        echo "ssh_keypair_name = \"$_SSH_KEYPAIR_NAME\"" >> $_config
        echo "ssh_private_key_file = \"$_SSH_PRIVATE_KEY_FILE\"" >> $_config
    fi

    # If not set - the default Nvidia driver version and the public Nvidia locations are used
    if [ "$_NVIDIA_DRIVER_VERSION" ]; then
        sed -i '/^nvidia_driver_version[[:space:]]*=/d' $_config
        echo "nvidia_driver_version = \"$_NVIDIA_DRIVER_VERSION\"" >> $_config
    fi
    if [ "$_NVIDIA_DRIVER_URL_PREFIX" ]; then
        sed -i '/^nvidia_driver_url_prefix[[:space:]]*=/d' $_config
        echo "nvidia_driver_url_prefix = \"$_NVIDIA_DRIVER_URL_PREFIX\"" >> $_config
    fi

    _packer_bin="$(mktemp -d)"
    cd $_packer_bin
    wget -q https://releases.hashicorp.com/packer/1.14.3/packer_1.14.3_linux_amd64.zip
    if [ $? -ne 0 ]; then
        echo "[ERROR] Cannot download packer"
        exit 1
    fi
    unzip packer_1.14.3_linux_amd64.zip &> /dev/null
    if [ $? -ne 0 ]; then
        echo "[ERROR] Cannot unzip packer"
        exit 1
    fi
    cd - &> /dev/null

    $_packer_bin/packer init . && $_packer_bin/packer build --var-file=$_config .

    rm -rf $_packer_bin
    rm -f $_config
done
