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

FROM rockylinux/rockylinux:9.8

# bzip2 unpacks the micromamba archive below.
# libxcrypt-compat provides libcrypt.so.1, which the pipe-omics bundle inside the pipe CLI links
RUN dnf install -y wget unzip bzip2 libxcrypt-compat && \
    dnf clean all

# Python 3.9 from micromamba (conda-forge), in place of the python:3.9-slim interpreter. Its python, python3 and pip
# are linked as the ones the steps below and init call; /usr/bin/python3 stays the system one, for dnf.
ENV MAMBA_ROOT_PREFIX=/opt/local/mamba
ENV CP_CLEANUP_PYTHON_ENV=$MAMBA_ROOT_PREFIX/envs/cleanup
RUN mkdir -p $MAMBA_ROOT_PREFIX && \
    cd $MAMBA_ROOT_PREFIX && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xj bin/micromamba && \
    bin/micromamba create -y --prefix $CP_CLEANUP_PYTHON_ENV -c conda-forge python=3.9 && \
    bin/micromamba clean --all --yes && \
    ln -sf $CP_CLEANUP_PYTHON_ENV/bin/python3.9 /usr/local/bin/python && \
    ln -sf $CP_CLEANUP_PYTHON_ENV/bin/python3.9 /usr/local/bin/python3 && \
    ln -sf $CP_CLEANUP_PYTHON_ENV/bin/pip /usr/local/bin/pip

ARG CP_API_DIST_URL=""
ENV COMMON_REPO_DIR=/usr/sbin/CommonRepo
ENV CP_CLEANUP_RUNS_HOME=/opt/cp-run-cleanup-job

# pipe-common pins setuptools 44.1.1: pip replaces the env's conda setuptools with it, but leaves the conda one's
# distutils-precedence.pth behind, and that file then fails on every python start. Remove it, if its module is gone.
RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    cd bin && \
    unzip -q pipeline.jar && \
    mkdir -p $COMMON_REPO_DIR && \
    mv BOOT-INF/classes/static/pipe-common.tar.gz $COMMON_REPO_DIR/pipe-common.tar.gz && \
    mv BOOT-INF/classes/static/pipe.tar.gz $COMMON_REPO_DIR/pipe.tar.gz && \
    cd $COMMON_REPO_DIR && \
    tar xf pipe-common.tar.gz && \
    pip install --no-cache-dir . && \
    SITE_PACKAGES=$CP_CLEANUP_PYTHON_ENV/lib/python3.9/site-packages && \
    { [ -d $SITE_PACKAGES/_distutils_hack ] || rm -f $SITE_PACKAGES/distutils-precedence.pth; } && \
    rm -f pipe-common.tar.gz && \
    tar xf pipe.tar.gz && \
    rm -f pipe.tar.gz && \
    ln -s $COMMON_REPO_DIR/pipe/pipe /usr/local/bin/ && \
    rm -rf /tmp/*

WORKDIR $CP_CLEANUP_RUNS_HOME
COPY cleanup/ cleanup/

ADD init /init
RUN chmod +x /init

CMD ["/init"]
