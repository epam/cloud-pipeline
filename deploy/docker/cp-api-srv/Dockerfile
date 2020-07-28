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
                openssl \
                git \
                nfs-utils \
                sudo \
                samba \
                samba-client \
                cifs-utils \
                gettext

ARG AMAZON_FSX_PUBLIC_KEY_URL="https://fsx-lustre-client-repo-public-keys.s3.amazonaws.com/fsx-rpm-public-key.asc"
ARG LUSTRE_CLIENT_REPO_URL="https://fsx-lustre-client-repo.s3.amazonaws.com/el/7/fsx-lustre-client.repo"
RUN wget $AMAZON_FSX_PUBLIC_KEY_URL -O /tmp/fsx-rpm-public-key.asc && \
    rpm --import /tmp/fsx-rpm-public-key.asc && \
    wget $LUSTRE_CLIENT_REPO_URL -O /etc/yum.repos.d/aws-fsx.repo && \
    yum install -y kmod-lustre-client lustre-client && \
    rm -f /tmp/fsx-rpm-public-key.asc /etc/yum.repos.d/aws-fsx.repo

# Prebuild version of openssl v1.1.x which is required by osslsigncode.
# As long as its compatibility is proven for osslsigncode tool only
# openssl v1.0.x installed with package manager will be used by default.
ARG OPENSSL_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/openssl/openssl-1.1.1c.tar.gz"
RUN wget $OPENSSL_DIST_URL -O openssl.tgz && \
    tar -xzf openssl.tgz -C /opt/ && \
    rm -f openssl.tgz

ARG OSSLSIGNCODE_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/osslsigncode/osslsigncode-2.0.tar.gz"
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
RUN pip install -I  awscli==1.16.314 \
                    boto3==1.10.50 \
                    botocore==1.13.50

# Azure - specific packages
RUN pip install -I  azure-common==1.1.18 \
                    azure==4.0.0 \
                    azure-mgmt-resource==2.0.0 \
                    azure-mgmt-compute==4.5.1 \
                    azure-mgmt-containerinstance==1.4.1 \
                    azure-cli-core==2.0.52

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

# Install error-pages templates
ADD error-pages /opt/api/static/error

# Add initialization scripts (api jar and git-sync)
ADD init-api /init-api
ADD init-git-sync /init-git-sync
ADD update-trust /update-trust

RUN chmod +x /init* && \
    chmod +x /update-trust

RUN wget -q https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/filebeat/filebeat-6.8.3-x86_64.rpm \
    && rpm -vi filebeat-6.8.3-x86_64.rpm && rm -rf filebeat-6.8.3-x86_64.rpm

COPY filebeat-template.yml /etc/filebeat/

WORKDIR /opt/api

CMD ["/init-api"]
