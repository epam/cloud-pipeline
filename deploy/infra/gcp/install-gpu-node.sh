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

# Disable automatic packages upgrade
yum remove yum-cron -y

# Upgrade to the latest mainline kernel (4.17+)
yum remove -y kernel-tools kernel-tools-libs kernel-headers && \
rpm --import https://www.elrepo.org/RPM-GPG-KEY-elrepo.org && \
rpm -Uvh http://www.elrepo.org/elrepo-release-7.0-4.el7.elrepo.noarch.rpm && \
yum --enablerepo=elrepo-kernel install kernel-ml \
                                       kernel-ml-devel \
                                       kernel-ml-headers \
                                       kernel-ml-tools \
                                       kernel-ml-tools-libs \
                                       kernel-ml-tools-libs-devel -y && \
sed -i '/GRUB_DEFAULT=/c\GRUB_DEFAULT=0' /etc/default/grub && \
grub2-mkconfig -o /boot/grub2/grub.cfg
grep 'menuentry ' /boot/grub2/grub.cfg | cut -f 2 -d "'" | nl -v 0
grub2-set-default 'CentOS Linux (5.7.7-1.el7.elrepo.x86_64) 7 (Core)'

###########
reboot
###########

# Install common
yum install -y  nc \
                python \
                curl \
                wget \
                btrfs-progs && \
curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

# Install jq
wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
chmod +x /usr/bin/jq

# Install nvidia driver deps
yum install -y  gcc

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

# Install nvidia driver
wget http://us.download.nvidia.com/XFree86/Linux-x86_64/440.100/NVIDIA-Linux-x86_64-440.100.run && \
sh NVIDIA-Linux-x86_64-440.100.run --silent && \
rm -f NVIDIA-Linux-x86_64-440.100.run

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
