#!/bin/bash

set -e

# LizardFS
curl -s http://packages.lizardfs.com/yum/el7/lizardfs.repo > /etc/yum.repos.d/lizardfs.repo && \
yum --disablerepo="*" --enablerepo="lizardfs" -q update
yum -y install epel-release
yum install -y --downloadonly --downloaddir=/rpmcache lizardfs-chunkserver lizardfs-master lizardfs-client
