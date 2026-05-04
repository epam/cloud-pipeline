#!/bin/bash
# Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

# Disable automatic packages upgrade, if cloud-init is configured
if [ -d "/etc/cloud/cloud.cfg.d" ]; then

cat <<EOF >/etc/cloud/cloud.cfg.d/99_no_upgrades.cfg
repo_upgrade: none
repo_upgrade_exclude:
 - kernel
 - nvidia*
 - cuda*
 - kubernetes*
EOF

fi

# Install common
yum install -y nc
yum install -y iproute-tc
yum install -y iptables-legacy
update-alternatives --set iptables /usr/sbin/iptables-legacy

# btrfs-progs package is not avaialble in 2023+
# Replaced with:
# - https://btrfs.readthedocs.io/en/latest/INSTALL.html#all-in-one-binary-busybox-style
# - https://github.com/kdave/btrfs-progs
cd /usr/bin && \
wget -q "https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/btrfs/6.17/btrfs.box.static" -O btrfs && \
chmod +x btrfs && \
ln -s btrfs mkfs.btrfs

# python2
cd /opt && \
wget -q "https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/python/2/Miniconda2-4.7.12.1-Linux-x86_64.tar.gz" && \
tar -zxf Miniconda2-4.7.12.1-Linux-x86_64.tar.gz && \
rm -f Miniconda2-4.7.12.1-Linux-x86_64.tar.gz && \
ln -s /opt/conda/bin/python2 /usr/bin/python2 && \
ln -s /opt/conda/bin/python2 /usr/bin/python && \
ln -s /opt/conda/bin/pip /usr/bin/pip2 && \
ln -s /opt/conda/bin/pip /usr/bin/pip

# Install jq
wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
chmod +x /usr/bin/jq

# Enable forwarding
cat <<EOF >/etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
EOF
sysctl --system

# Disable SELinux
setenforce 0
sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config

# Configure GRUB for compatible cgroups fs
# https://github.com/ddometita/mmumshad-kubernetes-the-hard-way/issues/8#issuecomment-1397606994
echo 'GRUB_CMDLINE_LINUX="systemd.unified_cgroup_hierarchy=0"' >> /etc/default/grub
grub2-mkconfig -o /boot/grub2/grub.cfg

# Nvidia drivers
yum install -y vulkan-devel \
                gcc \
                gcc-c++ \
                kernel-devel \
                kernel-modules-extra

DRIVER_VERSION=595.58.03
curl -k -L -O https://us.download.nvidia.com/tesla/$DRIVER_VERSION/NVIDIA-Linux-$(arch)-$DRIVER_VERSION.run
chmod +x ./NVIDIA-Linux-$(arch)-$DRIVER_VERSION.run
./NVIDIA-Linux-$(arch)-$DRIVER_VERSION.run -s

# Fabric Manager for A100, H100, H200 and friends
dnf config-manager --add-repo https://developer.download.nvidia.com/compute/cuda/repos/amzn2023/x86_64/cuda-amzn2023.repo
dnf install nvidia-fabric-manager -y
dnf install nvidia-persistenced -н
systemctl enable nvidia-fabricmanager
systemctl enable nvidia-persistenced