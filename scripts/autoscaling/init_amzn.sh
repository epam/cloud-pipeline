#!/bin/bash

# Mount all drives that do not have mount points yet. Each drive will be mounted to /ebsN folder (N is a number of a drive)
UNMOUNTED_DRIVES=$(lsblk -sdrpn -o NAME,TYPE,MOUNTPOINT | awk '$2 == "disk" && $3 == "" { print $1 }')
DRIVE_NUM=0
for DRIVE_NAME in $UNMOUNTED_DRIVES
do
  MOUNT_POINT="/ebs"
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
service docker stop
rm -rf /var/lib/docker

# Setup docker config
mkdir -p /etc/docker
cat <<EOT > /etc/docker/daemon.json
{
  "data-root": "/ebs/docker",
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ]
}
EOT
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

# Prepare to tag aws-specific info
## Get instance metadata information
_AWS_REGION=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep region | cut -d\" -f4)
_AWS_INSTANCE_AZ=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep availabilityZone | cut -d\" -f4)
_AWS_INSTANCE_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep instanceId | cut -d\" -f4)
_AWS_INSTANCE_TYPE=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep instanceType | cut -d\" -f4)
_AWS_INSTANCE_AMI_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep imageId | cut -d\" -f4)

# Setup well-know hostnames
# This will be replaced by "echo {IP} {HOSTNAME} >> /etc/hosts" in nodeup.py
@WELL_KNOWN_HOSTS@

# Setup proxies for a current AWS region
nameserver_val="@dns_proxy@"
http_proxy_val="@http_proxy@"
https_proxy_val="@https_proxy@"
no_proxy_val="@no_proxy@"

if [ "$nameserver_val" ] && [[ "$nameserver_val" != "@"*"@" ]]; then
  sed -i '/nameserver/d' /etc/resolv.conf
  echo "nameserver $nameserver_val" >> /etc/resolv.conf
fi
if [ "$http_proxy_val" ] && [[ "$http_proxy_val" != "@"*"@" ]]; then
  echo "unset http_proxy HTTP_PROXY; export http_proxy=$http_proxy_val" | tee -a /etc/sysconfig/docker /root/.bashrc /etc/profile
fi
if [ "$https_proxy_val" ] && [[ "$https_proxy_val" != "@"*"@" ]]; then
  echo "unset https_proxy HTTPS_PROXY; export https_proxy=$https_proxy_val" | tee -a /etc/sysconfig/docker /root/.bashrc /etc/profile
fi
if [ "$no_proxy_val" ] && [[ "$no_proxy_val" != "@"*"@" ]]; then
  echo "unset no_proxy NO_PROXY; export no_proxy=$no_proxy_val" | tee -a /etc/sysconfig/docker /root/.bashrc /etc/profile
fi

## Build kubelet node-labels string, so all the tags will be applied at a node join time
_KUBE_NODE_INSTANCE_LABELS="--node-labels=aws_region=$_AWS_REGION,aws_az=$_AWS_INSTANCE_AZ,aws_ins_id=$_AWS_INSTANCE_ID,aws_ins_type=$_AWS_INSTANCE_TYPE,aws_ami=$_AWS_INSTANCE_AMI_ID"
_KUBELET_INITD_DROPIN_PATH="/etc/init.d/kubelet"

## Check that "node-labels" was not previosly added to kubelet config (for some reason...) if not - append "node-labels" string to the kubelet start command
if ! grep -q 'node-labels' $_KUBELET_INITD_DROPIN_PATH; then
  sed -i "/^start()/i\KUBELET_KUBECONFIG_ARGS=\"\$KUBELET_KUBECONFIG_ARGS $_KUBE_NODE_INSTANCE_LABELS\"" $_KUBELET_INITD_DROPIN_PATH
fi

# Start docker and kubelet
service docker start
kubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --skip-preflight-checks
service kubelet start

# Add support for joining node to kube cluster after starting
echo -e "service docker start\nkubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --skip-preflight-checks\nservice kubelet start" >> /etc/rc.local

nc -l -k 8888 &
