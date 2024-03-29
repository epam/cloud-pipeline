#!/bin/bash
# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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


#### INSTALLATION NOTE #####
# For instances like Standard_NC*ads_A100_v4 nodes require the use of Generation 2 images
# So this node image should be build from one of standard Azure generation 2 marketplace images
# f.e. OpenLogic:CentOS-HPC:7.6 Gen2
############################

# Install common
yum install -y  nc \
                python \
                curl \
                coreutils \
                btrfs-progs \
                iproute-tc

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
            kubeadm-1.15.4-0.x86_64 \
            kubectl-1.15.4-0.x86_64 \
            kubelet-1.15.4-0.x86_64

# Setup default cgroups and cadvisor port
sed -i 's/Environment="KUBELET_CADVISOR_ARGS=--cadvisor-port=0"/Environment="KUBELET_CADVISOR_ARGS=--cadvisor-port=4194"/g' /etc/systemd/system/kubelet.service.d/10-kubeadm.conf
sed -i 's/Environment="KUBELET_CGROUP_ARGS=--cgroup-driver=systemd"/Environment="KUBELET_CGROUP_ARGS=--cgroup-driver=cgroupfs"/g' /etc/systemd/system/kubelet.service.d/10-kubeadm.conf

# Install nvidia driver
# - For a100
wget https://us.download.nvidia.com/XFree86/Linux-x86_64/470.57.02/NVIDIA-Linux-x86_64-470.57.02.run && \
sh NVIDIA-Linux-x86_64-470.57.02.run --silent && \
rm -f NVIDIA-Linux-x86_64-470.57.02.run
cat > /etc/yum.repos.d/cuda-rhel7.repo <<EOF
[cuda-rhel7-x86_64]
name=cuda-rhel7-x86_64
baseurl=https://developer.download.nvidia.com/compute/cuda/repos/rhel7/x86_64
enabled=1
gpgcheck=1
gpgkey=https://developer.download.nvidia.com/compute/cuda/repos/rhel7/x86_64/7fa2af80.pub
EOF
yum install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-7.noarch.rpm && \
yum -y install cuda-drivers-fabricmanager-470 && \
systemctl enable nvidia-fabricmanager
# -

# Install nvidia docker
distribution=$(. /etc/os-release;echo $ID$VERSION_ID) 
curl -s -L https://nvidia.github.io/nvidia-container-runtime/$distribution/nvidia-container-runtime.repo | \
  sudo tee /etc/yum.repos.d/nvidia-container-runtime.repo
curl -s -L https://nvidia.github.io/libnvidia-container/$DIST/libnvidia-container.repo | \
  sudo tee /etc/yum.repos.d/libnvidia-container.repo
curl -s -L https://nvidia.github.io/nvidia-docker/$distribution/nvidia-docker.repo | \
  sudo tee /etc/yum.repos.d/nvidia-docker.repo

yum install nvidia-docker2-2.0.3-1.docker18.03* \
    nvidia-container-runtime-3.11.0-1.noarch -y


sed -i 's|Provisioning.DecodeCustomData=n|Provisioning.DecodeCustomData=y|g' /etc/waagent.conf
sed -i 's|Provisioning.ExecuteCustomData=n|Provisioning.ExecuteCustomData=y|g' /etc/waagent.conf

# Upgrade to the latest mainline kernel (4.17+)
rpm --import https://www.elrepo.org/RPM-GPG-KEY-elrepo.org && \
rpm -Uvh http://www.elrepo.org/elrepo-release-7.0-4.el7.elrepo.noarch.rpm && \
yum --enablerepo=elrepo-kernel install kernel-ml -y && \
sed -i '/GRUB_DEFAULT=/c\GRUB_DEFAULT=0' /etc/default/grub && \
grub2-mkconfig -o /boot/grub2/grub.cfg
