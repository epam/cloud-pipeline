# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM rockylinux:8

# Build Python 3.12 from source on Rocky Linux 8 (glibc 2.28) to produce
# binaries compatible with RHEL/Rocky/Alma 8+, Ubuntu 20.04+, Debian 11+.

RUN dnf update -y && \
    dnf groupinstall "Development Tools" -y && \
    dnf install -y wget openssl-devel libffi-devel bzip2-devel \
        xz-devel sqlite-devel readline-devel zlib-devel ncurses-devel \
        tk-devel gdbm-devel libuuid-devel fuse fuse-devel git && \
    dnf clean all

RUN cd /opt && \
    wget https://www.python.org/ftp/python/3.12.4/Python-3.12.4.tgz && \
    tar xzf Python-3.12.4.tgz && cd Python-3.12.4 && \
    ./configure --prefix=/usr/local --enable-shared \
        LDFLAGS="-Wl,-rpath /usr/local/lib" && \
    make -j$(nproc) altinstall && \
    rm -rf /opt/Python-3.12.4.tgz /opt/Python-3.12.4

RUN ln -s /usr/local/bin/python3.12 /usr/local/bin/python3 && \
    ln -s /usr/local/bin/pip3.12 /usr/local/bin/pip3 && \
    ln -s /usr/local/bin/pip3.12 /usr/local/bin/pip

RUN pip3.12 install --upgrade pip setuptools wheel && \
    pip3.12 install pyinstaller==6.5.0
