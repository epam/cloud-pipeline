#!/usr/bin/env bash
#
# Installs a Kubernetes 1.15.4 master on a host that prepare-host.sh prepared and that has rebooted
# into cgroup v1 since: Docker 20.10.24, the Kubernetes RPMs and the system images, which must hold
# the network plugin's flannel image, then kubeadm init and the network plugin. Also lets an SSH
# client choose the address a remote forward binds to, so that a tunnel can bind a port on the
# master's private address.
#
# Runs as root on the host. Safe to run again: a step already done is skipped.
#
# Usage: install-master.sh <kubeadm-config> <network-manifest>
#   Both files are rendered already, with no placeholder left in them.
#
# Prints nothing secret. kubeadm's own output, which holds the join token, goes to a log only root
# can read, and what is printed of it has the token blanked out. The join token is written to
# JOIN_TOKEN_FILE, readable by root only, and the hash of the cluster CA's public key to
# CA_CERT_HASH_FILE.
set -euo pipefail

DOCKER_VERSION=20.10.24
KUBE_VERSION=1.15.4
DISTRIBUTION_URL=https://cloud-pipeline-oss-builds.s3.amazonaws.com
DISTRIBUTION_URL_US_EAST_1=https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com
DOCKER_URL=$DISTRIBUTION_URL/tools/docker/distr/linux/static/stable/x86_64/docker-$DOCKER_VERSION.tgz
KUBE_RPMS_URL=$DISTRIBUTION_URL/tools/kube/$KUBE_VERSION/rpm/kube-$KUBE_VERSION.el7.tgz
SYSTEM_IMAGES_URL=$DISTRIBUTION_URL/tools/kube/$KUBE_VERSION/docker
MASTER_IMAGES_URL=$DISTRIBUTION_URL_US_EAST_1/tools/kube/$KUBE_VERSION/docker/kube-dockers-$KUBE_VERSION.tgz

# Every node's system images, and then the ones only the master runs.
SYSTEM_IMAGES=(
  calico-node-v3.14.1.tar
  calico-pod2daemon-flexvol-v3.14.1.tar
  calico-cni-v3.14.1.tar
  k8s.gcr.io-kube-proxy-v1.15.4.tar
  ghcr.io-flannel-io-flannel-v0.26.4.tar
  k8s.gcr.io-pause-3.1.tar
)
MASTER_IMAGES=(
  k8s.gcr.io-k8s-dns-dnsmasq-nanny-1.14.13.tar
  k8s.gcr.io-k8s-dns-kube-dns-1.14.13.tar
  k8s.gcr.io-k8s-dns-sidecar-1.14.13.tar
  gcr.io-google-containers-k8s-dns-sidecar-1.15.11.tar
  calico-kube-controllers-v3.14.1.tar
  k8s.gcr.io-cluster-proportional-autoscaler-amd64-1.1.1.tar
  k8s.gcr.io-etcd-3.3.10.tar
  k8s.gcr.io-heapster-amd64-v1.5.4.tar
  k8s.gcr.io-kube-apiserver-v1.15.4.tar
  k8s.gcr.io-kube-controller-manager-v1.15.4.tar
  k8s.gcr.io-kube-scheduler-v1.15.4.tar
)

DOCKER_HOME=/var/cloud-pipeline/docker
SYSTEM_IMAGES_DIR=/root/docker-system-images
MASTER_IMAGES_DIR=/root/docker-system-images-master
STATE_DIR=/var/lib/cp-sandbox
LOG_DIR=/var/log/cp-sandbox
ADMIN_CONF=/etc/kubernetes/admin.conf
CA_CERT=/etc/kubernetes/pki/ca.crt
CREDENTIALS_DIR=/etc/kubernetes/cp-sandbox
JOIN_TOKEN_FILE=$CREDENTIALS_DIR/join-token
CA_CERT_HASH_FILE=$CREDENTIALS_DIR/ca-cert-hash
SSHD_DROP_IN=/etc/ssh/sshd_config.d/60-cp-sandbox.conf
SSHD_GATEWAY_PORTS='GatewayPorts clientspecified'
NODE_READY_TIMEOUT_SECONDS=${NODE_READY_TIMEOUT_SECONDS:-600}
POLL_SECONDS=${POLL_SECONDS:-10}

