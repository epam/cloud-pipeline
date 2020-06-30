#!/bin/bash
user_data_log="/var/log/user_data.log"
exec > "$user_data_log" 2>&1

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

    # /etc/resolv.conf will be overwritten by DHCP client after reboot
    # As a quick fix - make /etc/resolv.conf immutable
    chattr -i /etc/resolv.conf
    sed -i '/nameserver/d' /etc/resolv.conf
    echo "nameserver $nameserver" >> /etc/resolv.conf
    chattr +i /etc/resolv.conf
  fi
}

function setup_swap_device {
    local swap_size="${1:-0}"
    if [[ "${swap_size}" == "@"*"@" ]]; then
        return
    fi
    local unmounted_drives=$(lsblk -sdrpn -o NAME,TYPE,MOUNTPOINT | awk '$2 == "disk" && $3 == "" { print $1 }')
    local drive_num=0
    local swap_drive_name=""
    for drive_name in $unmounted_drives
    do
        drive_num=$(( drive_num + 1 ))
        drive_size_str=$(lsblk -sdrpn -o SIZE "${drive_name}")
        if [[ "${drive_size_str: -1}" == "G" ]]; then
            drive_size=${drive_size_str%"G"}
            if (( swap_size == drive_size )); then
                swap_drive_name="${drive_name}"
            fi
        fi
    done
    if [[ -z "${swap_drive_name}" ]]; then
        echo "Block device matching swap size ${swap_size}G was not found. Swap device won't be configured."
    elif (( drive_num < 2 )); then
        echo "${drive_num} device found. Swap device won't be configured."
    else
        echo "Swap device ${swap_drive_name} will be configured"
        mkswap "${swap_drive_name}"
        if [[ $? -ne 0 ]]; then
            echo "Unable to mkswap at $swap_drive_name"
            return 1
        fi
        swapon "${swap_drive_name}"
        if [[ $? -ne 0 ]]; then
            echo "Unable to swapon at $swap_drive_name"
            return 1
        fi
        swap_drive_uuid=$(lsblk -sdrpn -o NAME,UUID | awk '$1 == "'"$swap_drive_name"'" { print $2 }')
        echo "UUID=$swap_drive_uuid none swap sw 0 0" >> /etc/fstab
    fi
}

# Setup swap device configuration
swap_size="@swap_size@"
setup_swap_device "${swap_size:-0}"

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

  mkfs.btrfs -f -d single $DRIVE_NAME
  mkdir $MOUNT_POINT
  mount $DRIVE_NAME $MOUNT_POINT
  DRIVE_UUID=$(btrfs filesystem show "$MOUNT_POINT" | head -n 1 | awk '{print $NF}')
  echo "UUID=$DRIVE_UUID $MOUNT_POINT btrfs defaults,nofail 0 2" >> /etc/fstab
  mkdir -p $MOUNT_POINT/runs
  mkdir -p $MOUNT_POINT/reference
  rm -rf $MOUNT_POINT/lost+found/   
 
done

# Stop docker if it is running
systemctl stop docker

