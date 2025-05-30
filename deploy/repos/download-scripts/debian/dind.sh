#!/bin/bash

set -e

# Common packages
apt-get update
apt-get install --download-only -y \
    btrfs-progs \
    e2fsprogs \
    iptables \
    iproute2 \
    xfsprogs \
    xz-utils \
    pigz \
    kmod \
    libkmod2 \
    bash-completion \
    libfuse2 \
    libexpat1 \
    ucf \
    libxml2 \
    libip4tc0
