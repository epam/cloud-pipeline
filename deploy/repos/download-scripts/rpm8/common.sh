#!/bin/bash

set -e

# Common packages
yum install -y --downloadonly --downloaddir=/rpmcache --installroot=/tmp/installroot --releasever=/ \
    sudo \
    git \
    curl \
    wget \
    fuse \
    tzdata \
    acl \
    coreutils-single \
    libtool-ltdl \
    openssh-server \
    gnupg \
    nfs-utils \
    cifs-utils \
    tcl-devel
