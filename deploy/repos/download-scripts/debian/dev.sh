#!/bin/bash

set -e

# Common packages
apt-get update
apt-get install --download-only -y \
    automake \
    autotools-dev \
    fuse \
    g++ \
    libcurl4-gnutls-dev \
    libfuse-dev \
    libssl-dev \
    libxml2-dev \
    make \
    pkg-config \
    zlib1g-dev \
    libncurses5-dev \
    gettext-base \
    libjsoncpp-dev
