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
# See the License for the specific language governing peKrmissions and
# limitations under the License.

install_scripts_dir=$(dirname $0)/../../install/app
source $install_scripts_dir/format-utils.sh
source $install_scripts_dir/install-common.sh

function parse_options {
    POSITIONAL=()
    while [[ $# -gt 0 ]]
    do
    key="$1"

    case $key in
        --users)
        export CP_SYNC_USERS=1
        shift
        ;;
        --tools)
        export CP_SYNC_TOOLS=1
        shift
        ;;
        --source-url)
        export CP_ENV_SYNC_SRC_URL="$2"
        shift
        shift
        ;;
        --source-token)
        export CP_ENV_SYNC_SRC_TOKEN="$2"
        shift
        shift
        ;;
        --target-url)
        export CP_ENV_SYNC_TARGET_URL="$2"
        shift
        shift
        ;;
        --target-token)
        export CP_ENV_SYNC_TARGET_TOKEN="$2"
        shift
        shift
        ;;
        --docker-cmd)
        export CP_ENV_SYNC_DOCKER_CMD="$2"
        shift
        shift
        ;;
        *)    # unknown option
        POSITIONAL+=("$1") # save it in an array for later
        shift
        ;;
    esac
    done
    set -- "${POSITIONAL[@]}" # restore positional parameters

    valid_params=0
    if [ -z "$CP_ENV_SYNC_SRC_URL" ] ; then
        print_err "Source deployment URL is not defined. Please, specify it using \"--source-url\" flag"
        valid_params=1
    fi
    if [ -z "$CP_ENV_SYNC_SRC_TOKEN" ] ; then
        print_err "Token for the source deployment's API access is not defined. Please, specify it using \"--source-token\" flag"
        valid_params=1
    fi
    if [ -z "$CP_ENV_SYNC_TARGET_URL" ] ; then
        print_err "Target deployment URL is not defined. Please, specify it using \"--target-url\" flag"
        valid_params=1
    fi
    if [ -z "$CP_ENV_SYNC_TARGET_TOKEN" ] ; then
        print_err "Token for the target deployment's API access is not defined. Please, specify it using \"--target-token\" flag"
        valid_params=1
    fi
    export CP_ENV_SYNC_DOCKER_CMD="${CP_ENV_SYNC_DOCKER_CMD:-docker}"
    return $valid_params
}

function prepare_environment {
    if ! check_installed "wget"; then
        yum install -q -y wget
    fi
    if ! check_installed "python"; then
        print_info "Installing Python2"
        CP_CONDA_DISTRO_URL="${CP_CONDA_DISTRO_URL:-https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/python/2/Miniconda2-4.7.12.1-Linux-x86_64.tar.gz}"
        print_info "Getting python distro from $CP_CONDA_DISTRO_URL"
        wget -q "${CP_CONDA_DISTRO_URL}" -O "/tmp/conda.tgz" &> /dev/null
        if [ $? -ne 0 ]; then
            print_err "Can't download the python distro"
            return 1
        fi
        tar -zxf "/tmp/conda.tgz" -C "/"
        rm -f "/tmp/conda.tgz"
        print_info "Python distro is installed into /conda"
        echo "alias python=/conda/bin/python2" >> ~/.bashrc
        source ~/.bashrc
    elif [ -f /conda/bin/python2 ]; then
        print_info "Conda is configured already"
    else
        print_info "Python is installed already, checking dependencies"
        python -c "import requests;import urllib3" &> /dev/null
        if [ $? -ne 0 ]; then
            print_info "Not all dependencies are met, trying to add them"
            pip --help
            if [ $? -ne 0 ]; then
                print_info "pip not found, proceed with the installation"
                curl https://bootstrap.pypa.io/get-pip.py | python -
                pip --help
                if [ $? -ne 0 ]; then
                    print_err "Can't install pip, exiting"
                    exit 1
                fi
            fi
            local CP_REPO_PYPI_BASE_URL_DEFAULT="${CP_REPO_PYPI_BASE_URL_DEFAULT:-http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple}"
            local CP_REPO_PYPI_TRUSTED_HOST_DEFAULT="${CP_REPO_PYPI_TRUSTED_HOST_DEFAULT:-cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com}"
            local CP_PIP_PACKAGE_INDEX="--index-url $CP_REPO_PYPI_BASE_URL_DEFAULT --trusted-host $CP_REPO_PYPI_TRUSTED_HOST_DEFAULT"
            python -m pip install $CP_PIP_PACKAGE_INDEX -q urllib3 requests &> /dev/null
            if [ $? -ne 0 ]; then
                print_err "Can't install dependencies, exiting"
                exit 1
            fi
        else
            print_info "All required dependencies are installed"
        fi
    fi
    return 0
}

function check_docker {
    $CP_ENV_SYNC_DOCKER_CMD info > /dev/null 2>&1
    return $?
}

parse_options "$@"
if [ $? -ne 0 ]; then
  print_err "Unable to setup sync configuration, exiting"
  exit 1
fi

prepare_environment
if [ $? -ne 0 ]; then
    print_err "Unable to setup sync environment, exiting"
    exit 1
fi

if [ ! -z "$CP_SYNC_USERS" ] ; then
    print_ok "Start users synchronization from '$CP_ENV_SYNC_SRC_URL' to '$CP_ENV_SYNC_TARGET_URL'"
    python sync_users.py $CP_ENV_SYNC_SRC_URL $CP_ENV_SYNC_SRC_TOKEN $CP_ENV_SYNC_TARGET_URL $CP_ENV_SYNC_TARGET_TOKEN
    if [ $? -ne 0 ]; then
        print_err "Errors during users sync"
    else
        print_ok "Users synchronization is finished"
    fi
fi

if [ ! -z "$CP_SYNC_TOOLS" ] ; then
    check_docker
    if [ $? -ne 0 ]; then
        print_err "Docker is not available, tool sync is not possible, exiting"
        exit 1
    fi
    print_ok "Start tool synchronization from '$CP_ENV_SYNC_SRC_URL' to '$CP_ENV_SYNC_TARGET_URL'"
    python sync_tools.py $CP_ENV_SYNC_SRC_URL \
                          $CP_ENV_SYNC_SRC_TOKEN \
                          $CP_ENV_SYNC_TARGET_URL \
                          $CP_ENV_SYNC_TARGET_TOKEN \
                          $CP_ENV_SYNC_DOCKER_CMD
    if [ $? -ne 0 ]; then
        print_err "Errors during tools sync"
    else
        print_ok "Tools synchronization is finished"
    fi
fi

