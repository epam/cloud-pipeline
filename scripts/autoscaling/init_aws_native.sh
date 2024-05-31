#!/bin/bash

launch_token="/etc/user_data_launched"
if [[ -f "$launch_token" ]]; then exit 0; fi

user_data_log="/var/log/user_data.log"
exec > "$user_data_log" 2>&1

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

function wait_device_uuid {
    local device_name="$1"
    local attempts="$2"

    export DEVICE_UUID=""
    echo "Waiting for device uuid $device_name..."
    for i in $(seq 1 "$attempts"); do
        export DEVICE_UUID="$(lsblk -sdrpn -o NAME,UUID | awk -F'[ ]' '$1 ~ device_name { print $2 }' device_name="$device_name" | head -n 1)"
        if [ "$DEVICE_UUID" ]; then
            echo "Device uuid is ready $device_name ($DEVICE_UUID)"
            return 0
        fi
        echo "Waiting for device uuid..."
        sleep 1
    done

    echo "Device uuid is NOT ready after $attempts seconds"
    return 1
}

function wait_device_part {
    local device_prefix="$1"
    local attempts="$2"

    export DEVICE_NAME=""
    echo "Waiting for device part $device_prefix..."
    for i in $(seq 1 "$attempts"); do
        export DEVICE_NAME="$(lsblk -sdrpn -o NAME,TYPE,MOUNTPOINT | awk -F'[ ]' '$1 ~ device_prefix && $2 == "part" && $3 == "" { print $1 }' device_prefix="$device_prefix" | head -n 1)"
        if [ "$DEVICE_NAME" ]; then
            echo "Device part is ready $DEVICE_NAME"
            return 0
        fi
        echo "Waiting for device part..."
        sleep 1
    done

    echo "Device part is NOT ready after $attempts seconds"
    return 1
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
        wait_device_uuid "$swap_drive_name" 10
        echo "UUID=$DEVICE_UUID none swap sw 0 0" >> /etc/fstab
    fi
}

yum install btrfs-progs nc bc -y

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
      wait_device_part "$DRIVE_NAME" 10
      DRIVE_NAME="$DEVICE_NAME"
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

#######################################################
# Configure containerd data-root and state locations
#######################################################
mkdir -p $MOUNT_POINT/containerd/state
mkdir -p $MOUNT_POINT/containerd/data-root
_CONTAINERD_CONFIG_PATH=$MOUNT_POINT/containerd/config.toml

cat > $_CONTAINERD_CONFIG_PATH <<EOF
version = 2
root = "$MOUNT_POINT/containerd/data-root"
state = "$MOUNT_POINT/containerd/state"
imports = ["/etc/containerd/config.d/*.toml"]

[grpc]
address = "/run/containerd/containerd.sock"

[plugins."io.containerd.grpc.v1.cri".containerd]
default_runtime_name = "runc"
discard_unpacked_layers = true

[plugins."io.containerd.grpc.v1.cri"]
sandbox_image = "registry.k8s.io/pause:3.5"

[plugins."io.containerd.grpc.v1.cri".registry]
config_path = "/etc/containerd/certs.d:/etc/docker/certs.d"

[plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc]
runtime_type = "io.containerd.runc.v2"

[plugins."io.containerd.grpc.v1.cri".containerd.runtimes.runc.options]
SystemdCgroup = true

[plugins."io.containerd.grpc.v1.cri".cni]
bin_dir = "/opt/cni/bin"
conf_dir = "/etc/cni/net.d"

EOF

set -o xtrace

mkdir -p /etc/docker/certs.d/
@DOCKER_CERTS@

cloud=$(curl --head -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep Server | cut -f2 -d:)

