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

# Install common
yum install -y  nc \
                python \
                curl \
                coreutils


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
yum-config-manager \
    --add-repo \
    https://download.docker.com/linux/centos/docker-ce.repo && \
yum install -y  docker-ce-18.09.1 \
                docker-ce-cli-18.09.1 \
                containerd.io
if [ $? -ne 0 ]; then
  echo "Unable to install docker from the official repository, trying to use default docker-18.06*"

  # Otherwise try to install default docker (e.g. if it's amazon linux)
  yum install -y docker-18.06*
  if [ $? -ne 0 ]; then
    echo "Unable to install default docker-18.06* too, exiting"
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

# Install nvidia driver
wget http://us.download.nvidia.com/tesla/384.145/NVIDIA-Linux-x86_64-384.145.run && \
sh NVIDIA-Linux-x86_64-384.145.run --silent

# Install nvidia docker
distribution=$(. /etc/os-release;echo $ID$VERSION_ID) 
curl -s -L https://nvidia.github.io/nvidia-container-runtime/$distribution/nvidia-container-runtime.repo | \
  sudo tee /etc/yum.repos.d/nvidia-container-runtime.repo
curl -s -L https://nvidia.github.io/libnvidia-container/$DIST/libnvidia-container.repo | \
  sudo tee /etc/yum.repos.d/libnvidia-container.repo
curl -s -L https://nvidia.github.io/nvidia-docker/$distribution/nvidia-docker.repo | \
  sudo tee /etc/yum.repos.d/nvidia-docker.repo

yum install nvidia-docker2 -y && \
yum install libnvidia-container1 -y


# create a script that will parse and run user data every start up time
CUSTOM_USER_ACTIONS_SCRIPT="/usr/local/user-data-execute"
cat <<'EOF' >$CUSTOM_USER_ACTIONS_SCRIPT
#!/bin/bash

exec > /var/log/user_data_rc.log 2>&1

rdom () { local IFS=\> ; read -d \< E C ;}

custom_data_file=/var/lib/waagent/CustomData
waagent_file=/var/lib/waagent/ovf-env.xml

wait_attempts=120
while [ "$wait_attempts" -ne 0 ]; do
  if [ -f "$custom_data_file" ]; then
    echo "Custom data file found at $custom_data_file"
    cat /var/lib/waagent/CustomData | base64 --decode | /bin/bash
    echo "$custom_data_file executed"
    exit 0
  fi
  if [ -f "$waagent_file" ]; then
    echo "Custom data file found at $waagent_file"
    while rdom; do
      if [[ $E = *CustomData* ]]; then
          echo $C | base64 --decode | /bin/bash
          echo "$waagent_file executed"
          exit 0
      fi
    done < $waagent_file

    echo "$waagent_file WAS NOT executed, as <CustomData> tag was not found. Will proceed with waiting"
  fi
  wait_attempts=$((wait_attempts-1))
  sleep 1
done

echo "None of the Custom Data files was found: $custom_data_file , $waagent_file in the $wait_attempts seconds"
EOF

chmod +x $CUSTOM_USER_ACTIONS_SCRIPT

echo "bash $CUSTOM_USER_ACTIONS_SCRIPT" >> /etc/rc.d/rc.local
chmod +x /etc/rc.d/rc.local

# delete CustomData from the image
rm -f /var/lib/waagent/CustomData
