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


set -e

# 1
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

# 2
yum -q makecache -y --enablerepo kubernetes --nogpg

# 3
cat <<EOF >/etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
EOF
sysctl --system

# 4
setenforce 0
sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config

#6.1 - Docker
yum install -y yum-utils \
  device-mapper-persistent-data \
  lvm2 && \
yum-config-manager \
    --add-repo \
    https://download.docker.com/linux/centos/docker-ce.repo && \
yum install -y  docker-ce-18.09.1 \
                docker-ce-cli-18.09.1 \
                containerd.io

if [ "$CP_KUBE_MASTER_DOCKER_PATH" ]; then
  echo "CP_KUBE_MASTER_DOCKER_PATH is specified - docker will be configured to store data in $CP_KUBE_MASTER_DOCKER_PATH"
  mkdir -p "$CP_KUBE_MASTER_DOCKER_PATH"
  DOCKER_DATA_ROOT_ENTRY="\"data-root\": \"$CP_KUBE_MASTER_DOCKER_PATH\","
fi

mkdir -p /etc/docker
cat <<EOT > /etc/docker/daemon.json
{
  $DOCKER_DATA_ROOT_ENTRY
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ]
}
EOT

if [ "$http_proxy" ] || [ "$https_proxy" ]; then
  mkdir -p /etc/systemd/system/docker.service.d
cat > /etc/systemd/system/docker.service.d/http-proxy.conf << EOF
  [Service]
  Environment="http_proxy=$http_proxy" "https_proxy=$https_proxy" "no_proxy=$no_proxy"
EOF
fi

#6.2 - Kube
yum install -y \
            kubeadm-1.7.5-0.x86_64 \
            kubectl-1.7.5-0.x86_64 \
            kubelet-1.7.5-0.x86_64 \
            kubernetes-cni-0.5.1-0.x86_64

#7
sed -i 's/Environment="KUBELET_CADVISOR_ARGS=--cadvisor-port=0"/Environment="KUBELET_CADVISOR_ARGS=--cadvisor-port=4194"/g' /etc/systemd/system/kubelet.service.d/10-kubeadm.conf
sed -i 's/Environment="KUBELET_CGROUP_ARGS=--cgroup-driver=systemd"/Environment="KUBELET_CGROUP_ARGS=--cgroup-driver=cgroupfs"/g' /etc/systemd/system/kubelet.service.d/10-kubeadm.conf

#8
systemctl daemon-reload
systemctl enable docker
systemctl enable kubelet
systemctl start docker
systemctl start kubelet

# TODO: here and further - implement a smarter approach to wait for the kube service to init
sleep 10

#9
# If another directory for etcd is specified via CP_KUBE_MASTER_ETCD_HOST_PATH - it will be symlinked to the default location at /var/lib/etcd
# This allows to use different drive for etcd wal/data dirs to overcome any I/O latencies which may cause control plane pods failures
if [ "$CP_KUBE_MASTER_ETCD_HOST_PATH" ]; then
  echo "CP_KUBE_MASTER_ETCD_HOST_PATH is specified, etcd will be configured to store data in $CP_KUBE_MASTER_ETCD_HOST_PATH"
  CP_KUBE_MASTER_ETCD_DEFAULT_HOST_PATH="${CP_KUBE_MASTER_ETCD_DEFAULT_HOST_PATH:-"/var/lib/etcd"}"
  if [ -d "$CP_KUBE_MASTER_ETCD_DEFAULT_HOST_PATH" ]; then
    echo "Default etcd directory already exists at $CP_KUBE_MASTER_ETCD_DEFAULT_HOST_PATH deleting it"
    rm -rf "$CP_KUBE_MASTER_ETCD_DEFAULT_HOST_PATH"
  fi

  ln -s "$CP_KUBE_MASTER_ETCD_HOST_PATH" "$CP_KUBE_MASTER_ETCD_DEFAULT_HOST_PATH"
  echo "Symlink created from $CP_KUBE_MASTER_ETCD_HOST_PATH to $CP_KUBE_MASTER_ETCD_DEFAULT_HOST_PATH"
fi

FLANNEL_CIDR=${FLANNEL_CIDR:-"10.244.0.0/16"}
kubeadm init --pod-network-cidr="$FLANNEL_CIDR" --kubernetes-version v1.7.5 --skip-preflight-checks > $HOME/kubeadm_init.log

sleep 30

#10
wget -q "https://raw.githubusercontent.com/coreos/flannel/a154d2f68edd511498c948e33c8cbde20a5901ee/Documentation/kube-flannel.yml" -O /opt/kube-flannel.yml

#11
sed -i "s|10.244.0.0/16|$FLANNEL_CIDR|g" /opt/kube-flannel.yml
sed -i 's|"isDefaultGateway": true|"isDefaultGateway": true, "hairpinMode": true|g' /opt/kube-flannel.yml
sed -i 's@command: \[ "/opt/bin/flanneld", "--ip-masq", "--kube-subnet-mgr" \]@command: \[ "/bin/sh", "-c", "for i in $(seq 1 10); do printf ping | nc -w 1 $KUBERNETES_SERVICE_HOST $KUBERNETES_SERVICE_PORT_HTTPS \&\& break; done \&\& /opt/bin/flanneld --ip-masq --kube-subnet-mgr" \]@g' /opt/kube-flannel.yml
#12
mkdir -p $HOME/.kube
cp /etc/kubernetes/admin.conf $HOME/.kube/config
kubectl apply -f /opt/kube-flannel.yml

sleep 10

#13
kubectl create clusterrolebinding owner-cluster-admin-binding \
    --clusterrole cluster-admin \
    --user system:serviceaccount:default:default 

sleep 10

#14
# Allow services to bind to 80+ ports, as the default range is 30000-32767
# --service-node-port-range option is added as a next line after init command "- kube-apiserver"
# kubelet monitors /etc/kubernetes/manifests folder, so kube-api pod will be recreated automatically
sed -i '/- kube-apiserver/a \    \- --service-node-port-range=80-32767' /etc/kubernetes/manifests/kube-apiserver.yaml

sleep 30
