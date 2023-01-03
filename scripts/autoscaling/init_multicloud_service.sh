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
        if nc -z -w 1 $nameserver 53; then
          echo "nameserver $nameserver can be reached on port 53"
          is_nameserver_reachable="1"
          break
        fi
      done

      if [ "$is_nameserver_reachable" != "1" ]; then
        echo "Elapsed $ping_times retries, but $nameserver can NOT be reached on port 53"
      fi
    fi

    cp /etc/resolv.conf /etc/resolv.conf.backup
    chattr -i /etc/resolv.conf
    sed -i '/nameserver/d' /etc/resolv.conf
    echo "nameserver $nameserver" >>/etc/resolv.conf
    chattr +i /etc/resolv.conf
  fi
}

function check_param() {
    [ "$1" ] && [[ "$1" != "@"*"@" ]]
}

user_data_log="/var/log/user_data.log"
exec >"$user_data_log" 2>&1

export http_proxy="${http_proxy:-"@HTTP_PROXY@"}"
export https_proxy="${https_proxy:-"@HTTPS_PROXY@"}"
export no_proxy="${no_proxy:-"@NO_PROXY@"}"
export KUBE_IP="${KUBE_IP:-"@KUBE_IP@"}"
export KUBE_PORT="${KUBE_PORT:-"@KUBE_PORT@"}"
if ! check_param "$KUBE_PORT"; then
  export KUBE_PORT="6443"
fi
export KUBE_TOKEN="${KUBE_TOKEN:-"@KUBE_TOKEN@"}"
export KUBE_DNS_IP="${KUBE_DNS_IP:-"@KUBE_DNS_IP@"}"
if ! check_param "$KUBE_DNS_IP"; then
  export KUBE_DNS_IP="10.96.0.10"
fi
export KUBE_LABELS="${KUBE_LABELS:-"@KUBE_LABELS@"}"
export KUBE_SYS_IMGS_DISTR="${KUBE_SYS_IMGS_DISTR:-"@KUBE_SYS_IMGS_DISTR@"}"
if ! check_param "$KUBE_SYS_IMGS_DISTR"; then
  export KUBE_SYS_IMGS_DISTR="https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/kube/1.15.4/docker"
fi
export AWS_FS_URL="${AWS_FS_URL:-"@AWS_FS_URL@"}"

_KUBE_SYS_IMGS_DIR="/ebs/docker-system-images"
rm -rf "$_KUBE_SYS_IMGS_DIR"
mkdir -p "$_KUBE_SYS_IMGS_DIR"
_WO="--timeout=10 --waitretry=1 --tries=10"
wget $_WO "${KUBE_SYS_IMGS_DISTR}/calico-node-v3.14.1.tar" -O "$_KUBE_SYS_IMGS_DIR/calico-node-v3.14.1.tar" &&
  wget $_WO "${KUBE_SYS_IMGS_DISTR}/calico-pod2daemon-flexvol-v3.14.1.tar" -O "$_KUBE_SYS_IMGS_DIR/calico-pod2daemon-flexvol-v3.14.1.tar" &&
  wget $_WO "${KUBE_SYS_IMGS_DISTR}/calico-cni-v3.14.1.tar" -O "$_KUBE_SYS_IMGS_DIR/calico-cni-v3.14.1.tar" &&
  wget $_WO "${KUBE_SYS_IMGS_DISTR}/k8s.gcr.io-kube-proxy-v1.15.4.tar" -O "$_KUBE_SYS_IMGS_DIR/k8s.gcr.io-kube-proxy-v1.15.4.tar" &&
  wget $_WO "${KUBE_SYS_IMGS_DISTR}/quay.io-coreos-flannel-v0.11.0.tar" -O "$_KUBE_SYS_IMGS_DIR/quay.io-coreos-flannel-v0.11.0.tar" &&
  wget $_WO "${KUBE_SYS_IMGS_DISTR}/k8s.gcr.io-pause-3.1.tar" -O "$_KUBE_SYS_IMGS_DIR/k8s.gcr.io-pause-3.1.tar"
if [ $? -ne 0 ]; then
  _KUBE_SYS_IMGS_DIR="/opt/docker-system-images"
fi

mkdir -p /etc/docker
cat <<EOT >/etc/docker/daemon.json
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "storage-driver": "overlay2"
}
EOT

mkdir -p /etc/systemd/system/docker.service.d
cat >/etc/systemd/system/docker.service.d/http-proxy.conf <<EOF
  [Service]
  Environment="http_proxy=$http_proxy" "https_proxy=$https_proxy" "no_proxy=$no_proxy"
EOF

if check_param "$KUBE_LABELS"; then
  echo "KUBELET_EXTRA_ARGS=--node-labels $KUBE_LABELS" >>/etc/sysconfig/kubelet
fi
exe
systemctl daemon-reload
systemctl enable docker
systemctl enable kubelet
systemctl start docker

cloud="$(curl --head -s "http://169.254.169.254/latest/dynamic/instance-identity/document" | grep Server | cut -f2 -d:)"
gcloud_header="$(curl --head -s "http://169.254.169.254/latest/dynamic/instance-identity/document" | grep Metadata-Flavor | cut -f2 -d:)"
if [[ $cloud == *"EC2"* ]]; then
  _CLOUD_INSTANCE_ID="$(curl -s "http://169.254.169.254/latest/dynamic/instance-identity/document" | grep instanceId | cut -d\" -f4)"
  _KUBE_NODE_NAME="$_CLOUD_INSTANCE_ID"
elif [[ $cloud == *"Microsoft"* ]]; then
  _CLOUD_INSTANCE_ID="$(curl -H Metadata:true -s "http://169.254.169.254/metadata/instance/compute/name?api-version=2018-10-01&format=text")"
  _KUBE_NODE_NAME="$(echo "$_CLOUD_INSTANCE_ID" | grep -xE "[a-zA-Z0-9\-]{1,256}" &>/dev/null && echo "$_CLOUD_INSTANCE_ID" || hostname)"
elif [[ $gcloud_header == *"Google"* ]]; then
  _CLOUD_INSTANCE_ID="$(curl -H "Metadata-Flavor:Google" "http://169.254.169.254/computeMetadata/v1/instance/name")"
  _KUBE_NODE_NAME="$_CLOUD_INSTANCE_ID"
fi

for _KUBE_SYS_IMG_FILE in "$_KUBE_SYS_IMGS_DIR"/*.tar; do
  docker load -i "$_KUBE_SYS_IMG_FILE"
done
rm -rf "$_KUBE_SYS_IMGS_DIR"

kubeadm join --token "$KUBE_TOKEN" "$KUBE_IP:$KUBE_PORT" --discovery-token-unsafe-skip-ca-verification --ignore-preflight-errors all --node-name "$_KUBE_NODE_NAME"
systemctl start kubelet

yum install -y nc
update_nameserver "$KUBE_DNS_IP" "infinity"

if [[ $cloud == *"EC2"* ]]; then
  amazon-linux-extras install -y lustre2.10
  yum install -y lustre-client --disablerepo=kubernetes
  mkdir -p /opt
  mount -t lustre -o noatime,flock "$AWS_FS_URL" /opt
  echo "$AWS_FS_URL /opt lustre defaults,noatime,flock,_netdev 0 0" >>/etc/fstab
elif [[ $cloud == *"Microsoft"* ]]; then
  echo "WARNING: Azure shared file system mounting is not yet supported."
  # todo: Implement
elif [[ $gcloud_header == *"Google"* ]]; then
  echo "WARNING: Google Cloud shared file system mounting is not yet supported."
  # todo: Implement
fi
