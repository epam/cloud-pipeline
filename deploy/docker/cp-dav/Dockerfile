# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM rockylinux:8.7

# Setup httpd
RUN yum install -y  gettext \
                    wget \
                    expat-devel \
                    make \
                    gcc \
                    pcre-devel \
                    zlib-devel \
                    libcurl-devel \
                    libxml2-devel \
                    openssl-devel \
                    bzip2 \
                    bzip2-devel \
                    xz \
                    xz-devel \
                    ncurses-devel \
                    procps \
                    patch && \
    yum group install -y "Development Tools" && \
    yum clean all

ARG APACHE_BUILD_DIR=/opt/httpd-build
ARG APACHE_VERSION=2.4.41
ARG APR_VERSION=1.7.0
ARG APR_UTIL_VERSION=1.6.1
ENV APACHE_HOME=/opt/httpd
ENV APACHE_LOG_DIR=/var/log/dav/httpd
ENV CFLAGS="-DBIG_SECURITY_HOLE -O2"

COPY httpd/conf /tmp/
COPY httpd/modules.shared ${APACHE_BUILD_DIR}/
COPY httpd/modules.static ${APACHE_BUILD_DIR}/
RUN yum install -y epel-release && \
    rpm -Uvh https://packages.microsoft.com/config/rhel/8/packages-microsoft-prod.rpm
RUN modules_shared="$(echo $(cat ${APACHE_BUILD_DIR}/modules.shared))" && \
    modules_static="$(echo $(cat ${APACHE_BUILD_DIR}/modules.static))" && \
    mkdir -p ${APACHE_BUILD_DIR} && \
    cd ${APACHE_BUILD_DIR} && \
    wget -q https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/httpd/httpd-${APACHE_VERSION}.tar.gz && \
    tar -zxf httpd-${APACHE_VERSION}.tar.gz && \
    rm -f httpd-${APACHE_VERSION}.tar.gz && \
    cd ${APACHE_BUILD_DIR} && \
    wget -q -c https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/apr/apr-${APR_VERSION}.tar.gz && \
    wget -q -c https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/apr/apr-util-${APR_UTIL_VERSION}.tar.gz && \
    tar -zxf apr-${APR_VERSION}.tar.gz && \
    rm -f apr-${APR_VERSION}.tar.gz && \
    tar -zxf apr-util-${APR_UTIL_VERSION}.tar.gz && \
    rm -f apr-util-${APR_UTIL_VERSION}.tar.gz && \
    mv apr-${APR_VERSION} ${APACHE_BUILD_DIR}/httpd-${APACHE_VERSION}/srclib/apr && \
    mv apr-util-${APR_UTIL_VERSION} ${APACHE_BUILD_DIR}/httpd-${APACHE_VERSION}/srclib/apr-util && \
    cd ${APACHE_BUILD_DIR}/httpd-${APACHE_VERSION} && \
    ./configure --prefix=${APACHE_HOME} \
                --with-included-apr \
                --with-included-apr-util \
                --enable-mods-shared="$modules_shared" \
                --enable-mods-static="$modules_static" \
                --enable-load-all-modules && \
    make -j $(nproc) && \
    make install && \
    rm -rf ${APACHE_BUILD_DIR}

# Setup nfs sync
RUN yum install -y cronie \
                   nfs-utils \
                   cifs-utils \
                   python2 blobfuse fuse && \
    ln -sf /usr/bin/python2 /usr/bin/python
ARG LUSTRE_VERSION="2.10.8-2.el8.x86_64"
ARG LUSTRE_CLIENT_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-$LUSTRE_VERSION.tar.gz"
RUN cd /tmp && \
    wget -q $LUSTRE_CLIENT_URL -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    yum install -y lustre-client/*.rpm && \
    package-cleanup --cleandupes -y && \
    rm -rf lustre-client*

RUN curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python && \
    pip install -I requests==2.21.0 futures && \
    wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
    chmod +x /usr/bin/jq

# Python 3.12, alongside the Python 2 install above (not a replacement): CP_PYTHON_VERSION selects
# between them at container start, and the SMB server keeps its own separate micromamba env either way.
ARG CP_PYTHON3_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/python/3/python3.12.tar.gz"
RUN wget -q $CP_PYTHON3_DIST_URL -O python3.tgz && \
    tar -xzf python3.tgz -C /usr/local/ && \
    rm -f python3.tgz && \
    ln -sf /usr/local/python3.12/bin/python3.12 /usr/local/bin/python3 && \
    ln -sf /usr/local/python3.12/bin/pip3.12 /usr/local/bin/pip3 && \
    pip3 install -I requests==2.34.2

ENV SYNC_HOME=/opt/sync
ENV SYNC_LOG_DIR=/var/log/dav/sync
COPY sync/crontab /tmp/sync
COPY sync/sync-nfs.sh ${SYNC_HOME}/
COPY sync/nfs-watcher.sh ${SYNC_HOME}/
# "nfs-roles-management" shall be copied to the docker build folder from "(repo-root)/scripts/nfs-roles-management"
# E.g.: cp cp ../../../scripts/nfs-roles-management ./ -r && docker build ...
COPY nfs-roles-management ${SYNC_HOME}/nfs-roles-management

# Extras
ENV EXTRA_HOME=/opt/dav-extra
RUN pip install PyJWT==1.6.1 cryptography==2.6.1 flask==1.1.4 xxhash==2.0.2
# Python 3 pins differ from the Python 2 ones above where the old pin doesn't import under 3.12:
# PyJWT==1.6.1 uses collections.Iterable, removed in 3.10+; Flask==1.1.4's Jinja2/MarkupSafe pair
# resolves to incompatible newer releases under pip's 3.12 resolver. cryptography and xxhash import
# fine unchanged. Verified by installing and importing all five together in a real 3.12 venv.
RUN pip3 install PyJWT==2.8.0 cryptography==2.6.1 flask==2.3.3 xxhash==2.0.2
COPY extra ${EXTRA_HOME}

# SMB server — Python 3.12 via micromamba
ENV SMB_HOME=/opt/cp-smb
ENV SMB_CONDA_ENV=smb

RUN mkdir -p /opt/local/mamba && \
    cd /opt/local/mamba && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    /opt/local/mamba/bin/micromamba create -y \
        --prefix /opt/local/mamba/envs/${SMB_CONDA_ENV} \
        --root-prefix /opt/local/mamba \
        -c conda-forge \
        python=3.12 && \
    /opt/local/mamba/envs/${SMB_CONDA_ENV}/bin/pip install --no-cache-dir \
        impacket \
        PyJWT \
        cryptography \
        flask \
        werkzeug

COPY smb ${SMB_HOME}

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

COPY init /init
RUN  chmod +x /init
