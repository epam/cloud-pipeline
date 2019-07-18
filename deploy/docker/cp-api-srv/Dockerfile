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

# docker build . \
#   -t lifescience/cloud-pipeline:api-srv-latest \
#   --build-arg CP_API_DIST_URL="https://..." \
#   --build-arg OPENSSL_DIST_URL="https://..." \
#   --build-arg OSSLSIGNCODE_DIST_URL="https://..."

FROM centos:7

# Prerequisites
RUN yum install -y \
                wget \
                curl \
                java-1.8.0-openjdk \
                python \
                zip \
                unzip \
                git \
                nfs-utils \
                sudo \
                samba \
                samba-client \
                cifs-utils \
                gettext

ARG OPENSSL_DIST_URL=""
RUN wget $OPENSSL_DIST_URL -O openssl.tgz && \
    tar -xzf openssl.tgz -C /opt/ && \
    rm -f openssl.tgz

ENV LD_LIBRARY_PATH=/opt/openssl/lib:$LD_LIBRARY_PATH
ENV PATH=/opt/openssl/bin:$PATH

ARG OSSLSIGNCODE_DIST_URL=""
RUN wget $OSSLSIGNCODE_DIST_URL -O osslsigncode.tgz && \
    tar -xzf osslsigncode.tgz -C /usr/local/bin/ && \
    rm -f osslsigncode.tgz

RUN curl https://bootstrap.pypa.io/get-pip.py | python && \
    pip install -I  requests==2.21.0 \
                    ipaddress==1.0.22 \
                    pykube==0.15.0 \
                    psycopg2==2.7.7 \
                    sqlalchemy==1.3.2 \
                    luigi==2.8.3 \
                    cryptography==2.6.1

# AWS - specific packages
RUN pip install -I awscli==1.16.139 \
                    boto3==1.9.129

# Azure - specific packages
RUN pip install -I  azure-common==1.1.18 \
                    azure==4.0.0 \
                    azure-mgmt-resource==2.1.0 \
                    azure-mgmt-compute==4.5.1 \
                    azure-mgmt-containerinstance==1.4.1 \
                    azure-cli-core==2.0.61

# GCP - specific packages
RUN pip install -I google-api-python-client

# API distribution
ARG CP_API_DIST_URL=""
ENV CP_API_HOME="/opt/api"
RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    rm -rf bin/pipe-templates/__SYSTEM && \
    mkdir -p $CP_API_HOME && \
    mv bin/* $CP_API_HOME/ && \
    rm -f cloud-pipeline.tgz && \
    rm -rf bin
ADD config $CP_API_HOME/config

# Install pipe-common package
RUN mkdir -p /tmp/pipe-temp && \
    cp /opt/api/pipeline.jar /tmp/pipe-temp/ && \
    cd /tmp/pipe-temp/ && \
    unzip -qq pipeline.jar && \
    pip install -I /tmp/pipe-temp/BOOT-INF/classes/static/pipe-common.tar.gz && \
    cd / && \
    rm -rf /tmp/pipe-temp

# Install projects templates
ADD folder-templates /opt/api/folder-templates

# Add initialization scripts (api jar and git-sync)
ADD init-api /init-api
ADD init-git-sync /init-git-sync
ADD update-trust /update-trust

RUN chmod +x /init* && \
    chmod +x /update-trust

WORKDIR /opt/api

CMD ["/init-api"]
