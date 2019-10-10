#!/bin/bash

set -e

# Common packages
apt-get update
apt-get install --download-only -y \
    sudo \
    locales \
    python \
    git \
    curl \
    wget \
    fuse \
    python-docutils \
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
