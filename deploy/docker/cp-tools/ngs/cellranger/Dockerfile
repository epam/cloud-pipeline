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

ARG BASE_IMAGE=library/ubuntu:16.04
FROM ${BASE_IMAGE}

# Install common
RUN apt-get update -y && \
    apt-get install -y wget \
                   curl \
                   python \
                   git && \
    curl https://bootstrap.pypa.io/get-pip.py | python -

ENV CELLRANGER_HOME="/opt/cellranger"
ENV CELLRANGER_BIN="$CELLRANGER_HOME/cellranger"

# Configure SGE integration
ADD sge.template /tmp/sge.template

# Install cellranger
ARG CELLRANGER_URL=""
RUN     if [ -z "${CELLRANGER_URL}" ]; then echo "CELLRANGER_URL is not set. Specify it using \"--build-arg CELLRANGER_URL=http://\". Valid temporary distr URL can be obtained from https://support.10xgenomics.com/single-cell-gene-expression/software/downloads/latest" exit 1; fi && \
        cd /tmp && \
        wget -q -O cellranger.tar.gz "${CELLRANGER_URL}" && \
        tar -zxf cellranger.tar.gz && \
        rm -rf cellranger.tar.gz && \
        cp /tmp/sge.template cellranger-*/martian-cs/v*/jobmanagers/ && \
        mv cellranger-* $CELLRANGER_HOME ; \
        rm -f /tmp/sge.template && \
        chmod 0777 -R "$CELLRANGER_HOME"

WORKDIR $CELLRANGER_HOME