_CLOUD_REGION=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep region | cut -d\" -f4)
_CLOUD_INSTANCE_AZ=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep availabilityZone | cut -d\" -f4)
_CLOUD_INSTANCE_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep instanceId | cut -d\" -f4)
_CLOUD_INSTANCE_HOSTNAME=$(curl -s http://169.254.169.254/latest/meta-data/local-hostname)
_CLOUD_INSTANCE_TYPE=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep instanceType | cut -d\" -f4)
_CLOUD_INSTANCE_IMAGE_ID=$(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | grep imageId | cut -d\" -f4)
_CI_IP=$(curl -s http://169.254.169.254/latest/meta-data/local-ipv4)
_CLOUD_PROVIDER=AWS
_KUBE_NODE_NAME="$_CLOUD_INSTANCE_HOSTNAME"

useradd pipeline
cp -r /home/ec2-user/.ssh /home/pipeline/.ssh
chown -R pipeline. /home/pipeline/.ssh
chmod 700 .ssh
usermod -a -G wheel pipeline
echo 'pipeline ALL=(ALL) NOPASSWD:ALL' >> /etc/sudoers.d/cloud-init


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


_KUBE_NODE_INSTANCE_LABELS="--node-labels=cloud_provider=$_CLOUD_PROVIDER,cloud_region=$_CLOUD_REGION,cloud_ins_id=$_CLOUD_INSTANCE_ID,cloud_ins_type=$_CLOUD_INSTANCE_TYPE"

if [[ $_CLOUD_INSTANCE_AZ != "" ]]; then
    _KUBE_NODE_INSTANCE_LABELS=$_KUBE_NODE_INSTANCE_LABELS",cloud_az=$_CLOUD_INSTANCE_AZ"
fi

if [[ $_CLOUD_INSTANCE_IMAGE_ID != "" ]]; then
    _KUBE_NODE_INSTANCE_LABELS=$_KUBE_NODE_INSTANCE_LABELS",cloud_image=$_CLOUD_INSTANCE_IMAGE_ID"
fi

KUBE_RESERVED_CPU="250m"
KUBE_RESERVED_DISK="1Gi"
KUBE_RESERVED_MEM="@KUBE_RESERVED_MEM@"
if [ ! "$KUBE_RESERVED_MEM" ] || [[ "$KUBE_RESERVED_MEM" == "@"*"@" ]]; then
    KUBE_RESERVED_MEM="250Mi"
fi

SYSTEM_RESERVED_CPU="250m"
SYSTEM_RESERVED_DISK="1Gi"
SYSTEM_RESERVED_MEM="@SYSTEM_RESERVED_MEM@"
if [ ! "$SYSTEM_RESERVED_MEM" ] || [[ "$SYSTEM_RESERVED_MEM" == "@"*"@" ]]; then
    SYSTEM_RESERVED_MEM="250Mi"
fi

_KUBE_RESERVED_ARGS="--kube-reserved cpu=${KUBE_RESERVED_CPU},memory=${KUBE_RESERVED_MEM},ephemeral-storage=${KUBE_RESERVED_DISK}"
_KUBE_SYS_RESERVED_ARGS="--system-reserved cpu=${SYSTEM_RESERVED_CPU},memory=${SYSTEM_RESERVED_MEM},ephemeral-storage=${SYSTEM_RESERVED_DISK}"
_KUBE_EVICTION_ARGS="--eviction-hard= --eviction-soft= --eviction-soft-grace-period= --pod-max-pids=-1"
_KUBE_FAIL_ON_SWAP_ARGS="--fail-swap-on=false"

/etc/eks/bootstrap.sh "@KUBE_CLUSTER_NAME@" --containerd-config-file "$_CONTAINERD_CONFIG_PATH" --kubelet-extra-args "$_KUBE_NODE_INSTANCE_LABELS $_KUBE_LOG_ARGS $_KUBE_NODE_NAME_ARGS $_KUBE_RESERVED_ARGS $_KUBE_SYS_RESERVED_ARGS $_KUBE_EVICTION_ARGS $_KUBE_FAIL_ON_SWAP_ARGS"

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

touch "$launch_token"
nc -l -k 8888 &
