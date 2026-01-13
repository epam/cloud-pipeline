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
        ;;
        --subnet-id)
        _SUBNET_ID="$2"
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

if [ -z "$_REGION" ] || \
    [ -z "$_INSTANCE_PROFILE" ] || \
    [ -z "$_SUBNET_ID" ]; then
    echo "Usage: build.sh --region us-east-1 --instance-profile SSM_Role --subnet-id subnet-xxxxxxx --type cpu"
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

    echo >> $_config
    echo "region = \"$_REGION\"" >> $_config
    echo "iam_instance_profile = \"$_INSTANCE_PROFILE\"" >> $_config
    echo "subnet_id = \"$_SUBNET_ID\"" >> $_config

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
