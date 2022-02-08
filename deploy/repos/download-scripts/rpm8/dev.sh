#!/bin/bash

set -e

# Common packages
yum install -y --downloadonly --downloaddir=/rpmcache --installroot=/tmp/installroot --releasever=/ \
    automake \
    fuse \
    fuse-devel \
    gcc-c++ \
    libcurl-devel \
    libxml2-devel \
    openssl-devel
