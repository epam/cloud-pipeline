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
    --gpg-private)
    CP_REPOS_GPG_PRIV_KEY="$2"
    shift
    shift
    ;;
    --gpg-public)
    CP_REPOS_GPG_PUB_KEY="$2"
    shift
    shift
    ;;
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

if  [ -z "$CP_REPOS_GPG_PRIV_KEY" ] || \
    [ -z "$CP_REPOS_GPG_PUB_KEY" ] || \
    [ -z "$CP_REPOS_BUCKET" ] || \
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
            python-pip -y

if ! aws --version > /dev/null 2>&1; then
    echo "INFO: awscli is not installed, proceeding with installation"
    pip install awscli
fi

if ! deb-s3 > /dev/null 2>&1; then
    echo "INFO: deb-s3 is not installed, proceeding with installation"
    yum install ruby-2.0.0.648-36.el7.x86_64 -y
    gem install bundler -v '1.17.3'
    git clone https://github.com/sidoruka/deb-s3
    cd deb-s3
    git checkout 058a559484b5d62441bf47229a26687410bf605d
    bundle install
    \cp $(pwd)/bin/deb-s3 /usr/local/bin/
    \cp $(pwd)/lib/deb /usr/local/lib/ -r
    cd -
    rm -rf deb-s3
fi


CP_REPOS_GPG_TMP_DIR=$(mktemp -d)
aws s3 cp "$CP_REPOS_GPG_PRIV_KEY" "${CP_REPOS_GPG_TMP_DIR}/"
aws s3 cp "$CP_REPOS_GPG_PUB_KEY" "${CP_REPOS_GPG_TMP_DIR}/"
gpg --import "${CP_REPOS_GPG_TMP_DIR}/$(basename $CP_REPOS_GPG_PUB_KEY)"
gpg --allow-secret-key-import --import "${CP_REPOS_GPG_TMP_DIR}/$(basename $CP_REPOS_GPG_PRIV_KEY)"
CP_REPOS_GPG_PUB_KEY_ID=$(gpg --list-keys --with-colons | awk -F: '/^pub:/ { print $5 }')
if [ -z "$CP_REPOS_GPG_PUB_KEY_ID" ]; then
    echo "ERROR: Failed to get the ID of the imported GPG key, exiting"
    exit 1
fi
rm -rf "$CP_REPOS_GPG_TMP_DIR"

CP_REPOS_DOWNLOAD_OS_DETAILS=$(docker run -i --rm ${CP_REPOS_DOWNLOAD_OS}:${CP_REPOS_DOWNLOAD_OS_VERSION} cat /etc/os-release)
eval "$CP_REPOS_DOWNLOAD_OS_DETAILS"

for CP_REPOS_DOWNLOAD_SCRIPT in "${POSITIONAL[@]}"; do
    CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR=$(mktemp -d)
    CP_REPOS_DOWNLOAD_SCRIPT=$(realpath $CP_REPOS_DOWNLOAD_SCRIPT)
    if [ ! -f "$CP_REPOS_DOWNLOAD_SCRIPT" ]; then
        echo "WARN: $CP_REPOS_DOWNLOAD_SCRIPT is not found, skipping it"
        continue
    fi 
    docker run  -it \
                --rm \
                -v ${CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR}:/var/cache/apt/archives \
                -v ${CP_REPOS_DOWNLOAD_SCRIPT}:/download \
                ${CP_REPOS_DOWNLOAD_OS}:${CP_REPOS_DOWNLOAD_OS_VERSION} \
                bash /download

    if [ $? -ne 0 ]; then
        echo "ERROR: Unable to download the packages via $CP_REPOS_DOWNLOAD_SCRIPT script, skipping it"
        continue
    fi

    echo "INFO: packages are downloaded via $CP_REPOS_DOWNLOAD_SCRIPT, uploading to S3"
    deb-s3 upload   --bucket "$CP_REPOS_BUCKET" \
                    --prefix "$CP_REPOS_BUCKET_PREFIX/$CP_REPOS_DOWNLOAD_OS/$VERSION_ID" \
                    --codename stable \
                    --component main \
                    --access-key-id="$AWS_ACCESS_KEY_ID" \
                    --secret-access-key="$AWS_SECRET_ACCESS_KEY" \
                    --s3-region="$AWS_DEFAULT_REGION" \
                    --sign="$CP_REPOS_GPG_PUB_KEY_ID" \
                    --arch amd64 \
                    $CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR/*.deb
    rm -rf "$CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR"
done
