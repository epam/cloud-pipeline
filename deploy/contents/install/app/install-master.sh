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
# Try yum default repository
# Using this specific version to overcome centos 7.4+ etcd health issue https://github.com/etcd-io/etcd/issues/10310 which leads to kube controller/scheduler/dns failures)
yum install -y 2:docker-1.13.1-75.git8633870.el7.centos.x86_64 || docker_install_failed=1
if [[ "$docker_install_failed" == "1" ]]; then
  echo "Unable to install 2:docker-1.13.1-75.git8633870.el7.centos.x86_64 trying docker-ce"
  yum install -y yum-utils \
    device-mapper-persistent-data \
    lvm2 && \
  yum-config-manager \
      --add-repo \
      https://download.docker.com/linux/centos/docker-ce.repo && \
  yum install -y  docker-ce-18.09.1 \
                  docker-ce-cli-18.09.1 \
                  containerd.io
fi

# Configure docker to use systemd as a cgroup driver
mkdir -p /etc/docker
cat > /etc/docker/daemon.json <<EOF
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ]
}
EOF
mkdir -p /etc/systemd/system/docker.service.d

sed -i '/native.cgroupdriver/d' /usr/lib/systemd/system/docker.service

#6.2 - Kube
yum install -y \
            kubeadm-1.7.5-0.x86_64 \
            kubectl-1.7.5-0.x86_64 \
            kubelet-1.7.5-0.x86_64 \
            kubernetes-cni-0.5.1-0.x86_64

#7
sed -i 's/Environment="KUBELET_CADVISOR_ARGS=--cadvisor-port=0"/Environment="KUBELET_CADVISOR_ARGS=--cadvisor-port=4194"/g' /etc/systemd/system/kubelet.service.d/10-kubeadm.conf
sed -i 's/Environment="KUBELET_CGROUP_ARGS=--cgroup-driver=cgroupfs"/Environment="KUBELET_CGROUP_ARGS=--cgroup-driver=systemd"/g' /etc/systemd/system/kubelet.service.d/10-kubeadm.conf

#8
systemctl daemon-reload
systemctl enable docker
systemctl enable kubelet
systemctl start docker
systemctl start kubelet

# TODO: here and further - implement a smarter approach to wait for the kube service to init
sleep 10

#9
FLANNEL_CIDR=${FLANNEL_CIDR:-"10.244.0.0/16"}
kubeadm_init_cmd="kubeadm init --pod-network-cidr=$FLANNEL_CIDR --kubernetes-version v1.7.5 --skip-preflight-checks &> $HOME/kubeadm_init.log || kubeadm_failed=1"
eval "$kubeadm_init_cmd"
if [[ "$kubeadm_failed" == "1" ]]; then
  echo "kubeadm returned non-zero exit code, will try once more"
  kubeadm reset
  eval "$kubeadm_init_cmd"
fi

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

# 14
# Wait for all pods from the kube-system namespace to be ready (mainly kube-dns and flannel)
not_ready_pods_timeout=10
not_ready_pods_retry_count=60
while : ; do
  sleep $not_ready_pods_timeout
  not_ready_pods_retry_count=$((not_ready_pods_retry_count-1))

  if [ $not_ready_pods_retry_count -eq 0 ]; then
    echo "System pods were not able to start"
    exit 1
  fi

  not_ready_pods=$(kubectl get po --namespace kube-system -o json | \
                    jq -r '.items[].status.containerStatuses[] | select(.ready == false) | .name') || \
                    not_ready_pods_failed=1
  if [ "$not_ready_pods_failed" == "1" ]; then
    echo "Unable to get system pods information, waiting next $not_ready_pods_timeout seconds"
    unset not_ready_pods_failed
    continue
  fi
  if [ -z "$not_ready_pods" ]; then
    echo "All system pods are ready"
    break
  fi
  

  echo "The following system pods are not ready yet, waiting next $not_ready_pods_timeout seconds"
  echo $not_ready_pods
  echo
done
