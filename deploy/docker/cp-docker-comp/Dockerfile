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

FROM centos:7

# Prerequisites
RUN yum install -y \
                wget \
                curl \
                java-1.8.0-openjdk \
                python \
                unzip

# API distribution
ENV CP_DOCKER_COMP_HOME="/opt/docker-comp"

# Rely on the cloud-pipeline.tgz file is present in the docker build context directory
# While we can download tarball from the public repo - it takes time. This implementation allows to download once for all the dependant docker images
COPY cloud-pipeline.tgz /tmp/cloud-pipeline.tgz
RUN cd /tmp && \
    tar -zxf cloud-pipeline.tgz && \
    mkdir -p $CP_DOCKER_COMP_HOME && \
    mv bin/docker-comp-scan.jar $CP_DOCKER_COMP_HOME/ && \
    rm -rf /tmp/*

ADD config $CP_DOCKER_COMP_HOME/config

ADD init /init
RUN chmod +x /init

CMD ["/init"]
