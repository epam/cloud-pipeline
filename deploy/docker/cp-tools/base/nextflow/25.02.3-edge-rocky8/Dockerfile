# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE=nvidia/cuda:11.3.1-cudnn8-runtime-rockylinux8
FROM ${BASE_IMAGE}

ARG NXF_VER="25.02.3-edge"

# Common applications
RUN dnf install -y \
    ca-certificates \
  && dnf install -y \
    which \
    nano \
    diffutils \
    git \
    wget curl \
    zip unzip bzip2 \
    jre-1.8.0-openjdk \
    jre-21-openjdk \
    graphviz \
    python2 \
  && dnf clean all \
  && alternatives --install /usr/bin/java java /usr/lib/jvm/jre-21-openjdk/bin/java 21000 \
  && alternatives --install /usr/bin/java java /usr/lib/jvm/jre-1.8.0-openjdk/bin/java 10800 \
  && alternatives --install /usr/bin/java java /usr/lib/jvm/jre-1.8.0-openjdk/bin/java 10800 \
  && alternatives --set java  /usr/lib/jvm/jre-21-openjdk/bin/java \
  && curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 -

ENV CONDA_DIR="/opt/conda" 
ENV PATH="$CONDA_DIR/bin:$PATH"
RUN wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O /tmp/miniconda.sh && \
    bash /tmp/miniconda.sh -b -p $CONDA_DIR && \
    rm -f /tmp/miniconda.sh && \
    conda update -y -n base -c defaults conda && \
    conda clean -afy

ENV PATH="/opt/conda/bin:$PATH"

# Stick to a specific version, as the installation script at get.nextflow.io may be upgraded without a notification
# NXF_VER will be considered in the installation script
ENV NXF_HOME_ROOT="/opt/nextflow"
ENV NXF_HOME="$NXF_HOME_ROOT/$NXF_VER"

ENV CP_CAP_GE_CONSUMABLE_RESOURCE_NAME_RAM="mem_free"
ENV CP_CAP_GE_CONSUMABLE_RESOURCE_CONSUMABLE_TYPE_RAM="JOB"

# Redefine default analysis and input location to /common, which will be shared across nodes. These values can be also changed during startup
ENV ANALYSIS_DIR="/common/analysis"
ENV INPUT_DIR="/common/input"

# Install nextflow itlself into $NXF_HOME
RUN mkdir -p $NXF_HOME && \
    cd $NXF_HOME_ROOT && \
    curl -fsSL get.nextflow.io | bash && \
    mv nextflow "$NXF_HOME/nextflow"

ADD nextflow /usr/bin/nextflow
RUN chmod +x /usr/bin/nextflow

ADD nf-weblog-handler /opt/nf-weblog-handler
RUN chmod +x /opt/nf-weblog-handler/nf-weblog-handler.sh
RUN python2 -m pip install flask

