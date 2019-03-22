#!/bin/bash
mkfs -t ext4 /dev/xvdb
mkdir /ebs
mount /dev/xvdb /ebs
echo "/dev/xvdb /ebs ext4 defaults,nofail 0 2" >> /etc/fstab
mkdir -p /ebs/runs
mkdir -p /ebs/reference
rm -rf /ebs/lost+found/
cat <<EOT > /etc/docker/daemon.json
{
  "graph": "/ebs/docker",
  "storage-driver": "overlay2",
  "storage-opts": [
    "overlay2.override_kernel_check=true"
  ]
}
EOT
echo "STORAGE_DRIVER=" >> /etc/sysconfig/docker-storage-setup
sudo mkdir -p /etc/docker/certs.d/
@DOCKER_CERTS@

# Load NFS module
modprobe nfs
modprobe nfsd

systemctl enable docker
systemctl enable kubelet
systemctl start docker
kubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --skip-preflight-checks
systemctl start kubelet
nc -l -k 8888 &
