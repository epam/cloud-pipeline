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

ARG BASE_IMG=rockylinux/rockylinux:9.8
FROM $BASE_IMG

# No curl here: the base image ships curl-minimal, which provides /usr/bin/curl and conflicts with the curl package
RUN dnf install yum-utils -y

# Install common dependencies
RUN dnf install -y wget \
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
                   libxcrypt-compat && \
    dnf clean all
RUN dnf install -y --allowerasing coreutils

# Python 2 has no package on Rocky Linux 9: use the platform's Miniconda2, as a job container does.
# /init does not use it. scripts/pipeline-launch/launch.sh does (it looks up python2), when the platform runs
# this image as a job to serve a model (/opt/local/scripts/serve_model.sh).
# It is built for the /opt/conda prefix. /usr/bin/python3 stays the system one, for dnf.
# launch.sh also installs the platform's Python 3.12 into /usr/local, which links libffi.so.6: Rocky Linux 9
# only has libffi.so.8, so link the Miniconda2 one, or that Python's "ctypes" does not load.
# libxcrypt-compat above provides libcrypt.so.1, which the "crypt" module of both Pythons links.
ARG CP_PYTHON2_DIST_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/python/2/Miniconda2-4.7.12.1-Linux-x86_64.tar.gz"
RUN wget -q $CP_PYTHON2_DIST_URL -O python2.tgz && \
    tar -xzf python2.tgz -C /opt/ && \
    rm -f python2.tgz && \
    ln -sf /opt/conda/bin/python2 /usr/local/bin/python2 && \
    ln -sf /opt/conda/bin/python2 /usr/local/bin/python && \
    ln -sf /opt/conda/bin/pip /usr/local/bin/pip && \
    ln -s /opt/conda/lib/libffi.so.6 /usr/lib64/libffi.so.6

# Install pip
RUN curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 && \
    python2 -m pip install \
    --index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple \
    --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com \
    -I -q setuptools==44.1.1

# MLFlow base
RUN mkdir -p /opt/local/mamba && \
    cd /opt/local/mamba && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    export PATH=$PATH:/opt/local/mamba/bin && \
    export MAMBA_ROOT_PREFIX=/opt/local/mamba && \
    eval "$(micromamba shell hook --shell bash)" && \
    micromamba create -n mlflow && \
    micromamba activate mlflow && \
    micromamba install -y python==3.10 && \
    pip install mlflow psycopg2-binary boto3

# MLflow GUI
ENV NVM_DIR=/opt/local/nvm
RUN mkdir -p $NVM_DIR && \
    curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash && \
    source "$NVM_DIR/nvm.sh" && \
    nvm install 22 && \
    corepack enable yarn

ENV MLFLOW_JS_BUILD_DIR=/opt/local/mamba/envs/mlflow/lib/python3.10/site-packages/mlflow/server/js/build
RUN source "$NVM_DIR/nvm.sh" && \
    cd /tmp && \
    git clone https://github.com/rodichenko/mlflow.git && \
    cd mlflow && \
    git checkout 40ece48c449b893c0e7e50fc8920831054efdf22 && \
    cd mlflow/server/js && \
    yes " " | yarn install && \
    yarn run build && \
    rm -rf $MLFLOW_JS_BUILD_DIR/* && \
    cp -r build/* $MLFLOW_JS_BUILD_DIR/ && \
    rm -rf /tmp/mlflow

# Scripts
ADD init /init
RUN chmod +x /init
ADD serve_model.sh /opt/local/scripts/serve_model.sh
RUN chmod +x /opt/local/scripts/serve_model.sh
ADD liveness-mlflow.sh /liveness-mlflow.sh
RUN chmod +x /liveness-mlflow.sh
