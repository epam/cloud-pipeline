#!/bin/bash

set -e

# Common packages
apt-get update
apt-get install --download-only -y \
    sudo \
    locales \
    python2 \
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
    tcl-dev \
    samba* \
    procps \
    pciutils \
    screen \
    vim \
    nano \
    htop \
    gettext \
    bash-completion \
    libfuse2 \
    libexpat1 \
    ucf \
    libxml2



