# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE="rockylinux:8.7"
FROM $BASE_IMAGE

ENV LANG="en_US.UTF-8"
ENV LANGUAGE=en_US

# Install common dependencies
RUN yum install -y curl \
                   git \
                   openssh-server \
                   wget \
                   unzip \
                   python2  \
                   python2-devel \
                   gcc \
                   which \
                   bzip2 && \
    curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 - && \
    python2 -m pip install numpy==1.16.6 && \
    python2 -m pip install pillow==6.2.2 \
                    tifffile==2019.7.26.2 \
                    imagecodecs-lite==2019.12.3 \
                    pandas==0.24.2 \
                    openpyxl==2.6.4

ENV ANACONDA_HOME=/opt/local/mamba
ARG CP_PIP_EXTRA_ARGS="--index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com"

RUN mkdir -p "$ANACONDA_HOME" && \
    cd "$ANACONDA_HOME" && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    export PATH=$PATH:$ANACONDA_HOME/bin && \
    export MAMBA_ROOT_PREFIX=$ANACONDA_HOME && \
    eval "$(micromamba shell hook --shell bash)" && \
    micromamba create -y -n hcs python=3.9 && \
    micromamba activate hcs && \
    yum install -y epel-release && \
    yum install -y blosc \
                   ImageMagick \
                   java-1.8.0-openjdk-devel && \
    pip3 install $CP_PIP_EXTRA_ARGS -I -q generate-tiff-offsets==0.1.7 && \
    pip3 install $CP_PIP_EXTRA_ARGS -I -q awscli

ARG BIOFORMATS_TO_RAW_VERSION=0.11.0
ARG BIOFORMATS_TO_RAW_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION.zip"

ARG RAW_TO_OMETIFF_VERSION=0.8.0
ARG RAW_TO_OMETIFF_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION.zip"

ARG BFTOOLS_VERSION=8.3.0
ARG BFTOOLS_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bftools/bftools-cli-$BFTOOLS_VERSION.zip"

ENV HCS_TOOLS_HOME="/opt/local/hcs-tools"

RUN mkdir -p "$HCS_TOOLS_HOME" && \
    wget -q "$BIOFORMATS_TO_RAW_DISTR_URL" -O "$HCS_TOOLS_HOME/bioformats2raw.zip" && \
    unzip "$HCS_TOOLS_HOME/bioformats2raw.zip" -d "$HCS_TOOLS_HOME/bioformats2raw" && \
    rm -f "$HCS_TOOLS_HOME/bioformats2raw.zip" && \
    mv $HCS_TOOLS_HOME/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION/* "$HCS_TOOLS_HOME/bioformats2raw" && \
    rm -rf "$HCS_TOOLS_HOME/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION" && \
    chmod +x "$HCS_TOOLS_HOME/bioformats2raw/bin/bioformats2raw" && \
    wget -q "$RAW_TO_OMETIFF_DISTR_URL" -O "$HCS_TOOLS_HOME/raw2ometiff.zip" && \
    unzip "$HCS_TOOLS_HOME/raw2ometiff.zip" -d "$HCS_TOOLS_HOME/raw2ometiff" && \
    rm -f "$HCS_TOOLS_HOME/raw2ometiff.zip" && \
    mv $HCS_TOOLS_HOME/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION/* $HCS_TOOLS_HOME/raw2ometiff && \
    rm -rf "$HCS_TOOLS_HOME/raw2ometiff-$RAW_TO_OMETIFF_VERSION" && \
    chmod +x "$HCS_TOOLS_HOME/raw2ometiff/bin/raw2ometiff" && \
    wget "$BFTOOLS_DISTR_URL" -O "$HCS_TOOLS_HOME/bftools-cli.zip" && \
    unzip "$HCS_TOOLS_HOME/bftools-cli.zip" -d "$HCS_TOOLS_HOME" && \
    rm -f "$HCS_TOOLS_HOME/bftools-cli.zip"

ENV PATH="$HCS_TOOLS_HOME/bftools:$HCS_TOOLS_HOME/bioformats2raw/bin:$HCS_TOOLS_HOME/raw2ometiff/bin:$PATH"

COPY parser "$HCS_TOOLS_HOME/scripts/parser"
COPY convert_to_ome_tiff.sh "$HCS_TOOLS_HOME/scripts/convert_to_ome_tiff.sh"
COPY start.sh "$HCS_TOOLS_HOME/scripts/start.sh"
COPY start_cluster.sh "$HCS_TOOLS_HOME/scripts/start_cluster.sh"
COPY start_list.sh "$HCS_TOOLS_HOME/scripts/start_list.sh"

RUN chmod +x $HCS_TOOLS_HOME/scripts/*.sh
