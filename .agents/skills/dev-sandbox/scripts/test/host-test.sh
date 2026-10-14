#!/usr/bin/env bash
# shellcheck disable=SC2016 # the stubs' bodies, and the lines expected in files, are not for this shell to expand
#
# Checks of the host scripts, prepare-host.sh and install-master.sh: each run twice, as root, with
# every command that would change the host or reach the network (yum, wget, systemctl, docker,
# kubeadm, kubectl, ...) replaced by a stub that records its call. The files they write are real,
# so this runs in a container of its own, which sandbox-test.sh starts as root and throws away.
set -euo pipefail

TEST_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)
HOST_DIR=$(dirname "$TEST_DIR")/host
PREPARE=$HOST_DIR/prepare-host.sh
INSTALL=$HOST_DIR/install-master.sh
# The network plugin manifest up sends to the master, which sandbox-test.sh mounts read-only.
DEPLOY_NETWORK_MANIFEST=${DEPLOY_NETWORK_MANIFEST:?is set by sandbox-test.sh}

WORK=$(mktemp -d)
STUBS=$WORK/stubs
export FIXTURES=$WORK/fixtures STUB_STATE=$WORK/state STUB_CALLS=$WORK/calls
OUTPUT=$WORK/output
TOKEN=abcdef.0123456789abcdef
FAILURES=0
STATUS=

[[ $(id -u) == 0 ]] || { echo "host-test.sh runs as root, in a container of its own" >&2; exit 1; }

check() {   # check <description> <actual> <expected>
  if [[ $2 == "$3" ]]; then
    printf 'ok      %s\n' "$1"
  else
    printf 'FAILED  %s\n        expected: %s\n        actual:   %s\n' "$1" "$3" "$2"
    sed 's/^/        | /' "$OUTPUT"
    FAILURES=$((FAILURES + 1))
  fi
}

# run <script> [args...]: runs a host script with the stubs first on PATH; sets STATUS, and keeps
# what it printed in OUTPUT. Its calls start a fresh record.
run() {
  : >"$STUB_CALLS"
  if PATH=$STUBS:$PATH bash "$@" >"$OUTPUT" 2>&1; then STATUS=0; else STATUS=$?; fi
}

called() {   # called <command>: the recorded calls of it
  grep -E "^$1( |$)" "$STUB_CALLS" || true
}

count() {   # count <text> <file>
  grep -cF -- "$1" "$2" || true
}

stub() {   # stub <name> [<body>]: a command that records its call, then runs the body
  printf '#!/bin/bash\nprintf "%%s\\n" "$(basename "$0") $*" >>"$STUB_CALLS"\n%s\n' "${2:-exit 0}" \
    >"$STUBS/$1"
  chmod +x "$STUBS/$1"
}

# fixture_archive <name> <directory> <file>...: a gzipped tar of the files, under the directory,
# that the wget stub hands out for a URL ending in <name>.
fixture_archive() {
  local name=$1 dir=$2 file
  shift 2
  mkdir -p "$WORK/archive/$dir"
  for file in "$@"; do
    printf '#!/bin/sh\necho "Docker version 20.10.24, build fake"\n' >"$WORK/archive/$dir/$file"
    chmod +x "$WORK/archive/$dir/$file"
  done
  tar -czf "$FIXTURES/$name" -C "$WORK/archive" "$dir"
  rm -rf "${WORK:?}/archive"
}

