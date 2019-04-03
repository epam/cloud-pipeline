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

# User Data script to initialize common/gpu instances for azure instances

# Output out/err to the logfile
user_data_log="/var/log/user_data.log"
exec > $user_data_log 2>&1

function check_installed {
    command -v "$1" >/dev/null 2>&1
    return $?
}

function update_nameserver {
  local nameserver="$1"
  local ping_times="$2"

  local is_nameserver_reachable="0"
  if [ "$nameserver" ] && [[ "$nameserver" != "@"*"@" ]]; then
    if [ "$ping_times" ]; then
      if [ "$ping_times" == "infinity" ]; then
        ping_times=86400
      fi
      for i in $(seq 1 $ping_times); do 
        echo "Pinging nameserver $nameserver on port 53"
        if nc -z -w 1 $nameserver 53 ; then
          echo "nameserver $nameserver can be reached on port 53"
          is_nameserver_reachable="1"
          break
        fi
      done

      if [ "$is_nameserver_reachable" != "1" ]; then
        echo "Elapsed $ping_times retries, but $nameserver can NOT be reached on port 53"
      fi
    fi

    sed -i '/nameserver/d' /etc/resolv.conf
    echo "nameserver $nameserver" >> /etc/resolv.conf
  fi
}

# Mount all drives that do not have mount points yet. Each drive will be mounted to /ebsN folder (N is a number of a drive)
UNMOUNTED_DRIVES=$(lsblk -sdrpn -o NAME,TYPE,MOUNTPOINT | awk '$2 == "disk" && $3 == "" { print $1 }')
DRIVE_NUM=0
for DRIVE_NAME in $UNMOUNTED_DRIVES
do
  MOUNT_POINT="/ebs"

  PARTITION_RESULT=$(sfdisk -d $DRIVE_NAME 2>&1)
  if [[ $PARTITION_RESULT == "" ]]; then
      (echo o; echo n; echo p; echo; echo; echo; echo w) | fdisk $DRIVE_NAME
      #we created partition ${DRIVE_NAME}1 and now should use it as DRIVE_NAME
      DRIVE_NAME="${DRIVE_NAME}1"
  elif [[ $PARTITION_RESULT == *"No such device or address"* ]]; then
      continue
  fi

  DRIVE_NUM=$((DRIVE_NUM+1))

  if [[ $DRIVE_NUM != 1 ]]
  then
    MOUNT_POINT=$MOUNT_POINT$DRIVE_NUM
  fi

  mkfs -t ext4 $DRIVE_NAME
  mkdir $MOUNT_POINT
  mount $DRIVE_NAME $MOUNT_POINT
  echo "$DRIVE_NAME $MOUNT_POINT ext4 defaults,nofail 0 2" >> /etc/fstab
  mkdir -p $MOUNT_POINT/runs
  mkdir -p $MOUNT_POINT/reference
  rm -rf $MOUNT_POINT/lost+found/   
 
done

# Stop docker if it is running and clean any remaining default directories
systemctl stop docker
rm -rf /var/lib/docker

mkdir -p /etc/docker
# Check nvidia drivers are installed
if check_installed "nvidia-smi"; then
  # Fix nvidia-smi performance
  nvidia-persistenced --persistence-mode

cat <<EOT > /etc/docker/daemon.json
{
  "data-root": "/ebs/docker",
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ],
  "default-runtime": "nvidia",
   "runtimes": {
        "nvidia": {
            "path": "/usr/bin/nvidia-container-runtime",
            "runtimeArgs": []
        }
    }
}
EOT
else
  # Setup docker config for non-gpu
cat <<EOT > /etc/docker/daemon.json
{
  "data-root": "/ebs/docker",
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ]
}
EOT
fi

echo "STORAGE_DRIVER=" >> /etc/sysconfig/docker-storage-setup
mkdir -p /etc/docker/certs.d/
@DOCKER_CERTS@

# Load NFS module
modprobe nfs
modprobe nfsd

# Create user for cloud pipeline access
useradd pipeline
cp -r /home/ec2-user/.ssh /home/pipeline/.ssh
chown -R pipeline. /home/pipeline/.ssh
chmod 700 .ssh
usermod -a -G wheel pipeline
echo 'pipeline ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers.d/cloud-init

chmod +x /etc/rc.d/rc.local

# Prepare to tag cloud-specific info
## Get instance metadata information
cloud=$(curl --head -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep Server | cut -f2 -d:)
gcloud_header=$(curl --head -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep Metadata-Flavor | cut -f2 -d:)

if [[ $cloud == *"EC2"* ]]; then
    _CLOUD_REGION=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep region | cut -d\" -f4)
    _CLOUD_INSTANCE_AZ=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep availabilityZone | cut -d\" -f4)
    _CLOUD_INSTANCE_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep instanceId | cut -d\" -f4)
    _CLOUD_INSTANCE_TYPE=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep instanceType | cut -d\" -f4)
    _CLOUD_INSTANCE_IMAGE_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep imageId | cut -d\" -f4)
    _CLOUD_PROVIDER=AWS
