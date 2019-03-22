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
echo "STORAGE_DRIVER=" >> /etc/sysconfig/docker-storage-setup
sudo mkdir -p /etc/docker/certs.d/
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

# Start docker and kubelet
service docker start
kubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --skip-preflight-checks
service kubelet start
service docker restart

# Add support for joining node to kube cluster after starting
echo -e "service docker start\nkubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --skip-preflight-checks\nservice kubelet start" >> /etc/rc.local

nc -l -k 8888 &