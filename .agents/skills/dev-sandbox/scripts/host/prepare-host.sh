#!/usr/bin/env bash
#
# Prepares an Amazon Linux 2023 host (kernel 6.1 or later) for Kubernetes 1.15: the packages, the
# legacy iptables, btrfs, Python 2, jq, the kernel settings, SELinux in permissive mode, and cgroup
# v1, which takes effect at the next boot. The dev sandbox's shared host image is a host this
# prepared and then rebooted; sandbox masters and the platform's workers start from that image.
#
# Runs as root on the host. Safe to run again: a step already done is skipped.
#
# Usage: prepare-host.sh [prepare]   prepare the host
#        prepare-host.sh check       fail unless the host runs on cgroup v1, as it does once
#                                    prepared and rebooted
#        prepare-host.sh seal        clear what an image must not copy from this host; the last
#                                    step before the image is taken
set -euo pipefail

DISTRIBUTION_URL=https://cloud-pipeline-oss-builds.s3.amazonaws.com
DISTRIBUTION_URL_US_EAST_1=https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com
BTRFS_VERSION=6.17
BTRFS_URL=$DISTRIBUTION_URL_US_EAST_1/tools/btrfs/$BTRFS_VERSION/btrfs.box.static
MINICONDA2_ARCHIVE=Miniconda2-4.7.12.1-Linux-x86_64.tar.gz
MINICONDA2_URL=$DISTRIBUTION_URL_US_EAST_1/tools/python/2/$MINICONDA2_ARCHIVE
JQ_VERSION=1.6
JQ_URL=$DISTRIBUTION_URL/tools/jq/jq-$JQ_VERSION/jq-linux64

PACKAGES=(nc iproute-tc iptables-legacy)
CLOUD_PIPELINE_DIR=/var/cloud-pipeline
CONDA_BIN=$CLOUD_PIPELINE_DIR/conda/bin
CGROUP_V1_KERNEL_ARGUMENT=systemd.unified_cgroup_hierarchy=0
GRUB_DEFAULTS=/etc/default/grub
GRUB_CONFIG=/boot/grub2/grub.cfg

log() {
  printf '%s\n' "$*"
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

# download <url> <file>: the file appears only once it is complete.
download() {
  wget -q "$1" -O "$2.part"
  mv -f "$2.part" "$2"
}

# Upgrading packages at boot would move the kernel, and the NVIDIA, CUDA and Kubernetes packages,
# away from the versions the platform installs.
disable_package_upgrades() {
  mkdir -p /etc/cloud/cloud.cfg.d
  cat >/etc/cloud/cloud.cfg.d/99_no_upgrades.cfg <<'EOF'
repo_upgrade: none
repo_upgrade_exclude:
 - kernel
 - nvidia*
 - cuda*
 - kubernetes*
EOF
}

install_packages() {
  yum install -y "${PACKAGES[@]}"
  update-alternatives --set iptables /usr/sbin/iptables-legacy
}

install_btrfs() {
  if [[ ! -x /usr/bin/btrfs ]]; then
    log "Installing btrfs $BTRFS_VERSION."
    download "$BTRFS_URL" /usr/bin/btrfs
    chmod +x /usr/bin/btrfs
  fi
  ln -sfn btrfs /usr/bin/mkfs.btrfs
}

install_python2() {
  if [[ ! -x $CONDA_BIN/python2 ]]; then
    log "Installing Python 2 ($MINICONDA2_ARCHIVE)."
    mkdir -p "$CLOUD_PIPELINE_DIR"
    download "$MINICONDA2_URL" "$CLOUD_PIPELINE_DIR/$MINICONDA2_ARCHIVE"
    tar -zxf "$CLOUD_PIPELINE_DIR/$MINICONDA2_ARCHIVE" -C "$CLOUD_PIPELINE_DIR"
    rm -f "$CLOUD_PIPELINE_DIR/$MINICONDA2_ARCHIVE"
  fi
  ln -sfn "$CONDA_BIN/python2" /usr/bin/python2
  ln -sfn "$CONDA_BIN/python2" /usr/bin/python
  ln -sfn "$CONDA_BIN/pip" /usr/bin/pip2
  ln -sfn "$CONDA_BIN/pip" /usr/bin/pip
}

install_jq() {
  if [[ $(/usr/bin/jq --version 2>/dev/null || true) != "jq-$JQ_VERSION" ]]; then
    log "Installing jq $JQ_VERSION."
    download "$JQ_URL" /usr/bin/jq
    chmod +x /usr/bin/jq
  fi
}

# Bridged traffic goes through iptables, and the host forwards packets. The bridge settings exist
# only once br_netfilter is loaded, so it is loaded now and at every boot.
configure_kernel() {
  printf 'br_netfilter\n' >/etc/modules-load.d/k8s.conf
  modprobe br_netfilter
  cat >/etc/sysctl.d/k8s.conf <<'EOF'
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
EOF
  sysctl --system
}

configure_selinux() {
  setenforce 0 || true
  if [[ -f /etc/selinux/config ]]; then
    sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config
  fi
}

# Kubernetes 1.15 runs on cgroup v1 only, and Amazon Linux 2023 boots into cgroup v2.
# https://github.com/ddometita/mmumshad-kubernetes-the-hard-way/issues/8#issuecomment-1397606994
configure_cgroup_v1() {
  if grep -qF "$CGROUP_V1_KERNEL_ARGUMENT" "$GRUB_DEFAULTS"; then
    return 0
  fi
  log "Switching to cgroup v1 at the next boot."
  printf 'GRUB_CMDLINE_LINUX="%s"\n' "$CGROUP_V1_KERNEL_ARGUMENT" >>"$GRUB_DEFAULTS"
  grub2-mkconfig -o "$GRUB_CONFIG"
}

prepare() {
  disable_package_upgrades
  install_packages
  install_btrfs
  install_python2
  install_jq
  configure_kernel
  configure_selinux
  configure_cgroup_v1
  log "The host is prepared. Reboot it to switch to cgroup v1."
}

# cgroup v1 mounts a tmpfs at /sys/fs/cgroup, holding one hierarchy per controller; cgroup v2 mounts
# the unified hierarchy there itself.
check() {
  local fs
  fs=$(stat -fc %T /sys/fs/cgroup)
  [[ $fs == tmpfs ]] ||
    die "the host runs on cgroup v2 (/sys/fs/cgroup is $fs): prepare it, then reboot it"
  log "The host runs on cgroup v1, kernel $(uname -r)."
}

# Every instance started from the image makes a machine ID of its own when the file is empty, and
# cloud-init makes it SSH host keys of its own when there are none.
seal() {
  : >/etc/machine-id
  rm -f /etc/ssh/ssh_host_*
  log "The host is ready to be imaged."
}

main() {
  case ${1:-prepare} in
    prepare) prepare ;;
    check) check ;;
    seal) seal ;;
    *) die "unknown command: $1. Usage: prepare-host.sh [prepare|check|seal]" ;;
  esac
}

main "$@"
