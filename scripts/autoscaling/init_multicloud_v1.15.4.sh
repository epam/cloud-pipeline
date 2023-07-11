#!/bin/bash

launch_token="/etc/user_data_launched"
if [[ -f "$launch_token" ]]; then exit 0; fi

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

GLOBAL_DISTRIBUTION_URL="@GLOBAL_DISTRIBUTION_URL@"
if [ ! "$GLOBAL_DISTRIBUTION_URL" ] || [[ "$GLOBAL_DISTRIBUTION_URL" == "@"*"@" ]]; then
  GLOBAL_DISTRIBUTION_URL="https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/"
fi
export GLOBAL_DISTRIBUTION_URL

_WO="--timeout=10 --waitretry=1 --tries=10"
wget $_WO "${GLOBAL_DISTRIBUTION_URL}tools/nvme-cli/1.16/nvme.gz" -O /bin/nvme.gz && \
gzip -d /bin/nvme.gz && \
chmod +x /bin/nvme

swap_size="@swap_size@"
setup_swap_device "${swap_size:-0}"

FS_TYPE="@FS_TYPE@"

_ds=()
if [[ -x /bin/nvme ]]; then
  _ds=($(nvme list | grep 'Instance Storage' | awk '{ print $1 }'))
fi
UNMOUNTED_DRIVES=($(lsblk -sdrpn -o NAME,TYPE,MOUNTPOINT | awk '$2 == "disk" && $3 == "" { print $1 }'))
_dsc=( $({ printf "%s\n" "${UNMOUNTED_DRIVES[@]}" | sort -u; printf "%s\n" "${_ds[@]}" "${_ds[@]}"; } | sort | uniq -u) )
if [[ ${#_dsc[@]} > 0 ]]; then
  UNMOUNTED_DRIVES=("${_dsc[@]}")
fi
if [[ ${#UNMOUNTED_DRIVES[@]} > 1 ]]; then
  pvcreate ${UNMOUNTED_DRIVES[@]}
  vgcreate nvmevg ${UNMOUNTED_DRIVES[@]}
  lvcreate -l 100%FREE nvmevg -n nvmelv
  DRIVE_NAME=/dev/nvmevg/nvmelv
elif [[ ${#UNMOUNTED_DRIVES[@]} == 1 ]]; then
  DRIVE_NAME=${UNMOUNTED_DRIVES[0]}
  PARTITION_RESULT=$(sfdisk -d $DRIVE_NAME 2>&1)
  if [[ $PARTITION_RESULT == "" ]]; then
      (echo o; echo n; echo p; echo; echo; echo; echo w) | fdisk $DRIVE_NAME
      DRIVE_NAME="${DRIVE_NAME}1"
  elif [[ $PARTITION_RESULT == *"No such device or address"* ]]; then
      echo "Cannot create partition for ${DRIVE_NAME}, falling back to root volume"
      unset DRIVE_NAME
  fi
else
  echo "No unmounted drives found. Root volume is used for the /ebs"
fi

MOUNT_POINT="/ebs"
mkdir -p $MOUNT_POINT
if [ "$DRIVE_NAME" ]; then
  if [[ $FS_TYPE == "ext4" ]]; then
      mkfs -t ext4 $DRIVE_NAME
      mount $DRIVE_NAME $MOUNT_POINT
      echo "$DRIVE_NAME $MOUNT_POINT ext4 defaults,nofail 0 2" >> /etc/fstab
  else
    mkfs.btrfs -f -d single $DRIVE_NAME
    mount $DRIVE_NAME $MOUNT_POINT
    DRIVE_UUID=$(btrfs filesystem show "$MOUNT_POINT" | head -n 1 | awk '{print $NF}')
    echo "UUID=$DRIVE_UUID $MOUNT_POINT btrfs defaults,nofail 0 2" >> /etc/fstab
  fi
fi
mkdir -p $MOUNT_POINT/runs
mkdir -p $MOUNT_POINT/reference
rm -rf $MOUNT_POINT/lost+found

ssh_node_port="@NODE_SSH_PORT@"

if [ "$ssh_node_port" ]  && [[ "$ssh_node_port" != "@"*"@" ]]; then
  sed -i "/#Port/c\Port $ssh_node_port" /etc/ssh/sshd_config
  systemctl restart sshd
fi

systemctl stop docker

_DOCKER_SYS_IMGS="/ebs/docker-system-images"
rm -rf $_DOCKER_SYS_IMGS
_KUBE_SYSTEM_PODS_DISTR="@SYSTEM_PODS_DISTR_PREFIX@"
if [ ! "$_KUBE_SYSTEM_PODS_DISTR" ] || [[ "$_KUBE_SYSTEM_PODS_DISTR" == "@"*"@" ]]; then
  _KUBE_SYSTEM_PODS_DISTR="${GLOBAL_DISTRIBUTION_URL}tools/kube/1.15.4/docker"
fi
mkdir -p $_DOCKER_SYS_IMGS
wget $_WO "${_KUBE_SYSTEM_PODS_DISTR}/calico-node-v3.14.1.tar" -O $_DOCKER_SYS_IMGS/calico-node-v3.14.1.tar && \
wget $_WO "${_KUBE_SYSTEM_PODS_DISTR}/calico-pod2daemon-flexvol-v3.14.1.tar" -O $_DOCKER_SYS_IMGS/calico-pod2daemon-flexvol-v3.14.1.tar &&
wget $_WO "${_KUBE_SYSTEM_PODS_DISTR}/calico-cni-v3.14.1.tar" -O $_DOCKER_SYS_IMGS/calico-cni-v3.14.1.tar && \
wget $_WO "${_KUBE_SYSTEM_PODS_DISTR}/k8s.gcr.io-kube-proxy-v1.15.4.tar" -O $_DOCKER_SYS_IMGS/k8s.gcr.io-kube-proxy-v1.15.4.tar && \
wget $_WO "${_KUBE_SYSTEM_PODS_DISTR}/quay.io-coreos-flannel-v0.11.0.tar" -O $_DOCKER_SYS_IMGS/quay.io-coreos-flannel-v0.11.0.tar && \
wget $_WO "${_KUBE_SYSTEM_PODS_DISTR}/k8s.gcr.io-pause-3.1.tar" -O $_DOCKER_SYS_IMGS/k8s.gcr.io-pause-3.1.tar
if [ $? -ne 0 ]; then
  _DOCKER_SYS_IMGS="/opt/docker-system-images"
fi

mkdir -p /etc/docker

if [[ $FS_TYPE == "ext4" ]]; then
  DOCKER_STORAGE_DRIVER="overlay2"
  DOCKER_STORAGE_OPTS='"storage-opts": ["overlay2.override_kernel_check=true"],'
else
  DOCKER_STORAGE_DRIVER="btrfs"
  DOCKER_STORAGE_OPTS=""
fi

if check_installed "nvidia-smi"; then
  nvidia-persistenced --persistence-mode

cat <<EOT > /etc/docker/daemon.json
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "data-root": "/ebs/docker",
  "storage-driver": "$DOCKER_STORAGE_DRIVER",
  $DOCKER_STORAGE_OPTS
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
cat <<EOT > /etc/docker/daemon.json
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "data-root": "/ebs/docker",
  "storage-driver": "$DOCKER_STORAGE_DRIVER",
  $DOCKER_STORAGE_OPTS
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

modprobe nfs
modprobe nfsd

chmod +x /etc/rc.d/rc.local

cloud=$(curl --head -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep Server | cut -f2 -d:)
gcloud_header=$(curl --head -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep Metadata-Flavor | cut -f2 -d:)

if [[ $cloud == *"EC2"* ]]; then
    _CLOUD_REGION=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep region | cut -d\" -f4)
    _CLOUD_INSTANCE_AZ=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep availabilityZone | cut -d\" -f4)
    _CLOUD_INSTANCE_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep instanceId | cut -d\" -f4)
    _CLOUD_INSTANCE_TYPE=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep instanceType | cut -d\" -f4)
    _CLOUD_INSTANCE_IMAGE_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep imageId | cut -d\" -f4)
    _CI_IP=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)
    _CLOUD_PROVIDER=AWS
    _KUBE_NODE_NAME="$_CLOUD_INSTANCE_ID"

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
    _CI_IP=$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/ipv4/Ipaddress?api-version=2018-10-01&format=text')
    _KUBE_NODE_NAME=$(echo "$_CLOUD_INSTANCE_ID" | grep -xE "[a-zA-Z0-9\-]{1,256}" &> /dev/null && echo $_CLOUD_INSTANCE_ID || hostname)

    _CLOUD_INSTANCE_IMAGE_ID="$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/publisher?api-version=2018-10-01&format=text'):$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/product?api-version=2018-10-01&format=text'):$(curl -H Metadata:true -s 'http://169.254.169.254/metadata/instance/compute/plan/name?api-version=2018-10-01&format=text')"
    if [[ "$_CLOUD_INSTANCE_IMAGE_ID" == '::' ]]; then
        _CLOUD_INSTANCE_IMAGE_ID=""
    fi
    _CLOUD_PROVIDER=AZURE

    CHECK_AZURE_EVENTS_COMMAND="curl -k -H Metadata:true http://169.254.169.254/metadata/scheduledevents?api-version=2017-11-01 2> /dev/null | grep -q Preempt && kubectl label node $(hostname) cloud-pipeline/preempted=true --kubeconfig='/etc/kubernetes/kubelet.conf'"

    crontab -l | { cat ; echo -e "* * * * * $CHECK_AZURE_EVENTS_COMMAND \n* * * * * sleep 20 && $CHECK_AZURE_EVENTS_COMMAND \n* * * * * sleep 40 && $CHECK_AZURE_EVENTS_COMMAND" ; } | crontab -

elif [[ $gcloud_header == *"Google"* ]]; then
    _gcp_h='-H Metadata-Flavor:Google -s'
    _CLOUD_INSTANCE_AZ=$(curl $_gcp_h http://169.254.169.254/computeMetadata/v1/instance/zone | grep zones | cut -d/ -f4)
    _CLOUD_REGION=${_CLOUD_INSTANCE_AZ}
    _CLOUD_INSTANCE_ID=$(curl $_gcp_h http://169.254.169.254/computeMetadata/v1/instance/name)
    _CLOUD_INSTANCE_TYPE=$(curl $_gcp_h http://169.254.169.254/computeMetadata/v1/instance/machine-type | grep machineTypes | cut -d/ -f4)
    _CLOUD_INSTANCE_IMAGE_ID=$(curl $_gcp_h http://169.254.169.254/computeMetadata/v1/instance/image | cut -d/ -f5)
    _CI_IP=$(curl $_gcp_h http://169.254.169.254/computeMetadata/v1/instance/network-interfaces/0/ip)
    _CLOUD_PROVIDER=GCP
    _KUBE_NODE_NAME="$_CLOUD_INSTANCE_ID"
fi

mtu="@mtu@"
if [ "$mtu" ] && [[ "$mtu" != "@"*"@" ]]; then
  [ "$_CI_IP" ] && _iname=$(ifconfig | grep -B1 "$_CI_IP" | grep -o "^\w*")
  [ -z "$_iname" ] && _iname="eth0"
  ip link set dev $_iname mtu $mtu
fi

@WELL_KNOWN_HOSTS@

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

sed -i "s/--default-ulimit nofile=1024:4096/--default-ulimit nofile=65535:65535/g" /etc/sysconfig/docker

_KUBE_NODE_INSTANCE_LABELS="--node-labels=cloud_provider=$_CLOUD_PROVIDER,cloud_region=$_CLOUD_REGION,cloud_ins_id=$_CLOUD_INSTANCE_ID,cloud_ins_type=$_CLOUD_INSTANCE_TYPE"

if [[ $_CLOUD_INSTANCE_AZ != "" ]]; then
    _KUBE_NODE_INSTANCE_LABELS=$_KUBE_NODE_INSTANCE_LABELS",cloud_az=$_CLOUD_INSTANCE_AZ"
fi

if [[ $_CLOUD_INSTANCE_IMAGE_ID != "" ]]; then
    _KUBE_NODE_INSTANCE_LABELS=$_KUBE_NODE_INSTANCE_LABELS",cloud_image=$_CLOUD_INSTANCE_IMAGE_ID"
fi

_KUBELET_LOG_PATH=/var/log/kubelet
mkdir -p $_KUBELET_LOG_PATH
_KUBE_LOG_ARGS="--logtostderr=false --log-dir=$_KUBELET_LOG_PATH"

_KUBE_NODE_NAME="${_KUBE_NODE_NAME:-$(hostname)}"
_KUBE_NODE_NAME_ARGS="--hostname-override $_KUBE_NODE_NAME"

# FIXME: use the .NodeRegistration.KubeletExtraArgs object in the configuration files
_KUBELET_INITD_DROPIN_PATH="/etc/sysconfig/kubelet"
rm -f $_KUBELET_INITD_DROPIN_PATH

## FIXME: shall be moved to the preferences
_KUBE_RESERVED_RATIO=5
_KUBE_RESERVED_MIN_MB=500
_KUBE_RESERVED_MAX_MB=2000
_KUBE_NODE_MEM_TOTAL_MB=$(awk '/MemTotal/ { printf "%.0f", $2/1024 }' /proc/meminfo)
_KUBE_NODE_MEM_RESERVED_MB=$(( $_KUBE_NODE_MEM_TOTAL_MB * $_KUBE_RESERVED_RATIO / 100 / 2 ))
_KUBE_NODE_MEM_RESERVED_MB=$(( $_KUBE_NODE_MEM_RESERVED_MB > $_KUBE_RESERVED_MAX_MB ? $_KUBE_RESERVED_MAX_MB : $_KUBE_NODE_MEM_RESERVED_MB ))
_KUBE_NODE_MEM_RESERVED_MB=$(( $_KUBE_NODE_MEM_RESERVED_MB < $_KUBE_RESERVED_MIN_MB ? $_KUBE_RESERVED_MIN_MB : $_KUBE_NODE_MEM_RESERVED_MB ))
_KUBE_RESERVED_ARGS="--kube-reserved cpu=300m,memory=${_KUBE_NODE_MEM_RESERVED_MB}Mi,ephemeral-storage=1Gi"
_KUBE_SYS_RESERVED_ARGS="--system-reserved cpu=300m,memory=${_KUBE_NODE_MEM_RESERVED_MB}Mi,ephemeral-storage=1Gi"
_KUBE_EVICTION_ARGS="--eviction-hard= --eviction-soft= --eviction-soft-grace-period= --pod-max-pids=-1"
_KUBE_FAIL_ON_SWAP_ARGS="--fail-swap-on=false"

echo "KUBELET_EXTRA_ARGS=$_KUBE_NODE_INSTANCE_LABELS $_KUBE_LOG_ARGS $_KUBE_NODE_NAME_ARGS $_KUBE_RESERVED_ARGS $_KUBE_SYS_RESERVED_ARGS $_KUBE_EVICTION_ARGS $_KUBE_FAIL_ON_SWAP_ARGS" >> $_KUBELET_INITD_DROPIN_PATH
chmod +x $_KUBELET_INITD_DROPIN_PATH

systemctl enable docker
systemctl enable kubelet
systemctl start docker

for _KUBE_SYSTEM_POD_FILE in $_DOCKER_SYS_IMGS/*.tar; do
  docker load -i $_KUBE_SYSTEM_POD_FILE
done
rm -rf $_DOCKER_SYS_IMGS

kubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --discovery-token-unsafe-skip-ca-verification --node-name $_KUBE_NODE_NAME --ignore-preflight-errors all
systemctl start kubelet

update_nameserver "$nameserver_post_val" "infinity"

if [[ $FS_TYPE == "btrfs" ]]; then
  _API_URL="@API_URL@"
  _API_TOKEN="@API_TOKEN@"
  _MOUNT_POINT="/ebs"
  _FS_AUTOSCALE_PRESENT=0
  _CURRENT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
  if [ -f "$_CURRENT_DIR/fsautoscale" ]; then
    cp "$_CURRENT_DIR/fsautoscale" "/usr/bin/fsautoscale"
    _FS_AUTOSCALE_PRESENT=1
  else
    _FS_AUTO_URL="$(dirname $_API_URL)/fsautoscale.sh"
    echo "Cannot find $_CURRENT_DIR/fsautoscale, downloading from $_FS_AUTO_URL"
    curl -skf "$_FS_AUTO_URL" > /usr/bin/fsautoscale
    if [ $? -ne 0 ]; then
      echo "Error while downloading fsautoscale script"
    else
      _FS_AUTOSCALE_PRESENT=1
    fi
  fi
  if [ $_FS_AUTOSCALE_PRESENT -eq 1 ]; then
    chmod +x /usr/bin/fsautoscale
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
  fi
fi

if check_installed "nvidia-smi"; then
  cat >> /etc/rc.local << EOF
nvidia-persistenced --persistence-mode
nvidia-smi
EOF
fi
cat >> /etc/rc.local << EOF
systemctl start docker
kubeadm join --token @KUBE_TOKEN@ @KUBE_IP@ --discovery-token-unsafe-skip-ca-verification --node-name $_KUBE_NODE_NAME --ignore-preflight-errors all
systemctl start kubelet
EOF

_PRE_PULL_DOCKERS="@PRE_PULL_DOCKERS@"
_API_USER="@API_USER@"
if [[ ! -z "${_PRE_PULL_DOCKERS}" ]] && [[ "${_PRE_PULL_DOCKERS}" != "@"*"@" ]] ; then
  echo "Pre-pulling requested docker images ${_PRE_PULL_DOCKERS}"
  IFS=',' read -ra DOCKERS <<< "$_PRE_PULL_DOCKERS"
  for _DOCKER in "${DOCKERS[@]}"; do
    _REGISTRY="${_DOCKER%%/*}"
    docker login -u "$_API_USER" -p "$_API_TOKEN" "${_REGISTRY}"
    docker pull "$_DOCKER"
  done
fi

touch "$launch_token"
nc -l -k 8888 &