# A kubeadm bootstrap token, as it appears in kubeadm's output.
TOKEN_PATTERN='[a-z0-9]{6}\.[a-z0-9]{16}'
WGET_OPTIONS=(--timeout=10 --waitretry=1 --tries=10 -q)

export PATH=$PATH:$DOCKER_HOME
export KUBECONFIG=$ADMIN_CONF

log() {
  printf '%s\n' "$*"
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

# download <url> <file>: the file appears only once it is complete.
download() {
  wget "${WGET_OPTIONS[@]}" "$1" -O "$2.part"
  mv -f "$2.part" "$2"
}

redact() {
  sed -E "s/$TOKEN_PATTERN/<token>/g"
}

check_prepared() {
  local fs
  fs=$(stat -fc %T /sys/fs/cgroup)
  [[ $fs == tmpfs ]] || die "the host runs on cgroup v2 (/sys/fs/cgroup is $fs), which Kubernetes \
$KUBE_VERSION cannot use. Start the master from the sandbox's host image."
}

install_docker() {
  local archive
  if [[ ! -x $DOCKER_HOME/dockerd ]] ||
    [[ $("$DOCKER_HOME/dockerd" --version) != *"version $DOCKER_VERSION,"* ]]; then
    log "Installing Docker $DOCKER_VERSION."
    archive=$(mktemp)
    download "$DOCKER_URL" "$archive"
    mkdir -p "$DOCKER_HOME"
    tar --extract --file "$archive" --strip-components 1 --directory "$DOCKER_HOME"
    rm -f "$archive"
  fi

  rm -f /bin/docker-init
  cp "$DOCKER_HOME/docker-init" /bin/docker-init

  cat >/usr/lib/systemd/system/docker.service <<EOF
[Unit]
Description=Docker Application Container Engine
Documentation=https://docs.docker.com
After=network-online.target docker.socket firewalld.service containerd.service
Wants=network-online.target

[Service]
Type=notify
EnvironmentFile=-/etc/sysconfig/docker
EnvironmentFile=-/etc/sysconfig/docker-storage
EnvironmentFile=-/run/docker/runtimes.env
ExecStartPre=/bin/mkdir -p /run/docker
ExecStart=$DOCKER_HOME/dockerd \$OPTIONS \$DOCKER_STORAGE_OPTIONS \$DOCKER_ADD_RUNTIMES
ExecReload=/bin/kill -s HUP \$MAINPID
LimitNOFILE=infinity
LimitNPROC=infinity
LimitCORE=infinity
TimeoutStartSec=0
Delegate=yes
KillMode=process
RestartSec=2
Restart=always
StartLimitBurst=3
StartLimitInterval=60s

[Install]
WantedBy=multi-user.target
EOF

  mkdir -p /etc/sysconfig /run/docker /etc/docker
  cat >/etc/sysconfig/docker <<EOF
DAEMON_MAXFILES=1048576
OPTIONS="--default-ulimit nofile=65536:65536"
DAEMON_PIDFILE_TIMEOUT=10
PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:$DOCKER_HOME"
EOF
  printf 'DOCKER_STORAGE_OPTIONS=\n' >/etc/sysconfig/docker-storage
  printf 'STORAGE_DRIVER=\n' >/etc/sysconfig/docker-storage-setup
  printf 'DOCKER_ADD_RUNTIMES=""\n' >/run/docker/runtimes.env
  cat >/etc/docker/daemon.json <<'EOF'
{
  "exec-opts": ["native.cgroupdriver=systemd"],
  "storage-driver": "overlay2"
}
EOF

  if ! grep -qxF "PATH=\$PATH:$DOCKER_HOME" /root/.bashrc 2>/dev/null; then
    # shellcheck disable=SC2016 # $PATH is for .bashrc to expand
    printf 'PATH=$PATH:%s\n' "$DOCKER_HOME" >>/root/.bashrc
  fi
}

kube_installed() {
  local package
  for package in kubeadm kubelet kubectl; do
    [[ $(rpm -q --queryformat '%{VERSION}' "$package" 2>/dev/null || true) == "$KUBE_VERSION" ]] ||
      return 1
  done
}

install_kube() {
  local work
  if ! kube_installed; then
    log "Installing the Kubernetes $KUBE_VERSION RPMs."
    work=$(mktemp -d)
    download "$KUBE_RPMS_URL" "$work/kube.tgz"
    tar -xf "$work/kube.tgz" -C "$work"
    (cd "$work/kube" && yum localinstall -y ./*kube*.rpm ./*cri-tools*.rpm)
    rm -rf "$work"
  fi
  systemctl enable kubelet
}

start_docker() {
  systemctl daemon-reload
  systemctl start docker
  systemctl enable docker
}

load_system_images() {
  local image marker=$STATE_DIR/system-images.loaded
  if [[ -f $marker ]]; then
    log "The system images are loaded already."
    return 0
  fi
  log "Loading the system images."
  mkdir -p "$SYSTEM_IMAGES_DIR" "$MASTER_IMAGES_DIR"
  for image in "${SYSTEM_IMAGES[@]}"; do
    [[ -s $SYSTEM_IMAGES_DIR/$image ]] || download "$SYSTEM_IMAGES_URL/$image" "$SYSTEM_IMAGES_DIR/$image"
    docker load -i "$SYSTEM_IMAGES_DIR/$image"
  done

  download "$MASTER_IMAGES_URL" "$MASTER_IMAGES_DIR/kube-dockers-$KUBE_VERSION.tgz"
  tar -zxf "$MASTER_IMAGES_DIR/kube-dockers-$KUBE_VERSION.tgz" -C "$MASTER_IMAGES_DIR"
  rm -f "$MASTER_IMAGES_DIR/kube-dockers-$KUBE_VERSION.tgz"
  for image in "${MASTER_IMAGES[@]}"; do
    docker load -i "$MASTER_IMAGES_DIR/kube-dockers/$image"
  done
  : >"$marker"
}

# manifest_flannel_images <manifest>: the flannel images the manifest names, once each.
manifest_flannel_images() {
  local line quote="[\"']" name="[^[:space:]\"']"
  local pattern="^[[:space:]-]*image:[[:space:]]*$quote?($name*flannel$name*)"
  while IFS= read -r line || [[ -n $line ]]; do
    if [[ $line =~ $pattern ]]; then
      printf '%s\n' "${BASH_REMATCH[1]}"
    fi
  done <"$1" | sort -u
}

# The network plugin runs the flannel image loaded with the system images, under the name the
# manifest gives it. The manifest sets no pull policy, so the kubelet uses an image that is present,
# and would pull one that is not, from a registry the master is not meant to depend on.
check_network_images() {
  local manifest=$1 image
  local -a images missing=()
  mapfile -t images < <(manifest_flannel_images "$manifest")
  ((${#images[@]})) || die "$manifest names no flannel image, so the loaded system images cannot be \
checked against it"
  for image in "${images[@]}"; do
    docker image inspect "$image" >/dev/null 2>&1 || missing+=("$image")
  done
  ((${#missing[@]} == 0)) || die "the network plugin in $manifest runs ${missing[*]}, which no loaded \
system image provides, so the master would pull it. The flannel archive in SYSTEM_IMAGES must load \
under the name the manifest gives."
  log "The network plugin's flannel image is loaded: ${images[*]}."
}

# Remote forwards bind to the loopback address unless the server lets the client choose.
configure_sshd() {
  if [[ ! -f $SSHD_DROP_IN || $(<"$SSHD_DROP_IN") != "$SSHD_GATEWAY_PORTS" ]]; then
    mkdir -p "$(dirname "$SSHD_DROP_IN")"
    printf '%s\n' "$SSHD_GATEWAY_PORTS" >"$SSHD_DROP_IN"
    sshd -t
    systemctl reload sshd
  fi
  sshd -T | grep -qix "$SSHD_GATEWAY_PORTS" ||
    die "sshd does not read $SSHD_DROP_IN: '$SSHD_GATEWAY_PORTS' is not in effect"
}

# kubeadm starts the kubelet and writes the certificates and admin.conf half-way through init, so
# only the marker says that init finished. An init that stopped before the end is undone before
# the next one.
init_master() {
  local config=$1 output=$LOG_DIR/kubeadm-init.log marker=$STATE_DIR/kubeadm-init.done
  local reset_log=$LOG_DIR/kubeadm-reset.log
  if [[ -f $marker ]]; then
    log "kubeadm init has run already."
  else
    if [[ -e $ADMIN_CONF || -e /etc/kubernetes/pki || -e /var/lib/kubelet/config.yaml ]]; then
      log "An earlier kubeadm init did not finish. Undoing it with kubeadm reset, logged in \
$reset_log."
      if ! kubeadm reset -f >>"$reset_log" 2>&1; then
        tail -n 20 "$reset_log"
        die "kubeadm reset failed"
      fi
    fi
    log "Running kubeadm init. Its whole output is in $output."
    if ! (umask 077 && kubeadm init --config "$config" >"$output" 2>&1); then
      tail -n 30 "$output" | redact
      die "kubeadm init failed"
    fi
    redact <"$output"
    : >"$marker"
  fi
  install -d -m 0700 /root/.kube
  cp "$ADMIN_CONF" /root/.kube/config
}

apply_network() {
  log "Applying the network plugin."
  kubectl apply -f "$1"
}

master_ready() {
  [[ $(kubectl get nodes -l node-role.kubernetes.io/master \
    -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' 2>/dev/null || true) == True ]]
}

wait_master_ready() {
  local deadline=$((SECONDS + NODE_READY_TIMEOUT_SECONDS))
  log "Waiting for the master node to be Ready, up to $((NODE_READY_TIMEOUT_SECONDS / 60)) minutes."
  until master_ready; do
    if ((SECONDS >= deadline)); then
      kubectl get nodes -o wide || true
      kubectl -n kube-system get pods -o wide || true
      die "the master node is not Ready"
    fi
    sleep "$POLL_SECONDS"
  done
  kubectl get nodes -o wide
}

# The token the platform's nodes join with, which never expires: the one kubeadm init made from the
# config's bootstrapTokens, or a new one.
write_join_credentials() {
  local token hash
  token=$(kubeadm token list 2>/dev/null | awk 'NR > 1 && $2 == "<forever>" { print $1; exit }')
  if [[ -z $token ]]; then
    token=$(kubeadm token create --ttl 0)
  fi
  [[ $token =~ ^$TOKEN_PATTERN$ ]] || die "kubeadm gave no join token"
  install -d -m 0700 "$CREDENTIALS_DIR"
  (umask 077 && printf '%s\n' "$token" >"$JOIN_TOKEN_FILE")

  hash=$(openssl x509 -in "$CA_CERT" -noout -pubkey |
    openssl rsa -pubin -outform DER 2>/dev/null | sha256sum | cut -d' ' -f1)
  [[ $hash =~ ^[0-9a-f]{64}$ ]] || die "could not hash the public key of $CA_CERT"
  printf '%s\n' "$hash" >"$CA_CERT_HASH_FILE"
  log "Join token written to $JOIN_TOKEN_FILE. CA cert hash: $hash"
}

main() {
  (($# == 2)) || die "usage: install-master.sh <kubeadm-config> <network-manifest>"
  [[ -f $1 ]] || die "no kubeadm config at $1"
  [[ -f $2 ]] || die "no network manifest at $2"

  check_prepared
  mkdir -p "$STATE_DIR"
  install -d -m 0700 "$LOG_DIR"
  install_docker
  install_kube
  start_docker
  load_system_images
  check_network_images "$2"
  configure_sshd
  init_master "$1"
  apply_network "$2"
  wait_master_ready
  write_join_credentials
  log "The Kubernetes master is installed."
}

main "$@"
