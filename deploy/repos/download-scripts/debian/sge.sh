#!/bin/bash

set -e

# SGE
CP_CAP_SGE_VERSION="${CP_CAP_SGE_VERSION:-8.1.9+dfsg-4*}"
apt-get update
apt-get install --download-only libtinfo5 db-util libip4tc0 libkmod2 -y --allow-unauthenticated
mv /var/cache/apt/archives/*.deb /tmp/
apt-get install libtinfo5 gnupg curl apt-transport-https -y --allow-unauthenticated

curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/cloud-pipeline.key" | apt-key add -
sed -i "1 i\deb https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/ubuntu/16.04 stable main" /etc/apt/sources.list
apt-get update -y --allow-insecure-repositories

apt-get install --download-only -t stable -y --allow-unauthenticated \
    gridengine-exec="$CP_CAP_SGE_VERSION" \
    gridengine-client="$CP_CAP_SGE_VERSION" \
    gridengine-common="$CP_CAP_SGE_VERSION" \
    gridengine-master="$CP_CAP_SGE_VERSION"
mv /tmp/*.deb /var/cache/apt/archives/
