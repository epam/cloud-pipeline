#!/usr/bin/env bash

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

POSITIONAL=()
while [[ $# -gt 0 ]]
do
key="$1"
case $key in
    --os)
    CP_REPOS_DOWNLOAD_OS="$2"
    shift
    shift
    ;;
    --bucket)
    CP_REPOS_BUCKET="$2"
    shift
    shift
    ;;
    --prefix)
    CP_REPOS_BUCKET_PREFIX="$2"
    shift
    shift
    ;;
    *)                 # unknown option
    POSITIONAL+=("$1") # save it in an array for later use as a script path
    shift
    ;;
esac
done

if ! docker --version > /dev/null 2>&1; then
    echo "ERROR: docker is not installed, exiting"
    exit 1
fi

if  [ -z "$CP_REPOS_BUCKET" ] || \
    [ -z "$CP_REPOS_BUCKET_PREFIX" ] || \
    [ -z "$CP_REPOS_DOWNLOAD_OS" ]; then
    echo "ERROR: required parameters are not set, exiting"
    exit 1
fi

if [ "${#POSITIONAL[@]}" == "0" ]; then
    echo "ERROR: no download scripts are provided, exiting"
    exit 1
fi

if [[ "$CP_REPOS_DOWNLOAD_OS" == *":"* ]]; then
    IFS=':' read -ra CP_REPOS_DOWNLOAD_OS_ARR <<< "$CP_REPOS_DOWNLOAD_OS"
    CP_REPOS_DOWNLOAD_OS="${CP_REPOS_DOWNLOAD_OS_ARR[0]}"
    CP_REPOS_DOWNLOAD_OS_VERSION="${CP_REPOS_DOWNLOAD_OS_ARR[1]}"
else
    CP_REPOS_DOWNLOAD_OS_VERSION='latest'
    echo "WARN: OS ($CP_REPOS_DOWNLOAD_OS) version is not set, $CP_REPOS_DOWNLOAD_OS_VERSION will be used"
fi

export LANG=en_US.UTF-8
export LANGUAGE=en_US.UTF-8
export LC_ALL=en_US.UTF-8
localedef -v -c -i en_US -f UTF-8 en_US.UTF-8

yum install curl \
            wget \
            python \
            python-pip \
            yum-priorities \
            yum-utils \
            createrepo -y

if ! aws --version > /dev/null 2>&1; then
    echo "INFO: awscli is not installed, proceeding with installation"
    pip install awscli
fi

CP_REPOS_LOCAL_DIR=$(mktemp -d)
CP_REPOS_S3_DIR="s3://$CP_REPOS_BUCKET/$CP_REPOS_BUCKET_PREFIX/$CP_REPOS_DOWNLOAD_OS/$CP_REPOS_DOWNLOAD_OS_VERSION"
aws s3 cp "$CP_REPOS_S3_DIR" "$CP_REPOS_LOCAL_DIR" --recursive

for CP_REPOS_DOWNLOAD_SCRIPT in "${POSITIONAL[@]}"; do
    CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR=$(mktemp -d)
    CP_REPOS_DOWNLOAD_SCRIPT=$(realpath $CP_REPOS_DOWNLOAD_SCRIPT)
    if [ ! -f "$CP_REPOS_DOWNLOAD_SCRIPT" ]; then
        echo "WARN: $CP_REPOS_DOWNLOAD_SCRIPT is not found, skipping it"
        continue
    fi 
    docker run  -it \
                --rm \
                -v ${CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR}:/rpmcache \
                -v ${CP_REPOS_DOWNLOAD_SCRIPT}:/download \
                ${CP_REPOS_DOWNLOAD_OS}:${CP_REPOS_DOWNLOAD_OS_VERSION} \
                bash /download

    if [ $? -ne 0 ]; then
        echo "ERROR: Unable to download the packages via $CP_REPOS_DOWNLOAD_SCRIPT script, skipping it"
        continue
    fi

    \cp "$CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR/*" "$CP_REPOS_LOCAL_DIR/"
    rm -rf "$CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR"
done

echo "INFO: packages are downloaded, uploading to S3"
createrepo "$CP_REPOS_LOCAL_DIR"
echo "INFO: packages are uploaded to S3, generating repo metadata"
cat > "${CP_REPOS_LOCAL_DIR}/cloud-pipeline.repo" << EOF
[cloud-pipeline]
name=Cloud Pipeline Packages Cache
baseurl=https://${CP_REPOS_BUCKET}.s3.amazonaws.com/${CP_REPOS_BUCKET_PREFIX}/${CP_REPOS_DOWNLOAD_OS}/${CP_REPOS_DOWNLOAD_OS_VERSION}/
enabled=1
gpgcheck=0
priority=1
EOF

aws s3 sync "$CP_REPOS_LOCAL_DIR" "$CP_REPOS_S3_DIR"
rm -rf "$CP_REPOS_LOCAL_DIR"
