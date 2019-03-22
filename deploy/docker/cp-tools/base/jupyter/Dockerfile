# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE=library/centos:7
FROM ${BASE_IMAGE}

# Common dependencies
RUN yum install -y wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   curl \
                   epel-release && \
    yum clean all && \
    curl https://bootstrap.pypa.io/get-pip.py | python - 

# Install anaconda
ENV ANACONDA_HOME=/opt/local/anaconda
ARG ANACONDA_VERSION="2-latest"
ARG INSTALL_TEMP="/tmp/"

ADD anaconda_install.sh anaconda_install_python.sh anaconda_install_r.sh $INSTALL_TEMP/
RUN mkdir -p $ANACONDA_HOME

RUN chmod +x $INSTALL_TEMP/*.sh && \
    $INSTALL_TEMP/anaconda_install.sh $ANACONDA_HOME $ANACONDA_VERSION

# Install python packages
RUN $INSTALL_TEMP/anaconda_install_python.sh $ANACONDA_HOME base

# Install r packages
RUN $INSTALL_TEMP/anaconda_install_r.sh $ANACONDA_HOME base

RUN rm -f $INSTALL_TEMP/*

COPY	start.sh /start.sh
RUN     chmod +x /start.sh