# If /ebs/docker is missing - consider this is a first run (not a resume)
_KUBE_SYSTEM_PODS_NEEDS_LOAD=1
if [ ! -d "/ebs/docker" ]; then
  # Grab system docker images from the public storage
  _KUBE_SYSTEM_PODS_DISTR_PREFIX="@SYSTEM_PODS_DISTR_PREFIX@"
  if [ ! "$_KUBE_SYSTEM_PODS_DISTR_PREFIX" ] || [[ "$_KUBE_SYSTEM_PODS_DISTR_PREFIX" == "@"*"@" ]]; then
    _KUBE_SYSTEM_PODS_DISTR_PREFIX="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/kube/1.15.4/docker"
  fi
  mkdir -p /tmp/system-dockers
  wget -q "${_KUBE_SYSTEM_PODS_DISTR_PREFIX}/calico-node-v3.14.1.tar" -O /tmp/system-dockers/calico-node-v3.14.1.tar && \
  wget -q "${_KUBE_SYSTEM_PODS_DISTR_PREFIX}/calico-pod2daemon-flexvol-v3.14.1.tar" -O /tmp/system-dockers/calico-pod2daemon-flexvol-v3.14.1.tar && 
  wget -q "${_KUBE_SYSTEM_PODS_DISTR_PREFIX}/calico-cni-v3.14.1.tar" -O /tmp/system-dockers/calico-cni-v3.14.1.tar && \
  wget -q "${_KUBE_SYSTEM_PODS_DISTR_PREFIX}/k8s.gcr.io-kube-proxy-v1.15.4.tar" -O /tmp/system-dockers/k8s.gcr.io-kube-proxy-v1.15.4.tar && \
  wget -q "${_KUBE_SYSTEM_PODS_DISTR_PREFIX}/quay.io-coreos-flannel-v0.11.0.tar" -O /tmp/system-dockers/quay.io-coreos-flannel-v0.11.0.tar && \
  wget -q "${_KUBE_SYSTEM_PODS_DISTR_PREFIX}/k8s.gcr.io-pause-3.1.tar" -O /tmp/system-dockers/k8s.gcr.io-pause-3.1.tar
  # This flag is used further to load the images
  _KUBE_SYSTEM_PODS_NEEDS_LOAD=$?
  # If can't pull from the public repo - try to get the "baked-in" images
  if [ $_KUBE_SYSTEM_PODS_NEEDS_LOAD -ne 0 ]; then
    # Move any existing docker configuration to the mounted directory (if we have any existing docker images)
    if [ -d "/var/lib/docker" ] && [ ! -d "/ebs/docker" ]; then
      mv /var/lib/docker /ebs/
    fi
  fi
fi

mkdir -p /etc/docker
# Check nvidia drivers are installed
if check_installed "nvidia-smi"; then
  # Fix nvidia-smi performance
  nvidia-persistenced --persistence-mode

# We are still using overlay2 storage driver over the btrfs backing filesystem, as it is proved to work correctly
# If btrfs is used as a storage driver - this confuses cadvisor and kubelet (filesystem can't be enumerated within a container)
cat <<EOT > /etc/docker/daemon.json
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "data-root": "/ebs/docker",
  "storage-driver": "overlay2",
  "max-concurrent-uploads": 1,
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
  "exec-opts": ["native.cgroupdriver=systemd"],
  "data-root": "/ebs/docker",
  "storage-driver": "overlay2",
  "max-concurrent-uploads": 1
}
EOT
fi

echo "STORAGE_DRIVER=" >> /etc/sysconfig/docker-storage-setup
mkdir -p /etc/docker/certs.d/
@DOCKER_CERTS@

# Setup docker cli location for DinD
docker_cli_bin="/bin/docker"
if [ ! -f "$docker_cli_bin" ]; then
  docker_cli_location="$(command -v docker)"
  if [ "$docker_cli_location" ]; then
    \cp $docker_cli_location $docker_cli_bin
  else
    echo 'docker executable not found via "command -v docker"'
  fi
fi

# Load NFS module
modprobe nfs
modprobe nfsd

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
    _KUBE_NODE_NAME="$_CLOUD_INSTANCE_ID"

    # Create user for cloud pipeline access, this is required only for AWS. Other cloud providers will create appropriate user automatically
    useradd pipeline
    cp -r /home/ec2-user/.ssh /home/pipeline/.ssh
    chown -R pipeline. /home/pipeline/.ssh
    chmod 700 .ssh
    usermod -a -G wheel pipeline
    echo 'pipeline ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers.d/cloud-init
