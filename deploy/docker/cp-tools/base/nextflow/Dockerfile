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

# Common applications
RUN yum install -y wget \
                   git \
                   curl \
                   epel-release \
                   java-1.8.0-openjdk \
                   python \
                   which && \
    yum clean all && \
    curl https://bootstrap.pypa.io/get-pip.py | python -

# Stick to a specific version, as the installation script at get.nextflow.io may be upgraded without a notification
# NXF_VER will be considered in the installation script
ENV NXF_VER="19.04.1"
ENV NXF_HOME_ROOT="/opt/nextflow"
ENV NXF_HOME="$NXF_HOME_ROOT/$NXF_VER"

# Always use sge setup to leverge multi-node autoscaled capabilities
ENV CP_CAP_SGE="true"

# Install nextflow itlself into $NEXTFLOW_HOME
RUN mkdir -p $NEXTFLOW_HOME && \
    cd $NXF_HOME_ROOT && \
    curl -fsSL get.nextflow.io | bash

ADD default.config /etc/nextflow/default.config

ADD nextflow /usr/bin/nextflow
RUN chmod +x /usr/bin/nextflow
