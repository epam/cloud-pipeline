#!/bin/bash
# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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
yum install -y  nc \
                python \
                curl \
                btrfs-progs

yum install -y iproute-tc
curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

# Install jq
wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
chmod +x /usr/bin/jq

# Install nvidia driver deps
yum install -y  gcc \
                gcc-c++ \
                kernel-devel-$(uname -r) \
                dkms \
                libglvnd-devel

# Install Docker
yum install -y yum-utils \
  device-mapper-persistent-data \
  lvm2
 
# Try to install from the docker repo
yum install -y docker-20.10*
if [ $? -ne 0 ]; then
    echo "Unable to install default docker-20.10*, exiting"
    exit 1
fi
 
# Get the kube docker images, required by the kubelet
# This is needed, as we don't want to rely on the external repos
systemctl start docker && \
mkdir -p /opt/docker-system-images && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-node-v3.14.1.tar" -O /opt/docker-system-images/calico-node-v3.14.1.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-pod2daemon-flexvol-v3.14.1.tar" -O /opt/docker-system-images/calico-pod2daemon-flexvol-v3.14.1.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-cni-v3.14.1.tar" -O /opt/docker-system-images/calico-cni-v3.14.1.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/k8s.gcr.io-kube-proxy-v1.15.4.tar" -O /opt/docker-system-images/k8s.gcr.io-kube-proxy-v1.15.4.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/quay.io-coreos-flannel-v0.11.0.tar" -O /opt/docker-system-images/quay.io-coreos-flannel-v0.11.0.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/k8s.gcr.io-pause-3.1.tar" -O /opt/docker-system-images/k8s.gcr.io-pause-3.1.tar

systemctl stop docker
 
# Install kubelet
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
 
wget -q https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/rpm/kube-1.15.4.el7.tgz -Okube.tgz && \
     tar -xf kube.tgz && \
     cd kube && yum localinstall *kube*.rpm *cri-tools*.rpm -y && \
     cd .. && rm -rf kube/ && rm -rf kube.tgz
 
# Install nvidia driver
amazon-linux-extras install -y epel
yum install -y vulkan-devel libglvnd-devel elfutils-libelf-devel automake make gcc gcc-c++  xorg-x11-server-Xorg xorg-x11-fonts-Type1 xorg-x11-drivers
 
DRIVER_VERSION=560.35.03
curl -L -O https://us.download.nvidia.com/tesla/$DRIVER_VERSION/NVIDIA-Linux-$(arch)-$DRIVER_VERSION.run
chmod +x ./NVIDIA-Linux-$(arch)-$DRIVER_VERSION.run
CC=/usr/bin/gcc10-cc ./NVIDIA-Linux-$(arch)-$DRIVER_VERSION.run -s
 
# Install nvidia docker
distribution=$(. /etc/os-release;echo $ID$VERSION_ID)
curl -s -L https://nvidia.github.io/nvidia-container-runtime/$distribution/nvidia-container-runtime.repo |\
  sudo tee /etc/yum.repos.d/nvidia-container-runtime.repo
curl -s -L https://nvidia.github.io/libnvidia-container/$distribution/libnvidia-container.repo | \
  sudo tee /etc/yum.repos.d/libnvidia-container.repo
curl -s -L https://nvidia.github.io/nvidia-docker/$distribution/nvidia-docker.repo | \
  sudo tee /etc/yum.repos.d/nvidia-docker.repo
 
yum install nvidia-docker2-2.13.0-1 \
    nvidia-container-runtime-3.5.0-1 -y