elif [[ $cloud == *"Microsoft"* ]]; then
    _CLOUD_REGION=$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/location?api-version=2018-10-01&format=text')
    _CLOUD_INSTANCE_AZ=$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/zone?api-version=2018-10-01&format=text')
    _CLOUD_INSTANCE_ID="$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/name?api-version=2018-10-01&format=text')"
    _CLOUD_INSTANCE_TYPE=$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/vmSize?api-version=2018-10-01&format=text')
    _KUBE_NODE_NAME="$(hostname)"

    _CLOUD_INSTANCE_IMAGE_ID="$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/publisher?api-version=2018-10-01&format=text'):$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/product?api-version=2018-10-01&format=text'):$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/name?api-version=2018-10-01&format=text')"
    if [[ "$_CLOUD_INSTANCE_IMAGE_ID" == '::' ]]; then
        _CLOUD_INSTANCE_IMAGE_ID=""
    fi
    _CLOUD_PROVIDER=AZURE

    CHECK_AZURE_EVENTS_COMMAND="curl -k -H Metadata:true http://169.254.169.254/metadata/scheduledevents?api-version=2017-11-01 2> /dev/null | grep -q Preempt && kubectl label node $(hostname) cloud-pipeline/preempted=true --kubeconfig='/etc/kubernetes/kubelet.conf'"

    # run 3 cron jobs to be able to check events each 20 seconds
    crontab -l | { cat ; echo -e "* * * * * $CHECK_AZURE_EVENTS_COMMAND \n* * * * * sleep 20 && $CHECK_AZURE_EVENTS_COMMAND \n* * * * * sleep 40 && $CHECK_AZURE_EVENTS_COMMAND" ; } | crontab -

