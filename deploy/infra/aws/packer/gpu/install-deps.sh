#!/bin/bash
# Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set -euo pipefail

# Version of the Nvidia driver and the matching Nvidia rpm packages
NVIDIA_DRIVER_VERSION="${NVIDIA_DRIVER_VERSION:-595.58.03}"

# Custom location, e.g. an internal mirror, which serves a single "$NVIDIA_DRIVER_VERSION.tgz" archive
# with the driver runfile and the Nvidia rpm packages inside.
# If not set - the driver and the rpm packages are downloaded from the public Nvidia locations
NVIDIA_DRIVER_URL_PREFIX="${NVIDIA_DRIVER_URL_PREFIX:-}"
NVIDIA_DRIVER_URL_PREFIX="${NVIDIA_DRIVER_URL_PREFIX%/}"

# Disable automatic packages upgrade, if cloud-init is configured
if [ -d "/etc/cloud/cloud.cfg.d" ]; then

cat <<EOF >/etc/cloud/cloud.cfg.d/99_no_upgrades.cfg
repo_upgrade: none
repo_upgrade_exclude:
 - kernel
 - nvidia*
 - cuda*
 - kubernetes*
EOF

fi

# Install common
yum install -y nc
yum install -y iproute-tc
yum install -y iptables-legacy
update-alternatives --set iptables /usr/sbin/iptables-legacy
# The user data joins more than one unmounted disk into one LVM volume (pvcreate, vgcreate, lvcreate)
yum install -y lvm2

# Each download below goes into a temporary folder and is installed only if it succeeds: a failed download,
# unpack or check stops the build (set -e), so no image is created without the file

# btrfs-progs package is not avaialble in 2023+
# Replaced with:
# - https://btrfs.readthedocs.io/en/latest/INSTALL.html#all-in-one-binary-busybox-style
# - https://github.com/kdave/btrfs-progs
BTRFS_URL="https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/btrfs/6.17/btrfs.box.static"
BTRFS_TMP_DIR="$(mktemp -d)"
wget -q "$BTRFS_URL" -O "$BTRFS_TMP_DIR/btrfs" || { rm -rf "$BTRFS_TMP_DIR"; echo "[ERROR] Cannot download btrfs from $BTRFS_URL"; exit 1; }
chmod +x "$BTRFS_TMP_DIR/btrfs"
"$BTRFS_TMP_DIR/btrfs" --version
install -m 0755 "$BTRFS_TMP_DIR/btrfs" /usr/bin/btrfs
rm -rf "$BTRFS_TMP_DIR"
ln -s btrfs /usr/bin/mkfs.btrfs
/usr/bin/mkfs.btrfs --version

# Python 2 (Miniconda2) in /opt/conda: the commit and the pause of a run use python2 on the node,
# if there is no /usr/local/python3.12/bin/python3.12
CP_PYTHON2_DISTRO_URL="https://cloud-pipeline-oss-builds.s3.us-east-1.amazonaws.com/tools/python/2/Miniconda2-4.7.12.1-Linux-x86_64.tar.gz"
PYTHON2_TMP_DIR="$(mktemp -d)"
wget -q "$CP_PYTHON2_DISTRO_URL" -O "$PYTHON2_TMP_DIR/python2.tgz" || { rm -rf "$PYTHON2_TMP_DIR"; echo "[ERROR] Cannot download Python 2 from $CP_PYTHON2_DISTRO_URL"; exit 1; }
tar -zxf "$PYTHON2_TMP_DIR/python2.tgz" -C /opt
rm -rf "$PYTHON2_TMP_DIR"
/opt/conda/bin/python2 --version
ln -s /opt/conda/bin/python2 /usr/bin/python2
ln -s /opt/conda/bin/python2 /usr/bin/python
ln -s /opt/conda/bin/pip /usr/bin/pip2
ln -s /opt/conda/bin/pip /usr/bin/pip

# The platform's Python 3.12 distribution in /usr/local/python3.12, the path that it is built for, as
# deploy/infra/aws/install-*-node.sh install it: the commit and the pause of a run use it on the node
function install_python3_packages {
      local _install_path="$1"
      local _tmp_install_dir
      _tmp_install_dir="$(mktemp -d)"
      rm -rf "${_install_path}/python3.12" "${_install_path}/openssl"
      CP_PYTHON3_DISTRO_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/python/3/python3.12.tar.gz"

      echo "Getting python3 distro from $CP_PYTHON3_DISTRO_URL"
      if ! wget -q "${CP_PYTHON3_DISTRO_URL}" -O "${_tmp_install_dir}/python3.tgz" \
            || ! tar zxf "${_tmp_install_dir}/python3.tgz" -C "${_install_path}" \
            || ! "${_install_path}/python3.12/bin/python3.12" -c 'import ssl' \
            || ! "${_install_path}/python3.12/bin/python3.12" -m pip --version; then
            rm -rf "${_tmp_install_dir}"
            echo "[ERROR] Cannot install the python3 distro from $CP_PYTHON3_DISTRO_URL into ${_install_path}/python3.12"
            return 1
      fi
      rm -rf "${_tmp_install_dir}"
      echo "Python3 distro is installed into ${_install_path}/python3.12"

      if command -v python3 &>/dev/null; then
            echo "python3 is already available ($(command -v python3)), leaving it untouched"
      else
            ln -s "${_install_path}/python3.12/bin/python3.12" "/usr/bin/python3"
      fi

      if command -v pip3 &>/dev/null; then
            echo "pip3 is already available ($(command -v pip3)), leaving it untouched"
      else
            ln -s "${_install_path}/python3.12/bin/pip3.12" "/usr/bin/pip3"
      fi
}

