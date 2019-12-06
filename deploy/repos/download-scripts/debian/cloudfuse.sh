#!/bin/bash

set -e

# gcsfuse
apt update
apt install -y gnupg lsb-release apt-transport-https curl
GCSFUSE_REPO=gcsfuse-`lsb_release -c -s`
echo "deb http://packages.cloud.google.com/apt $GCSFUSE_REPO main" | tee /etc/apt/sources.list.d/gcsfuse.list
curl -s https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add -

# azure blobfuse
curl -s -O https://packages.microsoft.com/config/ubuntu/16.04/packages-microsoft-prod.deb
dpkg -i packages-microsoft-prod.deb

apt-get update -y
apt-get install --download-only -y gcsfuse blobfuse fuse
