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
                btrfs-progs && \
curl https://bootstrap.pypa.io/get-pip.py | python -

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
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.7.5/docker/kube-proxy-amd64-v1.7.5.tar" -O /tmp/kube-proxy-amd64-v1.7.5.tar  && \
docker load -i /tmp/kube-proxy-amd64-v1.7.5.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.7.5/docker/pause-amd64-3.0.tar" -O /tmp/pause-amd64-3.0.tar && \
docker load -i /tmp/pause-amd64-3.0.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.7.5/docker/flannel-v0.9.0-amd64.tar" -O /tmp/flannel-v0.9.0-amd64.tar && \
docker load -i /tmp/flannel-v0.9.0-amd64.tar && \
systemctl stop docker && \
rm -rf /tmp/*

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

# Install nvidia driver
wget http://us.download.nvidia.com/tesla/384.145/NVIDIA-Linux-x86_64-384.145.run && \
sh NVIDIA-Linux-x86_64-384.145.run --silent

# Install nvidia docker
distribution=$(. /etc/os-release;echo $ID$VERSION_ID) 
curl -s -L https://nvidia.github.io/nvidia-container-runtime/$distribution/nvidia-container-runtime.repo | \
  sudo tee /etc/yum.repos.d/nvidia-container-runtime.repo
curl -s -L https://nvidia.github.io/libnvidia-container/$distribution/libnvidia-container.repo | \
  sudo tee /etc/yum.repos.d/libnvidia-container.repo
curl -s -L https://nvidia.github.io/nvidia-docker/$distribution/nvidia-docker.repo | \
  sudo tee /etc/yum.repos.d/nvidia-docker.repo

yum install nvidia-docker2-2.0.3-1.docker18.03* \
    nvidia-container-runtime-2.0.0-1.docker18.03* -y

# According to https://aws.amazon.com/ru/premiumsupport/knowledge-center/g2-rhel-boot/ - the following shall be done for p3 instances (p2 work well)
# 1.    Resize the instance, choosing any instance other than one in the g2 series.
# 2.    Edit /etc/default/grub and add the following values to the GRUB_CMDLINE_LINUX line:
#     rd.driver.blacklist=nouveau nouveau.modeset=0
# 3.    Rebuild the grub configuration:
# grub2-mkconfig -o /boot/grub2/grub.cfg

sed -i 's/GRUB_CMDLINE_LINUX_DEFAULT="/GRUB_CMDLINE_LINUX_DEFAULT="rd.driver.blacklist=nouveau nouveau.modeset=0 /g' /etc/default/grub
grub2-mkconfig -o /boot/grub2/grub.cfg

# Label instance as Done
instance_id=$(curl -s http://169.254.169.254/latest/meta-data/instance-id)
region=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep region | cut -d\" -f4)

export AWS_ACCESS_KEY_ID={{AWS_ACCESS_KEY_ID}}
export AWS_SECRET_ACCESS_KEY={{AWS_SECRET_ACCESS_KEY}}
export AWS_DEFAULT_REGION=$region
aws ec2 create-tags --resources $instance_id --tags Key=user_data,Value=done
