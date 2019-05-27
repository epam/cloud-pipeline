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
#   -t lifescience/cloud-pipeline:share-srv-latest \
#   --build-arg CP_API_DIST_URL="https://..."

FROM centos:7

# Prerequisites
RUN yum install -y \
                wget \
                curl \
                java-1.8.0-openjdk \
                python \
                unzip \
                openssl


# SHARE distribution
ARG CP_SHARE_DIST_URL=""
ENV CP_SHARE_HOME="/opt/share-srv"

RUN cd /tmp && \
     wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
     tar -zxf cloud-pipeline.tgz && \
     mkdir -p $CP_SHARE_HOME && \
     mv bin/data-sharing-service.jar $CP_SHARE_HOME/ && \
     rm -f cloud-pipeline.tgz && \
     rm -rf bin

ADD config $CP_SHARE_HOME/config
ADD init /init
ADD update-trust /update-trust

RUN chmod +x /init* && \
    chmod +x /update-trust

WORKDIR /opt/share-srv

CMD ["/init"]
