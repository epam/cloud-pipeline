#!/usr/bin/env bash
# Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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


function install_prerequisites {
  echo "Installing DCV prerequisites"
  _pkg_result=0

  apt update -y && \
  DEBIAN_FRONTEND=noninteractive apt install -y --no-install-recommends ubuntu-desktop
  _pkg_result=$?

  if [ $_pkg_result -ne 0 ]; then
    echo "[ERROR] Cannot install system prerequisites for DCV" 
    return $_pkg_result
  fi

  python -m pip install $CP_PIP_EXTRA_ARGS flask==1.1.1 Flask-HTTPAuth==3.3.0
  _py_result=$?
  if [ $_py_result -ne 0 ]; then
    echo "[ERROR] Cannot install python prerequisites for DCV" 
    return $_py_result
  fi
}

function install_dcv {
  echo "Installing DCV server" 
  _pkg_result=0
  _dcv_distro_url=https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/dcv

  _NICE_DCV_DISTRIBUTION=nice-dcv-2021.2-11190-ubuntu1804-x86_64
  wget "$_dcv_distro_url/NICE-GPG-KEY"
  gpg --import NICE-GPG-KEY
  wget "$_dcv_distro_url/$_NICE_DCV_DISTRIBUTION.tgz" && \
  tar -xvzf $_NICE_DCV_DISTRIBUTION.tgz && cd $_NICE_DCV_DISTRIBUTION && \
  apt install -y ./nice-dcv-server_2021.2.11190-1_amd64.ubuntu1804.deb \
                 ./nice-dcv-web-viewer_2021.2.11190-1_amd64.ubuntu1804.deb \
                 ./nice-xdcv_2021.2.411-1_amd64.ubuntu1804.deb
  _pkg_result=$?

  if [ $_pkg_result -ne 0 ]; then
    echo "[ERROR] Cannot install DCV itself"
    return $_pkg_result
  fi
  cd ..
  rm -rf "${_NICE_DCV_DISTRIBUTION}*"

  echo "Configuring DCV server"

  usermod -aG video dcv

sed -i 's|#authentication="none"|authentication="none"|g' /etc/dcv/dcv.conf

  cat <<EOF >>/etc/dcv/dcv.conf
[clipboard]
primary-selection-copy=true
primary-selection-paste=true
EOF

  echo "DCV successfully installed and configured"
}

install_prerequisites && \
install_dcv
