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

# Install common
yum install -y  nc \
                python \
                curl \
                coreutils \
                btrfs-progs

# Install jq
wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
chmod +x /usr/bin/jq

# Install Docker
yum install -y yum-utils \
  device-mapper-persistent-data \
  lvm2

# User 18.03 to overcome the 8Gb layer commit limit of 18.06 (see https://github.com/moby/moby/issues/37581)
# 18.09 and up are not yet available for Amzn Linux 2
# Try to install from the docker repo
yum-config-manager \
    --add-repo \
    https://download.docker.com/linux/centos/docker-ce.repo && \
yum install -y  docker-ce-18.03* \
                docker-ce-cli-18.03* \
                containerd.io
if [ $? -ne 0 ]; then
  echo "Unable to install docker from the official repository, trying to use default docker-18.03*"

  # Otherwise try to install default docker (e.g. if it's amazon linux)
  yum install -y docker-18.03*
  if [ $? -ne 0 ]; then
    echo "Unable to install default docker-18.03* too, exiting"
    exit 1
  fi
fi

# Install kubelet
cat <<EOF >/etc/yum.repos.d/kubernetes.repo
[kubernetes]
name=Kubernetes
baseurl=http://yum.kubernetes.io/repos/kubernetes-el7-x86_64
enabled=1
gpgcheck=1
repo_gpgcheck=1
gpgkey=https://packages.cloud.google.com/yum/doc/yum-key.gpg
       https://packages.cloud.google.com/yum/doc/rpm-package-key.gpg
EOF

yum -q makecache -y --enablerepo kubernetes --nogpg

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

yum install -y \
            kubeadm-1.7.5-0.x86_64 \
            kubectl-1.7.5-0.x86_64 \
            kubelet-1.7.5-0.x86_64 \
            kubernetes-cni-0.5.1-0.x86_64

# Setup default cgroups and cadvisor port
sed -i 's/Environment="KUBELET_CADVISOR_ARGS=--cadvisor-port=0"/Environment="KUBELET_CADVISOR_ARGS=--cadvisor-port=4194"/g' /etc/systemd/system/kubelet.service.d/10-kubeadm.conf
sed -i 's/Environment="KUBELET_CGROUP_ARGS=--cgroup-driver=systemd"/Environment="KUBELET_CGROUP_ARGS=--cgroup-driver=cgroupfs"/g' /etc/systemd/system/kubelet.service.d/10-kubeadm.conf

sed -i 's|Provisioning.DecodeCustomData=n|Provisioning.DecodeCustomData=y|g' /etc/waagent.conf
sed -i 's|Provisioning.ExecuteCustomData=n|Provisioning.ExecuteCustomData=y|g' /etc/waagent.conf

# Upgrade to the latest mainline kernel (4.17+)
yum install -y ncurses-devel make gcc bc bison flex elfutils-libelf-devel openssl-devel grub2
cd /usr/src/
wget https://cdn.kernel.org/pub/linux/kernel/v4.x/linux-4.20.1.tar.xz
tar -xvf linux-4.20.1.tar.xz
cd linux-4.20.1/
cp -v /boot/config-3.10.0-957.27.2.el7.x86_64 /usr/src/linux-4.20.1/.config

make menuconfig
make bzImage
make modules
make
make modules_install
make install

sed -i '/GRUB_DEFAULT=/c\GRUB_DEFAULT=0' /etc/default/grub && \
grub2-mkconfig -o /boot/grub2/grub.cfg
