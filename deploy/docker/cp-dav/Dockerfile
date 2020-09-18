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

FROM library/centos:7

# Setup httpd
RUN yum update -y && \
    yum install -y  gettext \
                    wget \
                    expat-devel \
                    make \
                    gcc \
                    pcre-devel \
                    zlib-devel \
                    libcurl-devel \
                    libxml2-devel \
                    openssl-devel
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
RUN rpm -Uvh https://packages.microsoft.com/config/rhel/7/packages-microsoft-prod.rpm
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
                   python blobfuse fuse
ARG LUSTRE_VERSION="2.12.5-1.el7.x86_64"
ARG LUSTRE_CLIENT_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-$LUSTRE_VERSION.rpm"
ARG LUSTRE_KMOD_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/kmod-lustre-client-$LUSTRE_VERSION.rpm"
RUN wget -q $LUSTRE_KMOD_URL -O kmod-lustre-client.rpm && \
    wget -q $LUSTRE_CLIENT_URL -O lustre-client.rpm && \
    yum install -y -q kmod-lustre-client.rpm lustre-client.rpm && \
    rm -f *lustre-client.rpm
RUN curl -s https://bootstrap.pypa.io/get-pip.py | python && \
    pip install -I requests==2.21.0 && \
    wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
    chmod +x /usr/bin/jq
ENV SYNC_HOME=/opt/sync
ENV SYNC_LOG_DIR=/var/log/dav/sync
COPY sync/crontab /tmp/sync
COPY sync/sync-nfs.sh ${SYNC_HOME}/
# "nfs-roles-management" shall be copied to the docker build folder from "(repo-root)/scripts/nfs-roles-management"
# E.g.: cp cp ../../../scripts/nfs-roles-management ./ -r && docker build ...
COPY nfs-roles-management ${SYNC_HOME}/nfs-roles-management

COPY init /init
RUN  chmod +x /init