set_up() {
  local images
  mkdir -p "$STUBS" "$FIXTURES" "$STUB_STATE"
  stub wget 'out= url=
while (($#)); do case $1 in -O) out=$2; shift 2 ;; http*) url=$1; shift ;; *) shift ;; esac; done
if [[ -f $FIXTURES/${url##*/} ]]; then cp "$FIXTURES/${url##*/}" "$out"; else echo "fake $url" >"$out"; fi'
  stub yum '[[ $1 != localinstall ]] || : >"$STUB_STATE/kube-installed"'
  stub rpm '[[ -f $STUB_STATE/kube-installed ]] && printf 1.15.4 || exit 1'
  stub stat 'if [[ "$*" == "-fc %T /sys/fs/cgroup" ]]; then echo "${STUB_CGROUP_FS:-tmpfs}"; else exec /usr/bin/stat "$@"; fi'
  stub sshd 'if [[ $1 == -T ]]; then
  if [[ -z ${STUB_SSHD_IGNORES_DROP_INS:-} ]] && grep -qsx "GatewayPorts clientspecified" /etc/ssh/sshd_config.d/*.conf
  then echo "gatewayports clientspecified"; else echo "gatewayports no"; fi
fi'
  # As kubeadm does, init writes admin.conf before the phases that may still fail.
  stub kubeadm 'case "$1 ${2:-}" in
  "init --config")
    mkdir -p /etc/kubernetes/pki
    printf "kind: Config\n" >/etc/kubernetes/admin.conf
    printf "ca\n" >/etc/kubernetes/pki/ca.crt
    echo "[bootstrap-token] Using token: '"$TOKEN"'"
    [[ -z ${STUB_KUBEADM_INIT_FAILS:-} ]] || { echo "[ERROR] stubbed failure"; exit 1; }
    echo "kubeadm join 10.0.0.7:6443 --token '"$TOKEN"' --discovery-token-ca-cert-hash sha256:00" ;;
  "reset -f")
    [[ -z ${STUB_KUBEADM_RESET_FAILS:-} ]] || { echo "[reset] stubbed failure"; exit 1; }
    rm -rf /etc/kubernetes/admin.conf /etc/kubernetes/pki ;;
  "token list")
    echo "TOKEN TTL EXPIRES USAGES DESCRIPTION EXTRA GROUPS"
    [[ -n ${STUB_NO_TOKEN:-} ]] || echo "'"$TOKEN"'   <forever>   <never>   authentication,signing" ;;
  "token create") echo zyxwvu.9876543210zyxwvu ;;
esac'
  stub kubectl '[[ "$*" != *"get nodes -l"* ]] || { [[ -n ${STUB_NOT_READY:-} ]] && echo False || echo True; }'
  stub openssl 'if [[ $1 == x509 ]]; then echo "fake public key"; else cat >/dev/null; printf fake-der; fi'
  # Every image is there, but those in STUB_MISSING_IMAGES.
  stub docker 'if [[ "$1 $2" == "image inspect" ]]; then
  for image in ${STUB_MISSING_IMAGES:-}; do [[ $3 != "$image" ]] || exit 1; done
fi'
  for command in update-alternatives grub2-mkconfig sysctl setenforce modprobe systemctl; do
    stub "$command"
  done

  printf '#!/bin/sh\necho jq-1.6\n' >"$FIXTURES/jq-linux64"
  fixture_archive Miniconda2-4.7.12.1-Linux-x86_64.tar.gz conda/bin python2 pip
  fixture_archive docker-20.10.24.tgz docker dockerd docker docker-init containerd
  fixture_archive kube-1.15.4.el7.tgz kube kubeadm-1.15.4-0.x86_64.rpm kubelet-1.15.4-0.x86_64.rpm \
    kubectl-1.15.4-0.x86_64.rpm kubernetes-cni-0.7.5-0.x86_64.rpm cri-tools-1.13.0-0.x86_64.rpm
  mapfile -t images < <(sed -n '/^MASTER_IMAGES=(/,/^)/s/^  \(.*\.tar\)$/\1/p' "$INSTALL")
  fixture_archive kube-dockers-1.15.4.tgz kube-dockers "${images[@]}"

  # What Amazon Linux 2023 has and this container may not.
  mkdir -p /etc/sysctl.d /etc/modules-load.d /etc/selinux /usr/lib/systemd/system /etc/default
  printf 'GRUB_CMDLINE_LINUX_DEFAULT="console=tty0 console=ttyS0,115200n8"\nGRUB_TIMEOUT=0\n' \
    >/etc/default/grub
  printf 'SELINUX=enforcing\nSELINUXTYPE=targeted\n' >/etc/selinux/config
  printf '0123456789abcdef0123456789abcdef\n' >/etc/machine-id
  mkdir -p /etc/ssh
  touch /etc/ssh/ssh_host_ed25519_key /etc/ssh/ssh_host_ed25519_key.pub /etc/ssh/ssh_config
  touch /root/.bashrc

  printf 'kind: ClusterConfiguration\n' >"$WORK/kubeadm.yaml"
  # Flannel images as a manifest may name them: plain, quoted, as a list item, more than once.
  cat >"$WORK/network.yaml" <<'EOF'
kind: DaemonSet
spec:
  template:
    spec:
      containers:
        - name: calico-node
          image: calico/node:v3.14.1
        - name: kube-flannel
          image: quay.io/coreos/flannel:v0.11.0
        - image: "docker.io/flannel/flannel-cni-plugin:v1.1.0"
          name: install-cni
---
kind: DaemonSet
spec:
  template:
    spec:
      containers:
        - name: kube-flannel
          image: quay.io/coreos/flannel:v0.11.0
EOF
  printf 'kind: DaemonSet\nspec:\n  containers:\n    - image: calico/node:v3.14.1\n' >"$WORK/no-flannel.yaml"
}

test_prepare_host() {
  run "$PREPARE"
  check "prepare-host.sh succeeds" "$STATUS" 0
  check "it installs the packages, and switches to the legacy iptables" \
    "$(called yum; called update-alternatives)" \
    "$(printf '%s\n' 'yum install -y nc iproute-tc iptables-legacy' \
      'update-alternatives --set iptables /usr/sbin/iptables-legacy')"
  check "it downloads btrfs, Miniconda 2 and jq, from the platform's distribution" \
    "$(called wget | sed -E 's/ -O .*//')" \
    "$(printf 'wget -q %s\n' \
      https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/btrfs/6.17/btrfs.box.static \
      https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/python/2/Miniconda2-4.7.12.1-Linux-x86_64.tar.gz \
      https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64)"
  check "package upgrades at boot leave the kernel, NVIDIA, CUDA and Kubernetes alone" \
    "$(cat /etc/cloud/cloud.cfg.d/99_no_upgrades.cfg)" \
    "$(printf '%s\n' 'repo_upgrade: none' 'repo_upgrade_exclude:' ' - kernel' ' - nvidia*' ' - cuda*' ' - kubernetes*')"
  check "btrfs is installed, with mkfs.btrfs next to it" \
    "$([[ -x /usr/bin/btrfs ]] && echo executable) $(readlink /usr/bin/mkfs.btrfs)" 'executable btrfs'
  check "python, python2, pip and pip2 are Miniconda's" \
    "$(readlink /usr/bin/python) $(readlink /usr/bin/python2) $(readlink /usr/bin/pip) $(readlink /usr/bin/pip2)" \
    '/var/cloud-pipeline/conda/bin/python2 /var/cloud-pipeline/conda/bin/python2 /var/cloud-pipeline/conda/bin/pip /var/cloud-pipeline/conda/bin/pip'
  check "the Miniconda archive is removed once unpacked" \
    "$(find /var/cloud-pipeline -maxdepth 1 -name '*.tar.gz' | wc -l) $([[ -x /var/cloud-pipeline/conda/bin/python2 ]] && echo unpacked)" \
    '0 unpacked'
  check "jq 1.6 is installed" "$(/usr/bin/jq --version)" jq-1.6
  check "bridged traffic goes through iptables, and the host forwards packets" \
    "$(cat /etc/sysctl.d/k8s.conf) $(cat /etc/modules-load.d/k8s.conf) $(called modprobe) $(called sysctl)" \
    "$(printf '%s\n' 'net.bridge.bridge-nf-call-ip6tables = 1' 'net.bridge.bridge-nf-call-iptables = 1' 'net.ipv4.ip_forward = 1') br_netfilter modprobe br_netfilter sysctl --system"
  check "SELinux is permissive, now and after a reboot" \
    "$(called setenforce) $(grep '^SELINUX=' /etc/selinux/config)" 'setenforce 0 SELINUX=permissive'
  check "the next boot is into cgroup v1, through GRUB, keeping the image's other arguments" \
    "$(tail -n 1 /etc/default/grub) $(count console=ttyS0 /etc/default/grub) $(called grub2-mkconfig)" \
    'GRUB_CMDLINE_LINUX="systemd.unified_cgroup_hierarchy=0" 1 grub2-mkconfig -o /boot/grub2/grub.cfg'

  run "$PREPARE" prepare
  check "prepare-host.sh succeeds again" "$STATUS" 0
  check "a second run downloads nothing, and leaves GRUB as it was" \
    "$(called wget)$(called grub2-mkconfig) $(count systemd.unified_cgroup_hierarchy=0 /etc/default/grub)" ' 1'

  STUB_CGROUP_FS=cgroup2fs run "$PREPARE" check
  check "check fails on cgroup v2" "$STATUS $(count 'runs on cgroup v2' "$OUTPUT")" '1 1'
  STUB_CGROUP_FS=tmpfs run "$PREPARE" check
  check "check passes on cgroup v1" "$STATUS $(count 'runs on cgroup v1' "$OUTPUT")" '0 1'

  run "$PREPARE" seal
  check "seal empties the machine ID, and removes the SSH host keys, for each instance to make its own" \
    "$STATUS $(stat -c %s /etc/machine-id) $(find /etc/ssh -maxdepth 1 -name 'ssh_host_*' | wc -l) $([[ -f /etc/ssh/ssh_config ]] && echo kept)" \
    '0 0 0 kept'
  run "$PREPARE" reboot
  check "prepare-host.sh refuses an unknown command" "$STATUS" 1
}

test_install_master_refuses() {
  STUB_CGROUP_FS=cgroup2fs run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "install-master.sh refuses a host on cgroup v2, before installing anything" \
    "$STATUS $(count 'runs on cgroup v2' "$OUTPUT") $(called wget | wc -l)" '1 1 0'
  run "$INSTALL" "$WORK/kubeadm.yaml"
  check "install-master.sh needs both files" "$STATUS" 1
  run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/missing.yaml"
  check "install-master.sh refuses a file that is not there" "$STATUS $(count 'no network manifest' "$OUTPUT")" '1 1'
}

# After test_install_master, which leaves an installed master behind.
test_install_master_network_images() {
  STUB_MISSING_IMAGES=quay.io/coreos/flannel:v0.11.0 run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "install-master.sh fails when Docker lacks a flannel image the network plugin names, and names it" \
    "$STATUS $(count "runs quay.io/coreos/flannel:v0.11.0, which no loaded system image provides" "$OUTPUT")" '1 1'
  check "and applies no network plugin" "$(called 'kubectl apply')" ''

  run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/no-flannel.yaml"
  check "install-master.sh fails when the network plugin names no flannel image to check" \
    "$STATUS $(count 'names no flannel image' "$OUTPUT") $(called 'kubectl apply')" '1 1 '

  run "$INSTALL" "$WORK/kubeadm.yaml" "$DEPLOY_NETWORK_MANIFEST"
  check "deploy/'s network plugin runs quay.io/coreos/flannel:v0.11.0, the image install-master.sh checks for" \
    "$STATUS $(called 'docker image inspect')" '0 docker image inspect quay.io/coreos/flannel:v0.11.0'
}

# After test_install_master, which leaves an installed master behind.
test_install_master_failures_print_no_token() {
  rm -f /var/lib/cp-sandbox/kubeadm-init.done
  STUB_KUBEADM_INIT_FAILS=1 run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "install-master.sh fails when kubeadm init does, and says so" \
    "$STATUS $(count 'kubeadm init failed' "$OUTPUT")" '1 1'
  check "a failed kubeadm init prints its output without the token" \
    "$(count '[ERROR] stubbed failure' "$OUTPUT") $(count "$TOKEN" "$OUTPUT") $(count 'Using token: <token>' "$OUTPUT")" '1 0 1'
  check "a failed kubeadm init leaves admin.conf, as kubeadm's does, and no mark of being done" \
    "$([[ -f /etc/kubernetes/admin.conf ]] && echo admin.conf) $([[ -e /var/lib/cp-sandbox/kubeadm-init.done ]] && echo marked || echo unmarked)" \
    'admin.conf unmarked'

  run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "after a kubeadm init that failed, the next run resets it, then runs it again" \
    "$STATUS $(called kubeadm | head -n 2 | paste -sd '|')" \
    "0 kubeadm reset -f|kubeadm init --config $WORK/kubeadm.yaml"
  check "and marks it done" "$([[ -e /var/lib/cp-sandbox/kubeadm-init.done ]] && echo marked)" marked

  rm -f /var/lib/cp-sandbox/kubeadm-init.done /etc/kubernetes/admin.conf
  STUB_KUBEADM_RESET_FAILS=1 run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "an init that stopped before admin.conf, with certificates written, is reset too; a failed reset shows why" \
    "$STATUS $(called kubeadm | head -n 1) $(count '[reset] stubbed failure' "$OUTPUT") $(count 'kubeadm reset failed' "$OUTPUT")" \
    '1 kubeadm reset -f 1 1'
  run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "the run after it succeeds" "$STATUS" 0

  STUB_SSHD_IGNORES_DROP_INS=1 run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "install-master.sh fails when sshd does not read its drop-in" \
    "$STATUS $(count 'GatewayPorts clientspecified' "$OUTPUT")" '1 1'
}

test_install_master() {
  local base=https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools
  run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "install-master.sh succeeds" "$STATUS" 0
  check "it downloads Docker, the Kubernetes RPMs and the system images, from the platform's distribution" \
    "$(called wget | sed -E 's/^wget .* (https[^ ]+) -O .*/\1/')" \
    "$(printf '%s\n' "$base/docker/distr/linux/static/stable/x86_64/docker-20.10.24.tgz" \
      "$base/kube/1.15.4/rpm/kube-1.15.4.el7.tgz" \
      "$base/kube/1.15.4/docker/"{calico-node-v3.14.1.tar,calico-pod2daemon-flexvol-v3.14.1.tar,calico-cni-v3.14.1.tar,k8s.gcr.io-kube-proxy-v1.15.4.tar,ghcr.io-flannel-io-flannel-v0.26.4.tar,k8s.gcr.io-pause-3.1.tar} \
      https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/kube/1.15.4/docker/kube-dockers-1.15.4.tgz)"
  check "Docker runs from its own directory, with docker-init in /bin" \
    "$([[ -x /var/cloud-pipeline/docker/dockerd && -x /bin/docker-init ]] && echo installed) $(grep '^ExecStart=' /usr/lib/systemd/system/docker.service)" \
    'installed ExecStart=/var/cloud-pipeline/docker/dockerd $OPTIONS $DOCKER_STORAGE_OPTIONS $DOCKER_ADD_RUNTIMES'
  check "Docker uses the systemd cgroup driver and overlay2, and has its tools on root's PATH" \
    "$(tr -d ' \n' </etc/docker/daemon.json) $(count 'PATH=$PATH:/var/cloud-pipeline/docker' /root/.bashrc)" \
    '{"exec-opts":["native.cgroupdriver=systemd"],"storage-driver":"overlay2"} 1'
  check "the Kubernetes RPMs are installed, and the kubelet enabled" \
    "$(called yum) | $(called systemctl | paste -sd ',')" \
    'yum localinstall -y ./kubeadm-1.15.4-0.x86_64.rpm ./kubectl-1.15.4-0.x86_64.rpm ./kubelet-1.15.4-0.x86_64.rpm ./kubernetes-cni-0.7.5-0.x86_64.rpm ./cri-tools-1.13.0-0.x86_64.rpm | systemctl enable kubelet,systemctl daemon-reload,systemctl start docker,systemctl enable docker,systemctl reload sshd'
  check "every system image is loaded, the master's included" \
    "$(called docker | grep -c '^docker load -i ' || true)" 17
  check "once they are, Docker is asked for each flannel image the network plugin names, and then kubeadm init runs" \
    "$(grep -E '^(docker load|docker image inspect|kubeadm init|kubectl apply) ' "$STUB_CALLS" |
      sed -E 's/^docker load .*/docker load/' | uniq | paste -sd '|')" \
    "docker load|docker image inspect docker.io/flannel/flannel-cni-plugin:v1.1.0|docker image inspect quay.io/coreos/flannel:v0.11.0|kubeadm init --config $WORK/kubeadm.yaml|kubectl apply -f $WORK/network.yaml"
  check "install-master.sh says which flannel images it found" \
    "$(count "The network plugin's flannel image is loaded: docker.io/flannel/flannel-cni-plugin:v1.1.0 quay.io/coreos/flannel:v0.11.0." "$OUTPUT")" 1
  check "sshd lets a remote forward bind to the address the client chooses" \
    "$(cat /etc/ssh/sshd_config.d/60-cp-sandbox.conf) $(called sshd | paste -sd ',')" \
    'GatewayPorts clientspecified sshd -t,sshd -T'
  check "kubeadm init runs with the config given, and the network plugin is applied" \
    "$(called kubeadm | head -n 1) | $(called kubectl | head -n 1)" \
    "kubeadm init --config $WORK/kubeadm.yaml | kubectl apply -f $WORK/network.yaml"
  check "root's kubectl uses the admin kubeconfig" "$(cat /root/.kube/config)" 'kind: Config'
  check "the join token is written for root only" \
    "$(cat /etc/kubernetes/cp-sandbox/join-token) $(stat -c %a /etc/kubernetes/cp-sandbox/join-token) $(stat -c %a /etc/kubernetes/cp-sandbox)" \
    "$TOKEN 600 700"
  check "the CA cert hash is the SHA-256 of the CA's public key" \
    "$(cat /etc/kubernetes/cp-sandbox/ca-cert-hash)" "$(printf fake-der | sha256sum | cut -d' ' -f1)"
  check "kubeadm's whole output, token included, is in a log only root can read" \
    "$(stat -c %a /var/log/cp-sandbox/kubeadm-init.log) $(count "$TOKEN" /var/log/cp-sandbox/kubeadm-init.log)" '600 2'
  check "install-master.sh prints no token" \
    "$(count "$TOKEN" "$OUTPUT") $(count '--token <token>' "$OUTPUT")" '0 1'

  run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "install-master.sh succeeds again" "$STATUS" 0
  check "a second run downloads, installs and loads nothing, and runs no kubeadm init" \
    "$(called wget)$(called yum)$(called 'docker load')$(called 'kubeadm init')$(called 'systemctl reload')" ''
  check "a second run applies the network plugin again, and changes no file it appends to" \
    "$(called kubectl | head -n 1) $(count 'PATH=$PATH:/var/cloud-pipeline/docker' /root/.bashrc)" \
    "kubectl apply -f $WORK/network.yaml 1"
  check "a second run prints no token" "$(count "$TOKEN" "$OUTPUT")" 0

  rm -f /etc/kubernetes/cp-sandbox/join-token
  STUB_NO_TOKEN=1 run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "without a token that never expires, install-master.sh makes one" \
    "$STATUS $(cat /etc/kubernetes/cp-sandbox/join-token) $(called kubeadm | grep -c 'token create --ttl 0' || true)" \
    '0 zyxwvu.9876543210zyxwvu 1'

  STUB_NOT_READY=1 NODE_READY_TIMEOUT_SECONDS=0 run "$INSTALL" "$WORK/kubeadm.yaml" "$WORK/network.yaml"
  check "install-master.sh fails when the master node is not Ready in time" \
    "$STATUS $(count 'the master node is not Ready' "$OUTPUT")" '1 1'
}

set_up
test_prepare_host
test_install_master_refuses
test_install_master
test_install_master_network_images
test_install_master_failures_print_no_token
rm -rf "$WORK"
if ((FAILURES)); then
  printf '%s check(s) failed\n' "$FAILURES"
  exit 1
fi
