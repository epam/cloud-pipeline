#!/bin/bash

set -e

# Common packages
yum install -y epel-release
yum install -y --downloadonly --downloaddir=/rpmcache \
    sudo \
    python \
    git \
    curl \
    wget \
    fuse \
    python-docutils \
    tzdata \
    acl \
    coreutils \
    libtool-ltdl \
    openssh-server \
    gnupg \
    nfs-utils \
    cifs-utils \
    tcl-devel
