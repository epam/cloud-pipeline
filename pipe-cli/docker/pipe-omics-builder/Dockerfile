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

FROM centos:7

# How to install python 3.10 on centos:7
# https://github.com/pyenv/pyenv/issues/2416
# https://linuxstans.com/how-to-install-python-centos/

RUN yum update -y && \
 yum groupinstall "Development Tools" -y && \
 yum install epel-release -y && \
 yum install -y wget openssl11 openssl11-devel libffi-devel libffi-devel && \
 yum clean all

RUN mkdir /usr/local/openssl11 && \
    ln -s /usr/lib64/openssl11 /usr/local/openssl11/lib && \
    ln -s /usr/include/openssl11 /usr/local/openssl11/include

RUN cd /opt && \
    wget https://www.python.org/ftp/python/3.10.11/Python-3.10.11.tgz && \
    tar xzf Python-3.10.11.tgz && cd Python-3.10.11 && \
    ./configure --enable-optimizations --with-openssl=/usr/local/openssl11 --prefix=/usr/local --enable-shared LDFLAGS="-Wl,-rpath /usr/local/lib" && \
    make altinstall && \
    rm -rf Python-3.10.11.tgz Python-3.10.11

RUN python3.10 -m pip install pyinstaller==6.5.0