elif [[ $cloud == *"Microsoft"* ]]; then
    _CLOUD_REGION=$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/location?api-version=2018-10-01&format=text')
    _CLOUD_INSTANCE_AZ=$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/zone?api-version=2018-10-01&format=text')

    _CLOUD_INSTANCE_ID="$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/name?api-version=2018-10-01&format=text')"

    _CLOUD_INSTANCE_TYPE=$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/vmSize?api-version=2018-10-01&format=text')

    _CLOUD_INSTANCE_IMAGE_ID="$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/publisher?api-version=2018-10-01&format=text'):$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/product?api-version=2018-10-01&format=text'):$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/name?api-version=2018-10-01&format=text')"
    if [[ "$_CLOUD_INSTANCE_IMAGE_ID" == '::' ]]; then
        _CLOUD_INSTANCE_IMAGE_ID=""
    fi
    _CLOUD_PROVIDER=AZURE
elif [[ $gcloud_header == *"Google"* ]]; then
    _CLOUD_INSTANCE_AZ=$(curl -H "Metadata-Flavor:Google"  http://169.254.169.254/computeMetadata/v1/instance/zone | grep zones | cut -d/ -f4)
    _CLOUD_REGION=${_CLOUD_INSTANCE_AZ::-2}
    _CLOUD_INSTANCE_ID=$(curl -H "Metadata-Flavor:Google"  http://169.254.169.254/computeMetadata/v1/instance/name)
    _CLOUD_INSTANCE_TYPE=$(curl -H "Metadata-Flavor:Google"  http://169.254.169.254/computeMetadata/v1/instance/machine-type | grep machineTypes | cut -d/ -f4)
    _CLOUD_INSTANCE_IMAGE_ID=$(curl -H "Metadata-Flavor:Google"  http://169.254.169.254/computeMetadata/v1/instance/image | cut -d/ -f5)
    _CLOUD_PROVIDER=GCP
fi

# Setup well-know hostnames
# This will be replaced by "echo {IP} {HOSTNAME} >> /etc/hosts" in nodeup.py
@WELL_KNOWN_HOSTS@

# Setup proxies for a current AWS region
nameserver_val="@dns_proxy@"
nameserver_post_val="@dns_proxy_post@"
http_proxy_val="@http_proxy@"
https_proxy_val="@https_proxy@"
no_proxy_val="@no_proxy@"

update_nameserver "$nameserver_val" "30"
_PROXY_STRING=
if [ "$http_proxy_val" ] && [[ "$http_proxy_val" != "@"*"@" ]]; then
  _PROXY_STRING="\"http_proxy=$http_proxy_val\""
  echo "unset http_proxy HTTP_PROXY; export http_proxy=$http_proxy_val" | tee -a /etc/sysconfig/docker /root/.bashrc /etc/profile
fi
if [ "$https_proxy_val" ] && [[ "$https_proxy_val" != "@"*"@" ]]; then
  _PROXY_STRING="${_PROXY_STRING} \"https_proxy=$https_proxy_val\""
  echo "unset https_proxy HTTPS_PROXY; export https_proxy=$https_proxy_val" | tee -a /etc/sysconfig/docker /root/.bashrc /etc/profile
fi
if [ "$no_proxy_val" ] && [[ "$no_proxy_val" != "@"*"@" ]]; then
  _PROXY_STRING="${_PROXY_STRING} \"no_proxy=$no_proxy_val\""
  echo "unset no_proxy NO_PROXY; export no_proxy=$no_proxy_val" | tee -a /etc/sysconfig/docker /root/.bashrc /etc/profile
fi

if [ "$_PROXY_STRING" ]; then
  mkdir -p /etc/systemd/system/docker.service.d

cat >/etc/systemd/system/docker.service.d/http-proxy.conf <<EOL
[Service]
Environment=$_PROXY_STRING
EOL

fi

## Build kubelet node-labels string, so all the tags will be applied at a node join time
_KUBE_NODE_INSTANCE_LABELS="--node-labels=cloud_provider=$_CLOUD_PROVIDER,cloud_region=$_CLOUD_REGION,cloud_ins_id=$_CLOUD_INSTANCE_ID,cloud_ins_type=$_CLOUD_INSTANCE_TYPE"

if [[ $_CLOUD_INSTANCE_AZ != "" ]]; then
    _KUBE_NODE_INSTANCE_LABELS=$_KUBE_NODE_INSTANCE_LABELS",cloud_az=$_CLOUD_INSTANCE_AZ"
fi

if [[ $_CLOUD_INSTANCE_IMAGE_ID != "" ]]; then
    _KUBE_NODE_INSTANCE_LABELS=$_KUBE_NODE_INSTANCE_LABELS",cloud_image=$_CLOUD_INSTANCE_IMAGE_ID"
fi


_KUBELET_INITD_DROPIN_PATH="/etc/systemd/system/kubelet.service.d/20-kubelet-labels.conf"
rm -f $_KUBELET_INITD_DROPIN_PATH
## Append node-labels string to the systemd config
cat > $_KUBELET_INITD_DROPIN_PATH <<EOF
[Service]
Environment="KUBELET_EXTRA_ARGS=$_KUBE_NODE_INSTANCE_LABELS"
EOF
chmod +x $_KUBELET_INITD_DROPIN_PATH

# Start docker and kubelet
systemctl enable docker
systemctl enable kubelet
systemctl start docker
kubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --skip-preflight-checks
systemctl start kubelet

update_nameserver "$nameserver_post_val" "infinity"

# Add support for joining node to kube cluster after starting
echo -e "systemctl start docker\nkubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --skip-preflight-checks\nsystemctl start kubelet" >> /etc/rc.local

nc -l -k 8888 &
