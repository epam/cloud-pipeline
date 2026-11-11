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

# docker build . \
#   -t lifescience/cloud-pipeline:api-srv-latest \
#   --build-arg CP_API_DIST_URL="https://..." \
#   --build-arg OPENSSL_DIST_URL="https://..." \
#   --build-arg OSSLSIGNCODE_DIST_URL="https://..."

FROM rockylinux/rockylinux:9.8

# Prerequisites
# libxcrypt-compat provides libcrypt.so.1, which the crypt module of the bundled Python 3.12 links
RUN dnf install -y \
                wget \
                java-1.8.0-openjdk \
                zip \
                unzip \
                openssl \
                git \
                nfs-utils \
                sudo \
                samba \
                samba-client \
                cifs-utils \
                gettext \
                libxcrypt-compat

ARG LUSTRE_VERSION="2.10.8-2.el8.x86_64"
ARG LUSTRE_CLIENT_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-$LUSTRE_VERSION.tar.gz"
# yum-utils provides package-cleanup
RUN cd /tmp && \
    dnf install nfs-utils cifs-utils yum-utils -y && \
    wget -q $LUSTRE_CLIENT_URL -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    dnf install -y lustre-client/*.rpm && \
    package-cleanup --cleandupes -y && \
    rm -rf lustre-client*

# Prebuild version of openssl v1.1.x which is required by osslsigncode.
# As long as its compatibility is proven for osslsigncode tool only
# openssl v3.x installed with package manager will be used by default.
ARG OPENSSL_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/openssl/openssl-1.1.1c.tar.gz"
RUN wget $OPENSSL_DIST_URL -O openssl.tgz && \
    tar -xzf openssl.tgz -C /opt/ && \
    rm -f openssl.tgz

ARG OSSLSIGNCODE_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/osslsigncode/osslsigncode-2.0.tar.gz"
RUN wget $OSSLSIGNCODE_DIST_URL -O osslsigncode.tgz && \
    tar -xzf osslsigncode.tgz -C /usr/local/bin/ && \
    rm -f osslsigncode.tgz

# Python 3.12, the only Python of this image: the build a job container installs (scripts/pipeline-launch/launch.sh).
# /usr/bin/python3 stays the system one, for dnf.
ARG CP_PYTHON3_DIST_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/python/3/python3.12.tar.gz"
RUN wget -q $CP_PYTHON3_DIST_URL -O python3.tgz && \
    tar -xzf python3.tgz -C /usr/local/ && \
    rm -f python3.tgz && \
    ln -sf /usr/local/python3.12/bin/python3.12 /usr/local/bin/python3 && \
    ln -sf /usr/local/python3.12/bin/pip3.12 /usr/local/bin/pip3

# AWS - specific packages
RUN pip3 install -I  boto3==1.34.69 \
                     botocore==1.34.69

# Azure - specific packages. azure-mgmt-* stay on the msrestazure-based generation: current (track2, azure-core-based)
# releases of azure-mgmt-resource/compute/network dropped get_client_from_auth_file()/get_client_from_cli_profile()
# and raise azure.core exceptions, not msrestazure's CloudError - both of which scripts/autoscaling/azure/*.py and
# azureprovider.py depend on.
# azure-cli-core serves a deployment that is installed with the Azure CLI login files (azureProfile.json and
# accessTokens.json, see init-api) instead of an auth file: the node scripts then call get_client_from_cli_profile().
# 2.29.2 is the last release that reads accessTokens.json. It needs the "distutils" module, which Python 3.12 has
# no more: setuptools provides it (pipe-common installs setuptools below).
RUN pip3 install -I  azure-common==1.1.18 \
                     azure-mgmt-resource==2.0.0 \
                     azure-mgmt-compute==4.5.1 \
                     azure-mgmt-network==2.0.0 \
                     azure-cli-core==2.29.2

# GCP - specific packages
RUN pip3 install -I  google-api-core==1.32.0 \
                     google-api-python-client==1.12.11 \
                     google-auth==1.35.0 \
                     google-auth-httplib2==0.1.0 \
                     googleapis-common-protos==1.52.0

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
    pip3 install -I /tmp/pipe-temp/BOOT-INF/classes/static/pipe-common.tar.gz && \
    cd / && \
    rm -rf /tmp/pipe-temp

# Since 2.35.2, git refuses to work in a repository owned by another user. The API runs git as root
# in the folders of the mounted storages (the pipeline.local.path.transfer.script), which may belong to the storage user
RUN git config --system --add safe.directory '*'

# Install projects templates
ADD folder-templates /opt/api/folder-templates

# Install error-pages templates
ADD error-pages /opt/api/static/error

# Add initialization scripts (api jar and git-sync)
ADD init-api /init-api
ADD init-git-sync /init-git-sync
ADD update-trust /update-trust
ADD liveness-git-sync.sh /liveness-git-sync.sh

RUN chmod +x /init* && \
    chmod +x /update-trust && \
    chmod +x /liveness-git-sync.sh

RUN wget -q https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/filebeat/filebeat-6.8.3-x86_64.rpm \
    && rpm -vi filebeat-6.8.3-x86_64.rpm && rm -rf filebeat-6.8.3-x86_64.rpm

COPY filebeat-template.yml /etc/filebeat/

WORKDIR /opt/api

CMD ["/init-api"]
