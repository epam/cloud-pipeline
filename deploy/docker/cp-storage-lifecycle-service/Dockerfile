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
# bzip2 unpacks micromamba, gcc builds the C extensions of the packages that come as sources, procps-ng provides pgrep for liveness.sh
RUN dnf install -y \
                wget \
                unzip \
                gcc \
                bzip2 \
                procps-ng

# Python 3.6, the python3 of the Ubuntu 18.04 image this one replaces: the pipe-common and sls pins are for it.
# Rocky Linux 9 has no Python 3.6 package, so it comes from conda-forge, via micromamba. Its bin goes first in PATH,
# as the python3 and pip of the steps below and of start.sh; /usr/bin/python3 stays the system one, for dnf.
ENV MAMBA_ROOT_PREFIX=/opt/local/mamba
ENV CP_SLS_PYTHON_HOME=$MAMBA_ROOT_PREFIX/envs/sls
ENV PATH=$CP_SLS_PYTHON_HOME/bin:$PATH
RUN mkdir -p $MAMBA_ROOT_PREFIX && \
    cd $MAMBA_ROOT_PREFIX && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    bin/micromamba create -y --prefix $CP_SLS_PYTHON_HOME -c conda-forge python=3.6 pip && \
    bin/micromamba clean -a -y
RUN python3 -m pip install --upgrade pip && python3 -m pip install setuptools_rust

# API distribution
ARG CP_API_DIST_URL=""
ENV COMMON_REPO_DIR=/usr/sbin/CommonRepo
ENV CP_SLS_HOME="/opt/cp-sls"
ENV CP_SLS_RUN_MODE=""

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    cd bin && \
    mkdir -p $CP_SLS_HOME && \
    mv sls.tar.gz $CP_SLS_HOME/ && \
    unzip pipeline.jar && \
    mkdir -p $COMMON_REPO_DIR && \
    mv BOOT-INF/classes/static/pipe-common.tar.gz $COMMON_REPO_DIR/pipe-common.tar.gz && \
    # Installing pipe-common
    cd $COMMON_REPO_DIR && \
    tar xf pipe-common.tar.gz && \
    python3 -m pip install . $CP_PIP_EXTRA_ARGS -I && \
    chmod 777 . -R && \
    rm -f pipe-common.tar.gz && \
    # Copy sls distro
    cd $CP_SLS_HOME/ && \
    tar -zxf $CP_SLS_HOME/sls.tar.gz && \
    rm -rf $CP_SLS_HOME/sls.tar.gz  && \
    mv sls* sls && \
    chown -R root:root sls && chmod -R 777 sls && \
    rm -rf /tmp/*

RUN cd $CP_SLS_HOME/sls && \
    python3 setup.py install

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

ADD start.sh /start.sh
RUN chmod +x /start.sh

CMD ["/start.sh"]
