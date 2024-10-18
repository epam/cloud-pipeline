#!/bin/bash

set -e

# Common packages
yum install -y --downloadonly --downloaddir=/rpmcache --installroot=/tmp/installroot --releasever=/ \
    e2fsprogs \
    iptables \
    iproute \
    xfsprogs \
    xz \
    pigz \
    kmod
    # btrfs-progs
