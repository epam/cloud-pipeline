#!/bin/bash

set -e

# Common packages
yum install -y epel-release
yum install -y --downloadonly --downloaddir=/rpmcache \
    automake \
    fuse \
    fuse-devel \
    gcc-c++ \
    libcurl-devel \
    libxml2-devel \
    openssl-devel
