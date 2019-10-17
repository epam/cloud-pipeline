#!/bin/bash

set -e

# SGE
CP_CAP_SGE_VERSION="${CP_CAP_SGE_VERSION:-8.1.9+dfsg-4*}"
echo "deb [trusted=yes] http://ftp.us.debian.org/debian/ stretch main" > /etc/apt/sources.list.d/sge.list
apt-get update
apt-get install --download-only -t stretch -y --allow-unauthenticated \
    gridengine-exec="$CP_CAP_SGE_VERSION" \
    gridengine-client="$CP_CAP_SGE_VERSION" \
    gridengine-common="$CP_CAP_SGE_VERSION" \
    gridengine-master="$CP_CAP_SGE_VERSION"