elif [[ $gcloud_header == *"Google"* ]]; then
    _CLOUD_INSTANCE_AZ=$(curl -H "Metadata-Flavor:Google"  http://169.254.169.254/computeMetadata/v1/instance/zone | grep zones | cut -d/ -f4)
    _CLOUD_REGION=${_CLOUD_INSTANCE_AZ}
    _CLOUD_INSTANCE_ID=$(curl -H "Metadata-Flavor:Google"  http://169.254.169.254/computeMetadata/v1/instance/name)
    _CLOUD_INSTANCE_TYPE=$(curl -H "Metadata-Flavor:Google"  http://169.254.169.254/computeMetadata/v1/instance/machine-type | grep machineTypes | cut -d/ -f4)
    _CLOUD_INSTANCE_IMAGE_ID=$(curl -H "Metadata-Flavor:Google"  http://169.254.169.254/computeMetadata/v1/instance/image | cut -d/ -f5)
    _CLOUD_PROVIDER=GCP
    _KUBE_NODE_NAME="$_CLOUD_INSTANCE_ID"
fi

# Setup well-know hostnames
# This will be replaced by "echo {IP} {HOSTNAME} >> /etc/hosts" in nodeup.py
@WELL_KNOWN_HOSTS@

# Setup proxies for a current region
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

## Configure kubelet to write logs to the file
_KUBELET_LOG_PATH=/var/log/kubelet
mkdir -p $_KUBELET_LOG_PATH
_KUBE_LOG_ARGS="--logtostderr=false --log-dir=$_KUBELET_LOG_PATH"

_KUBE_NODE_NAME="${_KUBE_NODE_NAME:-$(hostname)}"
_KUBE_NODE_NAME_ARGS="--hostname-override $_KUBE_NODE_NAME"

# FIXME: use the .NodeRegistration.KubeletExtraArgs object in the configuration files
_KUBELET_INITD_DROPIN_PATH="/etc/sysconfig/kubelet"
rm -f $_KUBELET_INITD_DROPIN_PATH

## Configure kubelet reservations
## FIXME: shall be moved to the preferences

## Here we calculate reservation for RAM, depending on the machine size
## By default, we reserve 5% of the available RAM, but not less that 500Mi and not more than 2000Mi
_KUBE_RESERVED_RATIO=5
_KUBE_RESERVED_MIN_MB=500
_KUBE_RESERVED_MAX_MB=2000
### Getting total RAM in Mb
_KUBE_NODE_MEM_TOTAL_MB=$(awk '/MemTotal/ { printf "%.0f", $2/1024 }' /proc/meminfo)
### Calculate 5% reservation for each of the two system services (kubelet and system)
_KUBE_NODE_MEM_RESERVED_MB=$(( $_KUBE_NODE_MEM_TOTAL_MB * $_KUBE_RESERVED_RATIO / 100 / 2 ))
### Apply upper and lower limits restrictions (500 and 2000)
_KUBE_NODE_MEM_RESERVED_MB=$(( $_KUBE_NODE_MEM_RESERVED_MB > $_KUBE_RESERVED_MAX_MB ? $_KUBE_RESERVED_MAX_MB : $_KUBE_NODE_MEM_RESERVED_MB ))
_KUBE_NODE_MEM_RESERVED_MB=$(( $_KUBE_NODE_MEM_RESERVED_MB < $_KUBE_RESERVED_MIN_MB ? $_KUBE_RESERVED_MIN_MB : $_KUBE_NODE_MEM_RESERVED_MB ))
### Setup the  calculated values into the kubelet args
_KUBE_RESERVED_ARGS="--kube-reserved cpu=350m,memory=${_KUBE_NODE_MEM_RESERVED_MB}Mi,ephemeral-storage=1Gi"
_KUBE_SYS_RESERVED_ARGS="--system-reserved cpu=350m,memory=${_KUBE_NODE_MEM_RESERVED_MB}Mi,ephemeral-storage=1Gi"
_KUBE_EVICTION_ARGS="--eviction-hard= --eviction-soft= --eviction-soft-grace-period= --pod-max-pids=-1"
_KUBE_FAIL_ON_SWAP_ARGS="--fail-swap-on=false"

## Append extra kube-config to the systemd config
echo "KUBELET_EXTRA_ARGS=$_KUBE_NODE_INSTANCE_LABELS $_KUBE_LOG_ARGS $_KUBE_NODE_NAME_ARGS $_KUBE_RESERVED_ARGS $_KUBE_SYS_RESERVED_ARGS $_KUBE_EVICTION_ARGS $_KUBE_FAIL_ON_SWAP_ARGS" >> $_KUBELET_INITD_DROPIN_PATH
chmod +x $_KUBELET_INITD_DROPIN_PATH

# Start docker and kubelet
systemctl enable docker
systemctl enable kubelet
systemctl start docker

if [ $_KUBE_SYSTEM_PODS_NEEDS_LOAD -eq 0 ]; then
  for _KUBE_SYSTEM_POD_FILE in /tmp/system-dockers/*.tar; do 
    docker load -i $_KUBE_SYSTEM_POD_FILE
    rm -f $_KUBE_SYSTEM_POD_FILE
  done
fi

kubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --discovery-token-unsafe-skip-ca-verification --node-name $_KUBE_NODE_NAME --ignore-preflight-errors all
systemctl start kubelet

update_nameserver "$nameserver_post_val" "infinity"

_API_URL="@API_URL@"
_API_TOKEN="@API_TOKEN@"
_MOUNT_POINT="/ebs"
_CURRENT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
cp "$_CURRENT_DIR/fsautoscale" "/usr/bin/fsautoscale"
cat >/etc/systemd/system/fsautoscale.service <<EOL
[Unit]
Description=Cloud Pipeline Filesystem Autoscaling Daemon
Documentation=https://cloud-pipeline.com/

[Service]
Restart=always
StartLimitInterval=0
RestartSec=10
Environment="API_ARGS=--api-url $_API_URL --api-token $_API_TOKEN"
Environment="NODE_ARGS=--node-name $_KUBE_NODE_NAME"
Environment="MOUNT_POINT_ARGS=--mount-point $_MOUNT_POINT"
ExecStart=/usr/bin/fsautoscale \$API_ARGS \$NODE_ARGS \$MOUNT_POINT_ARGS

[Install]
WantedBy=multi-user.target
EOL
systemctl enable fsautoscale
systemctl start fsautoscale

# Setup the scripts to restore the state of the "paused" job
#   1. NVIDIA-specific scripts for GPU nodes
#     - Enable nvidia persistence mode
#     - Call nvidia-smi to cache the devices listings
if check_installed "nvidia-smi"; then
  cat >> /etc/rc.local << EOF
nvidia-persistenced --persistence-mode
nvidia-smi
EOF
fi
#   2. All other nodes: add support for joining node to kube cluster after starting the "paused" job
cat >> /etc/rc.local << EOF
systemctl start docker
kubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --discovery-token-unsafe-skip-ca-verification --node-name $_KUBE_NODE_NAME --ignore-preflight-errors all
systemctl start kubelet
EOF

nc -l -k 8888 &
