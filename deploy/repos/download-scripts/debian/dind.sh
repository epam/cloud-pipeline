#!/bin/bash

set -e

# Common packages
apt-get update
apt-get install --download-only -y \
    btrfs-tools \
    e2fsprogs \
    iptables \
    iproute2 \
    xfsprogs \
    xz-utils \
    pigz \
    kmod
