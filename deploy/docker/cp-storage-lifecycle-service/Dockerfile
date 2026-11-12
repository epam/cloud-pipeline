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
# gcc builds the C extensions of the packages that come as sources, procps-ng provides pgrep for liveness.sh,
# libxcrypt-compat provides libcrypt.so.1, which the crypt module of the Python 3.12 below links
RUN dnf install -y \
                wget \
                unzip \
                gcc \
                procps-ng \
                libxcrypt-compat && \
    dnf clean all

# Python 3.12, the version pipe-common requires: the platform's Python 3.12 distribution. Its python3 and pip3 in
# /usr/local/bin are the ones of the steps below and of start.sh; /usr/bin/python3 stays the system one, for dnf.
ARG CP_PYTHON3_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/python/3/python3.12.tar.gz"
RUN wget -q $CP_PYTHON3_DIST_URL -O python3.tgz && \
    tar -xzf python3.tgz -C /usr/local/ && \
    rm -f python3.tgz && \
    ln -sf /usr/local/python3.12/bin/python3.12 /usr/local/bin/python3 && \
    ln -sf /usr/local/python3.12/bin/pip3.12 /usr/local/bin/pip3
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

# pip, not "setup.py install": since setuptools 80.1, "setup.py install" no longer runs easy_install, so it would
# install the sls package without its dependencies
# "pip check" fails the build on a dependency conflict between the sls and pipe-common versions
RUN cd $CP_SLS_HOME/sls && \
    python3 -m pip install . && \
    python3 -m pip check

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

ADD start.sh /start.sh
RUN chmod +x /start.sh

CMD ["/start.sh"]
