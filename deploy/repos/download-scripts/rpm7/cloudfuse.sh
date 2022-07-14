#!/bin/bash

set -e

# gcsfuse
cat > /etc/yum.repos.d/gcsfuse.repo <<EOF
[gcsfuse]
name=gcsfuse (packages.cloud.google.com)
baseurl=https://packages.cloud.google.com/yum/repos/gcsfuse-el7-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg
       https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOF

# az blobfuse
. /etc/os-release
yum install -y epel-release
rpm -Uvh https://packages.microsoft.com/config/$ID/$VERSION_ID/packages-microsoft-prod.rpm

yum install --downloadonly --downloaddir=/rpmcache -y -q gcsfuse blobfuse fuse
