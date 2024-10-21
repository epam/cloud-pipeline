# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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

# Configure cloud-pipeline yum repository
RUN curl -sk "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/repos/centos/7/cloud-pipeline.repo" > /etc/yum.repos.d/cloud-pipeline.repo && \
    yum --disablerepo=* --enablerepo=cloud-pipeline install yum-priorities -y && \
    yum-config-manager --save --setopt=\*.skip_if_unavailable=true && \
    sed -i 's/enabled=1/enabled=0/g' /etc/yum/pluginconf.d/fastestmirror.conf && \
    sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo

RUN yum update -y && \
    yum install -y git wget curl zip unzip gettext openssh-server python3 && \
    python3 -m pip install boto3

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

ENV LC_ALL=en_US.UTF-8
