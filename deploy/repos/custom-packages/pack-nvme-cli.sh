#!/usr/bin/env bash

# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

docker run  -it \
            --rm \
            --privileged \
            -v $(pwd):/nvme-build \
            -e http_proxy=$http_proxy \
            -e https_proxy=$https_proxy \
            centos:7 bash

yum install -y epel-release && \
yum install -y centos-release-scl && \
yum install -y git devtoolset-9

cd /nvme-build && \
git clone https://github.com/linux-nvme/nvme-cli && \
cd nvme-cli && \
git checkout tags/v1.16 && \
make -j $(nproc) && \
PREFIX=/nvme-build/build make install && \
cd /nvme-build/build/sbin && \
gzip nvme && \
mv nvme.gz /nvme-build/

