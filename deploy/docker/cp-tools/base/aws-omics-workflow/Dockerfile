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

ARG BASE_IMAGE=library/centos:7.7.1908
FROM ${BASE_IMAGE}

RUN yum install -y git wget curl zip unzip gettext openssh-server

RUN cd /opt && curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && \
    ./aws/install && \
    rm -rf awscliv2.zip && \
    cd -

COPY omics-utils /opt/omics/utils

RUN cd /tmp && \
    git clone https://github.com/aws-samples/amazon-omics-tutorials && \
    mkdir -p /opt/omics/amazon && \
    mv amazon-omics-tutorials/utils/scripts/* /opt/omics/amazon && \
    rm -rf amazon-omics-tutorials && \
    cd -



