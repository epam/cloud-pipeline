# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMG=library/rockylinux:8.7
FROM $BASE_IMG

# Install common dependencies
RUN yum install -y wget \
                   tmux \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   fuse \
                   tzdata \
                   acl \
                   openssh-server \
                   yum-utils \
                   findutils \
                   rsync && \
    yum clean all

RUN dnf install -y --allowerasing coreutils

# Install Python 3.12: the platform's Python 3.12 distribution (python3.12.tar.gz of the public Cloud Pipeline bucket,
# self-contained CPython 3.12), the Python that pipe-common requires, into /usr/local/python3.12. setuptools 81.0.0 is
# the version that pipe-common's setup.py pins
ARG CP_PYTHON3_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/python/3/python3.12.tar.gz"
RUN wget -q "$CP_PYTHON3_DIST_URL" -O /tmp/python3.tgz && \
    tar -xzf /tmp/python3.tgz -C /usr/local/ && \
    rm -f /tmp/python3.tgz && \
    ln -sf /usr/local/python3.12/bin/python3.12 /usr/local/bin/python3 && \
    ln -sf /usr/local/python3.12/bin/pip3.12 /usr/local/bin/pip3 && \
    /usr/local/python3.12/bin/python3.12 -m pip install \
    --index-url http://cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com/tools/python/pypi3/simple \
    --trusted-host cloud-pipeline-oss-builds.s3-website.us-east-1.amazonaws.com \
    --no-cache-dir -I -q setuptools==81.0.0

# Install NFS/SMB/Lustre clients
RUN yum install nfs-utils cifs-utils -y
RUN cd /tmp && \
    yum install nfs-utils cifs-utils -y && \
    wget -q https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-2.10.8-2.el8.x86_64.tar.gz -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    yum install -y lustre-client/*.rpm && \
    (package-cleanup --cleandupes -y || true) && \
    rm -rf lustre-client*
