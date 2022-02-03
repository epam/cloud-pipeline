# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE="centos:7"
FROM $BASE_IMAGE

ARG BIOFORMATS_TO_RAW_VERSION=0.4.0
ARG BIOFORMATS_TO_RAW_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION.zip"

ARG RAW_TO_OMETIFF_VERSION=0.3.0
ARG RAW_TO_OMETIFF_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION.zip"

ARG BFTOOLS_VERSION=6.8.0
ARG BFTOOLS_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bftools/bftools-cli-$BFTOOLS_VERSION.zip"

ARG PYTHON_3_9_DISTRIBUTION_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/python/3/Python-3.9.6.tgz"

ENV HCS_TOOLS_HOME="/opt/local/hcs-tools"
ENV LANG="en_US.UTF-8"
ENV LANGUAGE=en_US

# Install common dependencies
RUN yum install -y curl \
                   git \
                   openssh-server \
                   wget \
                   unzip && \
    curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

# Install HCS-tools and their dependencies
RUN yum install -y -q epel-release && \
    yum update -y && \
    yum install -y -q blosc \
                      gcc \
                      java-1.8.0-openjdk-devel \
                      make \
                      openssl-devel \
                      xz-devel && \
    mkdir -p "$HCS_TOOLS_HOME" && \
    wget -q "$BIOFORMATS_TO_RAW_DISTR_URL" -O "$HCS_TOOLS_HOME/bioformats2raw.zip" && \
    unzip "$HCS_TOOLS_HOME/bioformats2raw.zip" -d "$HCS_TOOLS_HOME/bioformats2raw" && \
    rm -f "$HCS_TOOLS_HOME/bioformats2raw.zip" && \
    chmod +x "$HCS_TOOLS_HOME/bioformats2raw/bin/bioformats2raw" && \
    wget -q "$RAW_TO_OMETIFF_DISTR_URL" -O "$HCS_TOOLS_HOME/raw2ometiff.zip" && \
    unzip "$HCS_TOOLS_HOME/raw2ometiff.zip" -d "$HCS_TOOLS_HOME/raw2ometiff" && \
    rm -f "$HCS_TOOLS_HOME/raw2ometiff.zip" && \
    chmod +x "$HCS_TOOLS_HOME/raw2ometiff/bin/raw2ometiff" && \
    wget "$BFTOOLS_DISTR_URL" -O "$HCS_TOOLS_HOME/bftools-cli.zip" && \
    unzip "$HCS_TOOLS_HOME/bftools-cli.zip" -d "$HCS_TOOLS_HOME" && \
    rm -f "$HCS_TOOLS_HOME/bftools-cli.zip" && \
    wget "$PYTHON_3_9_DISTRIBUTION_URL" -O python3.tgz  && \
    tar xzf python3.tgz && \
    cd Python-* && \
    ./configure --enable-optimizations && \
    make altinstall && \
    cd .. && \
    rm -rf Python-* && \
    pip3.9 install generate-tiff-offsets

ENV PATH="$HCS_TOOLS_HOME/bftools:$HCS_TOOLS_HOME/bioformats2raw/bin:$HCS_TOOLS_HOME/raw2ometiff/bin:$PATH"

ADD process_hcs_files.py "$HCS_TOOLS_HOME/scripts/process_hcs_files.py"
ADD convert_to_ome_tiff.sh "$HCS_TOOLS_HOME/scripts/convert_to_ome_tiff.sh"
ADD start.sh "$HCS_TOOLS_HOME/scripts/start.sh"

RUN chmod +x "$HCS_TOOLS_HOME/scripts/*.sh"
