#!/bin/bash

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

set -o xtrace

yum install nc -y

mkdir -p /etc/docker/certs.d/
@DOCKER_CERTS@

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

/etc/eks/bootstrap.sh "@KUBE_CLUSTER_NAME@" --kubelet-extra-args "$_KUBE_NODE_INSTANCE_LABELS $_KUBE_LOG_ARGS $_KUBE_NODE_NAME_ARGS $_KUBE_RESERVED_ARGS $_KUBE_SYS_RESERVED_ARGS $_KUBE_EVICTION_ARGS $_KUBE_FAIL_ON_SWAP_ARGS"

nc -l -k 8888 &
update_nameserver "$nameserver_post_val" "infinity" &
