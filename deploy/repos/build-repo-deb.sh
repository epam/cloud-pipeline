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
            python -y

curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2

if ! aws --version > /dev/null 2>&1; then
    echo "INFO: awscli is not installed, proceeding with installation"
    pip install awscli
fi


rm -f /etc/yum.repos.d/cloud-pipeline.repo && \
yum install -y centos-release-scl-rh && \
yum install -y zstd

for repo_file in /etc/yum.repos.d/*.repo; do
    sed -i '/download.example/d' "$repo_file"
    sed -i 's/mirror.centos.org/vault.centos.org/g' "$repo_file"
    if grep -q 'baseurl' "$repo_file"; then
            sed -i 's/^#baseurl=/baseurl=/g' "$repo_file"
            sed -i 's/^metalink=/#metalink=/g' "$repo_file"
            sed -i 's/^mirrorlist=/#mirrorlist=/g' "$repo_file"
    fi
done

if ! deb-s3 > /dev/null 2>&1; then
    echo "INFO: deb-s3 is not installed, proceeding with installation"
    yum -y --enablerepo=centos-sclo-rh -y install rh-ruby30 && \
    yum -y --enablerepo=centos-sclo-rh -y install rh-ruby30-ruby-devel && \
    source /opt/rh/rh-ruby30/enable  && \
    gem install bundler -v '2.0' && \
    gem install nokogiri && \
    git clone https://github.com/deb-s3/deb-s3 && \
    cd deb-s3 && \
    git checkout 9fc17226d4f7d18571dd0adde2dc079c751d54c9  && \
    bundle install && \
    \cp $(pwd)/bin/deb-s3 /usr/local/bin/ && \
    \cp $(pwd)/lib/deb /usr/local/lib/ -r && \
    cd - && \
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

    # Replace all "%3a" with ":" as otherwise s3 urls are broken
    for f in ${CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR}/*.deb; do
        fn=$(echo "$f" | sed 's/%3a/:/g')
        /bin/mv "$f" "$fn"
    done

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
                    --visibility=nil \
                    $CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR/*.deb
    rm -rf "$CP_REPOS_DOWNLOAD_SCRIPT_TMP_DIR"
    
done

# Replace all "+" with " " (space) and this is a replacement in S3 urls
l=$(aws s3 ls --recursive s3://$CP_REPOS_BUCKET/$CP_REPOS_BUCKET_PREFIX/$CP_REPOS_DOWNLOAD_OS/$VERSION_ID/ | grep deb | cut -f2- -d'/' | grep '+')
for f in $(echo $l); do
    dir=$(dirname "$f")
    fn=$(basename "$f")
    
    echo $f

    aws s3 cp "s3://$CP_REPOS_BUCKET/tools/$f" ./
    dir2=$(echo "$dir" | sed 's/+/ /g')
    fn2=$(echo "$fn" | sed 's/+/ /g')
    /bin/mv "$fn" "$fn2"
    aws s3 cp "$fn2" "s3://$CP_REPOS_BUCKET/tools/$dir2/$fn2"
    rm -f "$fn2" 
    aws s3 rm "s3://$CP_REPOS_BUCKET/tools/$f"
    echo ----
done
