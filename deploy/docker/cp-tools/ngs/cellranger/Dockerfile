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

# CELLRANGER_URL can be obtained from https://www.10xgenomics.com/support/software/cell-ranger/downloads#download-links
# docker build . \
#       --build-arg BASE_IMG=library/rockylinux:8.9 \
#       --build-arg CELLRANGER_URL="https://cf.10xgenomics.com/releases/cell-exp/cellranger..."

ARG BASE_IMG=library/rockylinux:8.9
FROM $BASE_IMG

RUN yum install curl yum-utils -y

# Install common dependencies
RUN yum install -y wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   python2 \
                   fuse \
                   tzdata \
                   acl \
                   openssh-server \
                   yum-utils \
                   findutils && \
    yum clean all
RUN dnf install -y --allowerasing coreutils

# Install pip
RUN curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 && \
    python2 -m pip install \
    --index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple \
    --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com \
    -I -q setuptools==44.1.1

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

RUN yum install -y cmake \
                   gcc-toolset-9-toolchain

ENV CELLRANGER_HOME="/opt/cellranger"
ENV CELLRANGER_BIN="$CELLRANGER_HOME/cellranger-latest/cellranger"
ENV PATH "$PATH:/$CELLRANGER_HOME/cellranger-latest"

# Configure SGE integration
ADD sge.template /tmp/sge.template

# Install cellranger
ADD install_cr_versions.sh /tmp/install_cr_versions.sh
ARG CELLRANGER_URL=""
RUN bash /tmp/install_cr_versions.sh "$CELLRANGER_URL" "$CELLRANGER_HOME" && \
    rm -f /tmp/install_cr_versions.sh

ENV BCL2FASTQ_HOME="/opt/bcl2fastq"
# Install bcl2fastq 2, if required
ADD install_bcl2fastq2.sh /tmp/install_bcl2fastq2.sh
ARG WITH_BCL2FASTQ2="false"
RUN bash /tmp/install_bcl2fastq2.sh "$WITH_BCL2FASTQ2" && \
    rm -f /tmp/install_bcl2fastq2.sh

WORKDIR $CELLRANGER_HOME
