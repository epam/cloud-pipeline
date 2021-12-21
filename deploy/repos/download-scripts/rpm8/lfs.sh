#!/bin/bash

set -e

# LizardFS
# https://dev.lizardfs.com/packages/ - Centos 8+
# https://dev.lizardfs.com/old-packages/ - Centos 6,7
curl -s https://dev.lizardfs.com/packages/centos.lizardfs.repo > /etc/yum.repos.d/lizardfs.repo && \
yum --disablerepo="*" --enablerepo="lizardfs" -q update && \
yum install -y --downloadonly --downloaddir=/rpmcache --installroot=/tmp/installroot --releasever=/ \
    lizardfs-chunkserver-3.12.0-0el8.x86_64 \
    lizardfs-master-3.12.0-0el8.x86_64 \
    lizardfs-client-3.12.0-0el8.x86_64
