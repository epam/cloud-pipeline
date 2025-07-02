#!/bin/bash

set -e

# LizardFS
apt update
apt install lsb-release gnupg wget curl apt-transport-https -y

curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/cloud-pipeline.key" | apt-key add -

source /etc/os-release
if [ "$ID" == "debian" ]; then
    sed -i "1 i\deb https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/debian/8 stable main" /etc/apt/sources.list
else
    sed -i "1 i\deb https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/ubuntu/16.04 stable main" /etc/apt/sources.list
fi

apt-get update -y --allow-insecure-repositories
apt install -y --download-only -t stable \
    lizardfs-chunkserver \
    lizardfs-master \
    lizardfs-client \
    bash-completion \
    libfuse2 \
    libexpat1 \
    ucf \
    libxml2
