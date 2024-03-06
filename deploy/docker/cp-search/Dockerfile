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

ARG BASE_IMAGE="centos:7"
FROM $BASE_IMAGE

# Prerequisites
RUN yum install -y \
                wget \
                curl \
                java-1.8.0-openjdk \
                python \
                unzip \
                sudo \
                nfs-utils \
                samba \
                samba-client \
                cifs-utils

ARG LUSTRE_VERSION="2.12.5-1.el7.x86_64"
ARG LUSTRE_CLIENT_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-$LUSTRE_VERSION.tar.gz"
RUN cd /tmp && \
    wget -q https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/lustre/client/rpm/lustre-client-2.12.5-1.el7.x86_64.tar.gz -O lustre-client.tar.gz && \
    mkdir -p lustre-client && \
    tar -xzvf lustre-client.tar.gz -C lustre-client/ && \
    rpm -i --justdb --quiet --nodeps --force lustre-client/dependencies/*.rpm && \
    yum install -y lustre-client/*.rpm && \
    package-cleanup --cleandupes -y && \
    rm -rf lustre-client*

ENV CP_SEARCH_HOME="/opt/search"
ENV CP_SEARCH_MAPPINGS_LOCATION="$CP_SEARCH_HOME/search-mappings"

ADD dist/bin/elasticsearch-agent.jar $CP_SEARCH_HOME/elasticsearch-agent.jar
ADD dist/bin/search-mappings $CP_SEARCH_MAPPINGS_LOCATION

ADD config $CP_SEARCH_HOME/config

ADD init /init
RUN chmod +x /init

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

CMD ["/init"]
