#!/bin/bash

set -e

# Common packages
yum install -y epel-release
yum install -y --downloadonly --downloaddir=/rpmcache \
    btrfs-progs \
    e2fsprogs \
    iptables \
    iproute \
    xfsprogs \
    xz \
    pigz \
    kmod
