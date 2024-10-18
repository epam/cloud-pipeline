#!/bin/bash

set -e

# SGE
curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/centos/7/cloud-pipeline.repo" > /etc/yum.repos.d/cloud-pipeline.repo && \
yum --enablerepo=cloud-pipeline install \
    -y --downloadonly --downloaddir=/rpmcache \
    yum-priorities

yum install -y --downloadonly --downloaddir=/rpmcache --installroot=/tmp/installroot --releasever=/ \
    gridengine \
    gridengine-debuginfo \
    gridengine-devel \
    gridengine-drmaa4ruby \
    gridengine-execd \
    gridengine-guiinst \
    gridengine-qmaster \
    gridengine-qmon
