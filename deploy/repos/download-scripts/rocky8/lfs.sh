#!/bin/bash

set -e
curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/rocky/8.7/cloud-pipeline.repo" > /etc/yum.repos.d/cloud-pipeline.repo && \
yum --enablerepo=cloud-pipeline install \
    -y --downloadonly --downloaddir=/rpmcache \
    yum-priorities

yum install -y --downloadonly --downloaddir=/rpmcache --installroot=/tmp/installroot --releasever=/ \
    lizardfs-chunkserver-3.12.0-0el8.x86_64 \
    lizardfs-master-3.12.0-0el8.x86_64 \
    lizardfs-client-3.12.0-0el8.x86_64
