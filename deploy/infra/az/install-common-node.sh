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


AZURE_RESOURCE_GROUP={{AZURE_RESOURCE_GROUP}}
instance_name={{instance_name}}
region={{region}}

rpm --import https://packages.microsoft.com/keys/microsoft.asc
sh -c 'echo -e "[azure-cli]\nname=Azure CLI\nbaseurl=https://packages.microsoft.com/yumrepos/azure-cli\nenabled=1\ngpgcheck=1\ngpgkey=https://packages.microsoft.com/keys/microsoft.asc" > /etc/yum.repos.d/azure-cli.repo'

# Install common
yum install -y  nc \
                python \
                curl \
                azure-cli \
                coreutils

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

# create a script that will parse and run user data every start up time
CUSTOM_USER_ACTIONS_SCRIPT="/usr/local/user-data-execute"
cat <<EOF >$CUSTOM_USER_ACTIONS_SCRIPT
#!/bin/bash
# chkconfig: 345 99 10

rdom () { local IFS=\> ; read -d \< E C ;}

wait_file() {
  local file="\$1"; shift
  local wait_seconds="\${1:-10}"; shift # 10 seconds as default timeout

  until test \$((wait_seconds--)) -eq 0 -o -f "\$file" ; do sleep 1; done

  ((++wait_seconds))
}

custom_data_file=/var/lib/waagent/CustomData

wait_file "\$custom_data_file" 300 || {
  echo "Waagent file missing after waiting for \$? seconds: \$custom_data_file"
}

cat /var/lib/waagent/CustomData | base64 --decode | /bin/bash

waagent_file=/var/lib/waagent/ovf-env.xml

wait_file "\$waagent_file" 300 || {
  echo "Waagent file missing after waiting for \$? seconds: \$waagent_file"
  exit 1
}

while rdom; do
    if [[ \$E = *CustomData* ]]; then
        echo \$C | base64 --decode | /bin/bash
        exit 0
    fi
done < /var/lib/waagent/ovf-env.xml

EOF

chmod +x $CUSTOM_USER_ACTIONS_SCRIPT

echo "bash $CUSTOM_USER_ACTIONS_SCRIPT" >> /etc/rc.d/rc.local
chmod +x /etc/rc.d/rc.local

mkdir -p ~/.azure
export AZURE_AUTH_LOCATION=~/.azure/credentials
cat <<EOF > $AZURE_AUTH_LOCATION
{{AZURE_CREDS}}
EOF

echo "Installing jq"
wget -q "https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64" -O /usr/bin/jq
chmod +x /usr/bin/jq

az login --service-principal --username $(cat $AZURE_AUTH_LOCATION | jq -r .clientId) --password $(cat $AZURE_AUTH_LOCATION | jq -r .clientSecret) --tenant $(cat $AZURE_AUTH_LOCATION | jq -r .tenantId)
# delete service credentials from the image
rm -f $AZURE_AUTH_LOCATION
rm -f /var/lib/waagent/CustomData
az vm update --resource-group $AZURE_RESOURCE_GROUP --name $instance_name --set tags.user_data=done