install_python3_packages "/usr/local"

# Install jq: the package of Amazon Linux 2023, which its AMI already has
yum install -y jq
jq --version

# Enable forwarding
cat <<EOF >/etc/sysctl.d/k8s.conf
net.bridge.bridge-nf-call-ip6tables = 1
net.bridge.bridge-nf-call-iptables = 1
net.ipv4.ip_forward = 1
EOF
sysctl --system

# Disable SELinux
setenforce 0
sed -i 's/^SELINUX=enforcing$/SELINUX=permissive/' /etc/selinux/config

# Configure GRUB for compatible cgroups fs
# https://github.com/ddometita/mmumshad-kubernetes-the-hard-way/issues/8#issuecomment-1397606994
echo 'GRUB_CMDLINE_LINUX="systemd.unified_cgroup_hierarchy=0"' >> /etc/default/grub
grub2-mkconfig -o /boot/grub2/grub.cfg

# Nvidia drivers
yum install -y vulkan-devel \
                gcc \
                gcc-c++ \
                kernel-devel \
                kernel-modules-extra

DRIVER_VERSION="$NVIDIA_DRIVER_VERSION"

if [ "$NVIDIA_DRIVER_URL_PREFIX" ]; then

    # A custom location serves everything as a single archive:
    # the driver runfile and the Nvidia rpm packages, e.g. Fabric Manager
    NVIDIA_DIST_DIR="$(mktemp -d)"
    curl -k -f -L -o "$NVIDIA_DIST_DIR/$DRIVER_VERSION.tgz" "$NVIDIA_DRIVER_URL_PREFIX/$DRIVER_VERSION.tgz"
    tar -zxf "$NVIDIA_DIST_DIR/$DRIVER_VERSION.tgz" -C "$NVIDIA_DIST_DIR"
    rm -f "$NVIDIA_DIST_DIR/$DRIVER_VERSION.tgz"

    NVIDIA_RUN_FILES=()
    while IFS= read -r _nvidia_file; do
        NVIDIA_RUN_FILES+=("$_nvidia_file")
    done < <(find "$NVIDIA_DIST_DIR" -type f -name '*.run' | sort)
    if [ "${#NVIDIA_RUN_FILES[@]}" -ne 1 ]; then
        echo "[ERROR] Exactly one *.run driver file is expected in $NVIDIA_DRIVER_URL_PREFIX/$DRIVER_VERSION.tgz, but ${#NVIDIA_RUN_FILES[@]} found"
        exit 1
    fi
    chmod +x "${NVIDIA_RUN_FILES[0]}"
    "${NVIDIA_RUN_FILES[0]}" -s

    NVIDIA_RPM_FILES=()
    while IFS= read -r _nvidia_file; do
        NVIDIA_RPM_FILES+=("$_nvidia_file")
    done < <(find "$NVIDIA_DIST_DIR" -type f -name '*.rpm' | sort)
    if [ "${#NVIDIA_RPM_FILES[@]}" -eq 0 ]; then
        echo "[ERROR] At least one *.rpm package is expected in $NVIDIA_DRIVER_URL_PREFIX/$DRIVER_VERSION.tgz, but none found"
        exit 1
    fi
    yum install -y "${NVIDIA_RPM_FILES[@]}"

    rm -rf "$NVIDIA_DIST_DIR"

else

    # The driver runfile and the rpm packages are downloaded into a temporary folder, which is removed afterwards.
    # A failed download (curl -f) stops the build
    NVIDIA_DIST_DIR="$(mktemp -d)"
    NVIDIA_RUN_FILE="$NVIDIA_DIST_DIR/NVIDIA-Linux-$(arch)-$DRIVER_VERSION.run"
    curl -k -f -L -o "$NVIDIA_RUN_FILE" "https://us.download.nvidia.com/tesla/$DRIVER_VERSION/NVIDIA-Linux-$(arch)-$DRIVER_VERSION.run"
    chmod +x "$NVIDIA_RUN_FILE"
    "$NVIDIA_RUN_FILE" -s

    # Fabric Manager for A100, H100, H200 and friends
    NVIDIA_PUBLIC_RPM_URL_PREFIX="https://developer.download.nvidia.com/compute/cuda/repos/amzn2023/x86_64"
    for _nvidia_package in nvidia-fabricmanager libnvidia-cfg nvidia-persistenced; do
        _nvidia_rpm="${_nvidia_package}-${DRIVER_VERSION}-1.amzn2023.x86_64.rpm"
        curl -k -f -L -o "$NVIDIA_DIST_DIR/$_nvidia_rpm" "$NVIDIA_PUBLIC_RPM_URL_PREFIX/$_nvidia_rpm"
        yum install -y "$NVIDIA_DIST_DIR/$_nvidia_rpm"
    done

    rm -rf "$NVIDIA_DIST_DIR"

fi

systemctl enable nvidia-fabricmanager
systemctl enable nvidia-persistenced
