# Copyright 2026 EPAM Systems, Inc. (https://www.epam.com/)
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

# The builder of the Linux pipe CLI bundles: build_linux.sh runs it as lifescience/cloud-pipeline:pipe-cli-centos7-py312
#   docker build --platform linux/amd64 -t lifescience/cloud-pipeline:pipe-cli-centos7-py312 .

FROM centos:7

# Build Python 3.12 from source on CentOS 7 (glibc 2.17). PyInstaller bundles every library but glibc,
# so the pipe CLI built here runs on glibc 2.17 or later: CentOS / RHEL 7, Amazon Linux 2,
# Ubuntu 16.04, Debian 9 and later.
# CentOS 7 has OpenSSL 1.0.2, while Python 3.12 needs 1.1.1 or later, so OpenSSL is built from source too.

ARG OPENSSL_VERSION="3.5.9"
ARG OPENSSL_SHA256="603f5602e2eef00d77fbd429d34dcd5822bb301757a1bc9cdb24c670f1eb859a"
ARG PYTHON_VERSION="3.12.16"
ARG PYTHON_SHA256="b5796915dc45ff745cfb4e990a7e7c93d6f546328a102ce31448d69ebc37575c"

# CentOS 7 has reached its end of life, its packages are kept at vault.centos.org only.
# The build of Python 3.12 with GCC 4.8 of CentOS 7 crashed (the compiler, on Python/pystrhex.c), so the newer GCC
# of devtoolset-11 is used. As in the manylinux2014 images, what it builds still needs glibc 2.17 only.
RUN sed -i -e 's/^mirrorlist=/#mirrorlist=/' \
           -e 's|^#baseurl=http://mirror.centos.org/centos/$releasever|baseurl=https://vault.centos.org/7.9.2009|' \
           /etc/yum.repos.d/CentOS-Base.repo && \
    yum update -y && \
    yum install -y centos-release-scl-rh && \
    sed -i -e 's/^mirrorlist=/#mirrorlist=/' \
           -e 's|^#[[:space:]]*baseurl=http://mirror.centos.org/centos/7|baseurl=https://vault.centos.org/7.9.2009|' \
           /etc/yum.repos.d/CentOS-SCLo-scl-rh.repo && \
    yum groupinstall -y "Development Tools" && \
    yum install -y devtoolset-11-gcc devtoolset-11-gcc-c++ devtoolset-11-make \
        wget zlib-devel bzip2-devel xz-devel sqlite-devel readline-devel ncurses-devel \
        gdbm-devel libuuid-devel libffi-devel fuse fuse-devel git \
        perl-IPC-Cmd perl-Time-Piece perl-Digest-SHA perl-Data-Dumper && \
    yum clean all && \
    rm -rf /var/cache/yum

ENV PATH="/opt/rh/devtoolset-11/root/usr/bin:${PATH}"

# OPENSSLDIR is /etc/pki/tls, as of the OpenSSL packages of CentOS and Rocky Linux: the bundled OpenSSL
# takes the CA certificates and the configuration from there, as the bundles built on Rocky Linux 8 did
RUN cd /opt && \
    curl -fsSL -o openssl.tar.gz \
        "https://github.com/openssl/openssl/releases/download/openssl-${OPENSSL_VERSION}/openssl-${OPENSSL_VERSION}.tar.gz" && \
    echo "${OPENSSL_SHA256}  openssl.tar.gz" | sha256sum -c - && \
    tar xzf openssl.tar.gz && cd "openssl-${OPENSSL_VERSION}" && \
    ./Configure linux-x86_64 shared no-tests \
        --prefix=/usr/local/openssl --libdir=lib --openssldir=/etc/pki/tls \
        -Wl,-rpath,/usr/local/openssl/lib && \
    make -j$(nproc) && \
    make install_sw && \
    echo "/usr/local/openssl/lib" > /etc/ld.so.conf.d/openssl.conf && \
    ldconfig && \
    cd /opt && rm -rf openssl.tar.gz "openssl-${OPENSSL_VERSION}"

RUN cd /opt && \
    curl -fsSL -o Python.tar.xz "https://www.python.org/ftp/python/${PYTHON_VERSION}/Python-${PYTHON_VERSION}.tar.xz" && \
    echo "${PYTHON_SHA256}  Python.tar.xz" | sha256sum -c - && \
    tar xJf Python.tar.xz && cd "Python-${PYTHON_VERSION}" && \
    ./configure --prefix=/usr/local --enable-shared \
        --with-openssl=/usr/local/openssl --with-openssl-rpath=auto \
        LDFLAGS="-Wl,-rpath,/usr/local/lib" && \
    make -j$(nproc) altinstall && \
    cd /opt && rm -rf Python.tar.xz "Python-${PYTHON_VERSION}"

RUN ln -s /usr/local/bin/python3.12 /usr/local/bin/python3 && \
    ln -s /usr/local/bin/pip3.12 /usr/local/bin/pip3 && \
    ln -s /usr/local/bin/pip3.12 /usr/local/bin/pip

# Fail the image build, if a module that the pipe CLI bundles was not built
RUN python3 -c "import ssl, hashlib, ctypes, lzma, bz2, zlib, sqlite3, uuid, readline; \
                print(ssl.OPENSSL_VERSION); \
                ssl.create_default_context(); \
                hashlib.sha256(b'').hexdigest()"

RUN pip3.12 install --upgrade pip setuptools wheel && \
    pip3.12 install pyinstaller==6.5.0
