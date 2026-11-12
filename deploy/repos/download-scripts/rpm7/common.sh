#!/bin/bash

set -e

# Common packages
yum install -y epel-release
yum install -y --downloadonly --downloaddir=/rpmcache \
    sudo \
    git \
    curl \
    wget \
    fuse \
    tzdata \
    acl \
    coreutils \
    libtool-ltdl \
    openssh-server \
    gnupg \
    nfs-utils \
    cifs-utils \
    tcl-devel
