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
      CP_PYTHON3_DISTRO_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/python/3/python3.12.tar.gz"

      # The distro is unpacked next to its place, as scripts/pipeline-launch/launch.sh does, and moved in place
      # only when it runs. The check reads the version it prints, as a file that is not a binary, e.g. an empty one,
      # is run by bash as a script, and succeeds
      _tmp_install_dir="$(mktemp -d "${_install_path}/.cp-python3.XXXXXX")"
      echo "Getting python3 distro from $CP_PYTHON3_DISTRO_URL"
      if ! wget -q "${CP_PYTHON3_DISTRO_URL}" -O "${_tmp_install_dir}/python3.tgz" \
            || ! tar zxf "${_tmp_install_dir}/python3.tgz" -C "${_tmp_install_dir}" \
            || [ "$("${_tmp_install_dir}/python3.12/bin/python3.12" -c 'import ssl, sys; print("%d.%d" % sys.version_info[:2])')" != "3.12" ] \
            || ! "${_tmp_install_dir}/python3.12/bin/python3.12" -m pip --version; then
            rm -rf "${_tmp_install_dir}"
            echo "[ERROR] Cannot install the python3 distro from $CP_PYTHON3_DISTRO_URL into ${_install_path}/python3.12"
            return 1
      fi
      rm -rf "${_install_path}/python3.12"
      mv "${_tmp_install_dir}/python3.12" "${_install_path}/python3.12"
      # The distro has its OpenSSL built in, and /usr/local/openssl may be the image's own: only a distro that brings
      # an openssl folder (the previous python3.12.0 build) replaces it
      if [ -d "${_tmp_install_dir}/openssl" ]; then
            rm -rf "${_install_path}/openssl"
            mv "${_tmp_install_dir}/openssl" "${_install_path}/openssl"
      fi
      rm -rf "${_tmp_install_dir}"
      echo "Python3 distro is installed into ${_install_path}/python3.12"

      # python3 and pip3 are linked to it only if the image has no python3 of its own, so that pip3 matches python3
      if command -v python3 &>/dev/null; then
            echo "python3 is already available ($(command -v python3)), leaving python3 and pip3 untouched"
      else
            ln -s "${_install_path}/python3.12/bin/python3.12" "/usr/bin/python3"
            if command -v pip3 &>/dev/null; then
                  echo "pip3 is already available ($(command -v pip3)), leaving it untouched"
            else
                  ln -s "${_install_path}/python3.12/bin/pip3.12" "/usr/bin/pip3"
            fi
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