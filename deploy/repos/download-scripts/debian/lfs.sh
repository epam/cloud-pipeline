#!/bin/bash

set -e

# LizardFS
apt update
apt install lsb-release gnupg wget -y

source /etc/os-release
if [ "$ID" == "debian" ]; then
    # If it is debian (especially v8 - "jessie") - use /debian/ repository
    _repository=debian
    _release="$(lsb_release -sc)"
else
    # Use xenial release even for 18.04
    _repository=ubuntu
    _release=xenial
fi


wget -O - http://packages.lizardfs.com/lizardfs.key | apt-key add -
echo "deb [trusted=yes] http://packages.lizardfs.com/$_repository/$_release $_release main" > /etc/apt/sources.list.d/lizardfs.list
apt update
apt install --download-only lizardfs-chunkserver lizardfs-master lizardfs-client -y
