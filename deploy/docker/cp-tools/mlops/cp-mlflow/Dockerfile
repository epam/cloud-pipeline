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

RUN dnf install yum-utils -y

# Install common dependencies
# libxcrypt-compat provides libcrypt.so.1 (Rocky Linux 8 had it in the base): the "crypt" modules of the Python 2
# below and of the Python 3.12 that launch.sh installs link it
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
                   patch \
                   libxcrypt-compat && \
    dnf clean all
RUN dnf install -y --allowerasing coreutils

# Python 2 has no package on Rocky Linux 9: use the platform's Miniconda2. A run's launch.sh installs
# pipe-common into the "python2" it finds on PATH, and fails if there is none. The distribution is built
# for the /opt/conda prefix; /usr/bin/python3 stays the system one, for dnf.
# launch.sh also installs the platform's Python 3.12 into /usr/local, which links libffi.so.6: Rocky Linux 9
# only has libffi.so.8, so link the Miniconda2 one, or that Python's "ctypes" does not load.
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
    pip install mlflow

# Patch MLFlow
ADD patches /tmp/patches
RUN cd /opt/local/mamba/envs/mlflow/lib/python3.10/site-packages/mlflow/tracking && \
    patch -p1 fluent.py < /tmp/patches/fluent.py.patch
