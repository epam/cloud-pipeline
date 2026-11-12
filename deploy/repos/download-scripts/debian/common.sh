#!/bin/bash

set -e

# Common packages
apt-get update
apt-get install --download-only -y \
    sudo \
    locales \
    git \
    curl \
    wget \
    fuse \
    tzdata \
    acl \
    coreutils \
    libltdl7 \
    openssh-server \
    gnupg \
    lsb-release \
    nfs-common \
    cifs-utils \
    nfs-kernel-server \
    tcl-dev
