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

# Setup httpd
# procps-ng provides the "sysctl" that init calls and the "ps" that nfs-watcher.sh calls
RUN dnf update -y && \
    dnf install -y  gettext \
                    wget \
                    expat-devel \
                    make \
                    gcc \
                    pcre-devel \
                    zlib-devel \
                    libcurl-devel \
                    libxml2-devel \
                    openssl-devel \
                    procps-ng && \
    dnf clean all

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
RUN rpm -Uvh https://packages.microsoft.com/config/rhel/9/packages-microsoft-prod.rpm
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
    # OpenSSL 3 removed ERR_GET_FUNC (its function codes are always 0): without this, mod_ssl.so keeps an
    # undefined ERR_GET_FUNC symbol and httpd fails to load it
    sed -i 's/ERR_GET_FUNC(ERR_peek_last_error())/0/' modules/ssl/ssl_engine_init.c && \
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
# blobfuse (v1) has no el9 package, the Microsoft rhel/9 repo has only blobfuse2 (it pulls in fuse3):
# syncmounts.sh mounts the Azure storages with its v1-compatible "blobfuse2 mountv1"
RUN dnf install -y cronie \
                   nfs-utils \
                   cifs-utils \
                   blobfuse2 fuse

# Rocky 9's pam_unix checks an account through its unix_chkpwd helper. Where the host's AppArmor has the
# unix-chkpwd profile (Ubuntu 24.04 has it), the helper cannot read /etc/shadow and crond refuses to run
# root's jobs. Let root pass the account check of crond
RUN sed -i '/^account\s\+include\s\+system-auth/i account    sufficient pam_succeed_if.so uid = 0 quiet' /etc/pam.d/crond && \
    grep -q pam_succeed_if /etc/pam.d/crond

# Python 3.12 runs the sync scripts and the NFS mount watcher: the build a job container installs
# (scripts/pipeline-launch/launch.sh). The cron jobs run with the PATH of crond (/usr/bin:/bin), so they call it
# by its full path. /usr/bin/python3 stays the system one, for dnf.
# That build's ctypes links libffi.so.6, and Rocky Linux 9 has libffi.so.8: the NFS mount watcher's "watchdog" needs
# ctypes, so libffi.so.6 comes from the EPEL compat package libffi3.1. EPEL is removed afterwards, so the later dnf
# installs resolve as before
ARG CP_PYTHON3_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/python/3/python3.12.tar.gz"
RUN dnf install -y epel-release && \
    dnf install -y libffi3.1 && \
    dnf remove -y epel-release && \
    dnf clean all && \
    wget -q $CP_PYTHON3_DIST_URL -O python3.tgz && \
    tar -xzf python3.tgz -C /usr/local/ && \
    rm -f python3.tgz && \
    ln -sf /usr/local/python3.12/bin/python3.12 /usr/local/bin/python3 && \
    ln -sf /usr/local/python3.12/bin/pip3.12 /usr/local/bin/pip3

ARG LUSTRE_VERSION="2.10.8-2.el8.x86_64"
ARG LUSTRE_CLIENT_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-$LUSTRE_VERSION.tar.gz"
# yum-utils provides package-cleanup
RUN cd /tmp && \
    dnf install -y yum-utils && \
    wget -q $LUSTRE_CLIENT_URL -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    dnf install -y lustre-client/*.rpm && \
    package-cleanup --cleandupes -y && \
    rm -rf lustre-client*

RUN pip3 install -I requests==2.34.2 && \
    wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
    chmod +x /usr/bin/jq

ENV SYNC_HOME=/opt/sync
ENV SYNC_LOG_DIR=/var/log/dav/sync
COPY sync/crontab /tmp/sync
COPY sync/sync-nfs.sh ${SYNC_HOME}/
COPY sync/nfs-watcher.sh ${SYNC_HOME}/
# "nfs-roles-management" shall be copied to the docker build folder from "(repo-root)/scripts/nfs-roles-management"
# E.g.: cp cp ../../../scripts/nfs-roles-management ./ -r && docker build ...
COPY nfs-roles-management ${SYNC_HOME}/nfs-roles-management

COPY init /init
RUN  chmod +x /init
