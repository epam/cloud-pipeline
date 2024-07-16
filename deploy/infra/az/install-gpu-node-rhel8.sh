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

systemctl disable firewalld

yum install -y https://www.elrepo.org/elrepo-release-8.el8.elrepo.noarch.rpm

# Install common
yum install -y  nc \
                python2 \
                curl \
                iproute-tc

# Install jq
wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
chmod +x /usr/bin/jq

# Install nvidia driver deps
yum install -y https://dl.fedoraproject.org/pub/epel/epel-release-latest-8.noarch.rpm
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
yum install -y  docker-ce-20.10* \
                docker-ce-cli-20.10* \
                containerd.io

if [ $? -ne 0 ]; then
  echo "Unable to install docker from the official repository, trying to use default docker-18.03*"

  # Otherwise try to install default docker (e.g. if it's amazon linux)
  yum install -y docker-20.10*
  if [ $? -ne 0 ]; then
    echo "Unable to install default docker-18.03* too, exiting"
    exit 1
  fi
fi

# Get the kube docker images, required by the kubelet
# This is needed, as we don't want to rely on the external repos
systemctl start docker && \
mkdir -p /opt/docker-system-images && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-node-v3.14.1-nft.tar" -O /opt/docker-system-images/calico-node-v3.14.1-nft.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-pod2daemon-flexvol-v3.14.1.tar" -O /opt/docker-system-images/calico-pod2daemon-flexvol-v3.14.1.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-cni-v3.14.1.tar" -O /opt/docker-system-images/calico-cni-v3.14.1.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/k8s.gcr.io-kube-proxy-v1.15.4-nft.tar" -O /opt/docker-system-images/k8s.gcr.io-kube-proxy-v1.15.4-nft.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/quay.io-coreos-flannel-v0.11.0-nft.tar" -O /opt/docker-system-images/quay.io-coreos-flannel-v0.11.0-nft.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/k8s.gcr.io-pause-3.1.tar" -O/opt/docker-system-images/k8s.gcr.io-pause-3.1.tar

docker load -i /opt/docker-system-images/calico-node-v3.14.1-nft.tar && \
docker load -i /opt/docker-system-images/calico-pod2daemon-flexvol-v3.14.1.tar && \
docker load -i /opt/docker-system-images/calico-cni-v3.14.1.tar && \
docker load -i /opt/docker-system-images/k8s.gcr.io-kube-proxy-v1.15.4-nft.tar && \
docker load -i /opt/docker-system-images/quay.io-coreos-flannel-v0.11.0-nft.tar && \
docker load -i /opt/docker-system-images/k8s.gcr.io-pause-3.1.tar

systemctl stop docker


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

wget https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/rpm/kube-1.15.4.el7.tgz && \
tar -xf kube-1.15.4.el7.tgz && \
cd kube && \
yum localinstall -y \
            ./*kubeadm*.rpm \
            ./*kubectl*.rpm \
            ./*kubernetes*.rpm \
            ./*cri-tools*.rpm \
            ./*kubelet*.rpm && \
cd .. && \
rm -rf ./kube*

sed -i 's|Provisioning.DecodeCustomData=n|Provisioning.DecodeCustomData=y|g' /etc/waagent.conf
sed -i 's|Provisioning.ExecuteCustomData=n|Provisioning.ExecuteCustomData=y|g' /etc/waagent.conf

wget http://us.download.nvidia.com/tesla/525.125.06/NVIDIA-Linux-x86_64-525.125.06.run && \
bash NVIDIA-Linux-x86_64-525.125.06.run --silent && \
rm -rf NVIDIA-Linux-x86_64-525.125.06.run

distribution=centos8
curl -s -L https://nvidia.github.io/nvidia-container-runtime/$distribution/nvidia-container-runtime.repo | \
  sudo tee /etc/yum.repos.d/nvidia-container-runtime.repo
curl -s -L https://nvidia.github.io/libnvidia-container/$distribution/libnvidia-container.repo | \
  sudo tee /etc/yum.repos.d/libnvidia-container.repo
curl -s -L https://nvidia.github.io/nvidia-docker/$distribution/nvidia-docker.repo | \
  sudo tee /etc/yum.repos.d/nvidia-docker.repo

  yum install nvidia-docker2 \
      nvidia-container-runtime -y

# File is read-only, disable it
sudo chattr -i /etc/resolv.conf
