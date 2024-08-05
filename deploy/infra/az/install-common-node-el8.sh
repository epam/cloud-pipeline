#!/bin/bash
# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

# RedHat 8.8 Gen 2

yum install -y  nc \
                python2 \
                curl \
                coreutils \
                iproute-tc && \
wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
chmod +x /usr/bin/jq && \
yum install -y yum-utils \
  device-mapper-persistent-data \
  lvm2 && \
yum-config-manager \
    --add-repo \
    https://download.docker.com/linux/centos/docker-ce.repo && \
yum install -y  docker-ce-20.10.04 \
                docker-ce-cli-20.10.04 \
                containerd.io && \
mkdir -p /opt/docker-system-images && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-node-v3.14.1-nft.tar" -O /opt/docker-system-images/calico-node-v3.14.1.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-pod2daemon-flexvol-v3.14.1.tar" -O /opt/docker-system-images/calico-pod2daemon-flexvol-v3.14.1.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/calico-cni-v3.14.1.tar" -O /opt/docker-system-images/calico-cni-v3.14.1.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/k8s.gcr.io-kube-proxy-v1.15.4-nft.tar" -O /opt/docker-system-images/k8s.gcr.io-kube-proxy-v1.15.4.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/quay.io-coreos-flannel-v0.11.0-nft.tar" -O /opt/docker-system-images/quay.io-coreos-flannel-v0.11.0.tar && \
wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker/k8s.gcr.io-pause-3.1.tar" -O /opt/docker-system-images/k8s.gcr.io-pause-3.1.tar

cat <<EOF >/etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
EOF
sysctl --system
setenforce 0 || true
sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config

cd && \
wget https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/rpm/kube-1.15.4.tgz && \
tar -zxvf kube-1.15.4.tgz && \
cd kube && \
yum install -y ./2813cf105c52ad1240f2cc6cba9a3f779bf2f5c4940c731a27df6e5d9557a5b1-kubeadm-1.15.4-0.x86_64.rpm \
                ./de6039d16a6e77e0f38ce47cfaff9d450545757b3d09d34a10daf5667cd95ef6-kubectl-1.15.4-0.x86_64.rpm \
                ./dd6f87ffb5e04121b39c9b8301573225aff0a135ebf73046dcd3e21c4ab7a6cb-kubelet-1.15.4-0.x86_64.rpm \
                ./029bc0d7b2112098bdfa3f4621f2ce325d7a2c336f98fa80395a3a112ab2a713-kubernetes-cni-0.8.6-0.x86_64.rpm \
                ./14bfe6e75a9efc8eca3f638eb22c7e2ce759c67f95b43b16fae4ebabde1549f3-cri-tools-1.13.0-0.x86_64.rpm && \
cd .. && \
rm -rf kube kube-1.15.4.tgz

sed -i 's|Provisioning.DecodeCustomData=n|Provisioning.DecodeCustomData=y|g' /etc/waagent.conf
sed -i 's|Provisioning.ExecuteCustomData=n|Provisioning.ExecuteCustomData=y|g' /etc/waagent.conf

waagent -deprovision+user
