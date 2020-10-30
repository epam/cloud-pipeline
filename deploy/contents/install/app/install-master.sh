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

# Check that we have enough disk space for the master compononents (root fs, docker, etcd)
check_enough_disk "${CP_COMMON_ROOT_FS_MIN_DISK_MB:-20480}" \
                  "/"

check_enough_disk "${CP_KUBE_MASTER_DOCKER_MIN_DISK_MB:-102400}" \
                  "$CP_KUBE_MASTER_DOCKER_PATH" "/var/lib/docker"

check_enough_disk "${CP_KUBE_MASTER_ETCD_MIN_DISK_MB:-20480}" \
                  "$CP_KUBE_MASTER_ETCD_HOST_PATH" "/var/lib/etcd"



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
setenforce 0 || true
sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config

#6.1 - Docker
set +e
yum install -y yum-utils \
  device-mapper-persistent-data \
  lvm2 && \
yum-config-manager \
    --add-repo \
    https://download.docker.com/linux/centos/docker-ce.repo && \
yum install -y  docker-ce-18.03* \
                docker-ce-cli-18.03* \
                containerd.io
if [ $? -ne 0 ]; then
  echo "Unable to install docker from the official repository, trying to use default docker-18.03*"
  
  # Otherwise try to install default docker (e.g. if it's amazon linux)
  rm -f /etc/yum.repos.d/docker-ce.repo
  yum install -y docker-18.03*
  if [ $? -ne 0 ]; then
    echo "Unable to install default docker-18.03* too, exiting"
    exit 1
  fi
fi
set -e

if [ "$CP_KUBE_MASTER_DOCKER_PATH" ]; then
  echo "CP_KUBE_MASTER_DOCKER_PATH is specified - docker will be configured to store data in $CP_KUBE_MASTER_DOCKER_PATH"
  mkdir -p "$CP_KUBE_MASTER_DOCKER_PATH"
  DOCKER_DATA_ROOT_ENTRY="\"data-root\": \"$CP_KUBE_MASTER_DOCKER_PATH\","
fi

mkdir -p /etc/docker
cat <<EOT > /etc/docker/daemon.json
{
  $DOCKER_DATA_ROOT_ENTRY
  "exec-opts": ["native.cgroupdriver=systemd"],
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

# Enable forwarding and make sure iptables are used
# See https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/install-kubeadm/#installing-kubeadm-kubelet-and-kubectl
modprobe br_netfilter
cat <<EOF >/etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
EOF
sysctl --system

# Disable SELinux as required by the Kube
# See https://kubernetes.io/docs/setup/production-environment/tools/kubeadm/install-kubeadm/#installing-kubeadm-kubelet-and-kubectl
# Ignore exit code as setenforce will return 1 if selinux is already disabled
# which will faile the whole script as -e is set
setenforce 0 || true
sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config

# Add Kubernetes repository to yum
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

#6.2 - Kube
yum install -y \
            kubeadm-1.15.4-0.x86_64 \
            kubectl-1.15.4-0.x86_64 \
            kubelet-1.15.4-0.x86_64

#8
systemctl daemon-reload
systemctl enable docker
systemctl enable kubelet
systemctl start docker
systemctl start kubelet

# FIXME: here and further - implement a smarter approach to wait for the kube service to init
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

# Temporary disable http/https/no proxy settings, as kubeadm will put them into /etc/kubernetes/manifests/kube-apiserver.yaml
# For 99% of the use-cases this is not desired as it breaks inter-node communication
# After kubeadm is done - values will be restored (this can be controlled with --keep-kubedm-proxies option)
bkp_http_proxy="$http_proxy"
bkp_https_proxy="$https_proxy"
bkp_no_proxy="$no_proxy"
if [ "$CP_KUBE_KEEP_KUBEADM_PROXIES" != "1" ]; then
  unset http_proxy https_proxy no_proxy
fi

# Initialize the kubeadm config and start the master
export CP_KUBE_FLANNEL_CIDR=${CP_KUBE_FLANNEL_CIDR:-"10.244.0.0/16"}
export CP_KUBE_KUBELET_PORT="${CP_KUBE_KUBELET_PORT:-10250}"
CP_KUBEADM_INIT_CONFIG_YAML="$K8S_SPECS_HOME/kube-system/kubeadm-init-config.yaml"
if [ ! -f "$CP_KUBEADM_INIT_CONFIG_YAML" ]; then
  echo "Unable to find kubeadm init spec file at ${CP_KUBEADM_INIT_CONFIG_YAML}, exiting"
  exit 1
fi
CP_KUBEADM_INIT_CONFIG_YAML_TMP="/tmp/$(basename $CP_KUBEADM_INIT_CONFIG_YAML)"
envsubst '${CP_KUBE_FLANNEL_CIDR} ${CP_KUBE_KUBELET_PORT}' < "$CP_KUBEADM_INIT_CONFIG_YAML" > "$CP_KUBEADM_INIT_CONFIG_YAML_TMP"
kubeadm init --config "$CP_KUBEADM_INIT_CONFIG_YAML_TMP"
sleep 30

# Copy the admin's config file to the default location to "enable" kubectl
mkdir -p $HOME/.kube
\cp /etc/kubernetes/admin.conf $HOME/.kube/config

# Restore http proxy proxy values, if they were dropped previously
export http_proxy="$bkp_http_proxy"
export https_proxy="$bkp_https_proxy"
export no_proxy="$bkp_no_proxy"
unset bkp_http_proxy bkp_https_proxy bkp_no_proxy

#10
CP_KUBE_NETWORK_YAML="$K8S_SPECS_HOME/kube-system/canal.yaml"
if [ ! -f "$CP_KUBE_NETWORK_YAML" ]; then
  echo "Unable to find flannel spec file at ${CP_KUBE_NETWORK_YAML}, exiting"
  exit 1
fi
envsubst '${CP_KUBE_FLANNEL_CIDR}' < "$CP_KUBE_NETWORK_YAML" | kubectl apply -f -
sleep 10

#11
CP_KUBE_NETWORK_POLICY_PATH=${CP_KUBE_NETWORK_POLICY_PATH:-"$K8S_SPECS_HOME/kube-system/network-policy"}
if [ ! -d "$CP_KUBE_NETWORK_POLICY_PATH" ] || [ ! -d "$CP_KUBE_NETWORK_POLICY_PATH" ]; then
  print_warn "Network policies are not provided in the directory $CP_KUBE_NETWORK_POLICY_PATH"
else
  for entry in $(find $CP_KUBE_NETWORK_POLICY_PATH -type f -name "*.yaml"); do
    print_info "Appling network policy from $entry"
    kubectl apply -f "$entry"
  done
fi
# label kube-system namespace, label is required for sensitive network policy
kubectl label namespace kube-system name=kube-system

#12
kubectl create clusterrolebinding owner-cluster-admin-binding \
    --clusterrole cluster-admin \
    --user system:serviceaccount:default:default
sleep 10

#13
# Allow services to bind to 80+ ports, as the default range is 30000-32767
# --service-node-port-range option is added as a next line after init command "- kube-apiserver"
# kubelet monitors /etc/kubernetes/manifests folder, so kube-api pod will be recreated automatically
sed -i '/- kube-apiserver/a \    \- --service-node-port-range=80-32767' /etc/kubernetes/manifests/kube-apiserver.yaml
sleep 30

set +e
