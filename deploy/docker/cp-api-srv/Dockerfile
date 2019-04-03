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
#   --build-arg CP_API_DIST_URL="https://..."

FROM centos:7

# Prerequisites
RUN yum install -y \
                wget \
                curl \
                java-1.8.0-openjdk \
                python \
                unzip \
                openssl \
                git \
                nfs-utils \
                sudo \
                samba \
                samba-client \
                cifs-utils \
                gettext

RUN curl https://bootstrap.pypa.io/get-pip.py | python && \
    pip install -I  requests \
                    ipaddress \
                    pykube \
                    psycopg2 \
                    sqlalchemy \
                    luigi

# AWS - specific packages
RUN pip install -I awscli \
                    boto3

# Azure - specific packages
RUN pip install -I  azure-common \
                    azure azure-mgmt-resource \
                    azure-mgmt-compute \
                    azure-mgmt-containerinstance \
                    azure-cli-core

# API distribution
ENV CP_API_HOME="/opt/api"

# Rely on the cloud-pipeline.tgz file is present in the docker build context directory
# While we can download tarball from the public repo - it takes time. This implementation allows to download once for all the dependant docker images
ADD cloud-pipeline.tgz /tmp/cloud-pipeline.tgz
RUN cd /tmp && \
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
