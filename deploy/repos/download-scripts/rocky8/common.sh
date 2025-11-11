#!/bin/bash

set -e

# Common packages
yum install -y --downloadonly --downloaddir=/rpmcache --installroot=/tmp/installroot --releasever=/ \
    sudo \
    python2 \
    git \
    curl \
    wget \
    fuse \
    python2-docutils \
    tzdata \
    acl \
    coreutils-single \
    libtool-ltdl \
    openssh-server \
    gnupg \
    nfs-utils \
    cifs-utils \
    tcl-devel
