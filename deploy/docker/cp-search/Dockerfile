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

# Prerequisites
# yum-utils: package-cleanup for the Lustre client
RUN dnf install -y \
                wget \
                java-1.8.0-openjdk \
                unzip \
                sudo \
                yum-utils \
                nfs-utils \
                samba \
                samba-client \
                cifs-utils

ARG LUSTRE_VERSION="2.10.8-2.el8.x86_64"
ARG LUSTRE_CLIENT_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-$LUSTRE_VERSION.tar.gz"
RUN cd /tmp && \
    wget -q "$LUSTRE_CLIENT_URL" -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    dnf install -y lustre-client/*.rpm && \
    package-cleanup --cleandupes -y && \
    rm -rf lustre-client*

# API distribution
ARG CP_API_DIST_URL=""
ENV CP_SEARCH_HOME="/opt/search" \
    CP_SEARCH_MAPPINGS_LOCATION="$CP_SEARCH_HOME/search-mappings"
RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    mkdir -p $CP_SEARCH_HOME && \
    mv bin/elasticsearch-agent.jar $CP_SEARCH_HOME/ && \
    mv bin/search-mappings $CP_SEARCH_MAPPINGS_LOCATION && \
    rm -rf /tmp/*

ADD config $CP_SEARCH_HOME/config

ADD init /init
RUN chmod +x /init

CMD ["/init"]
