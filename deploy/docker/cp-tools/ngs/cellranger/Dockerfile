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
    curl https://bootstrap.pypa.io/2.7/get-pip.py | python -

ENV CELLRANGER_HOME="/opt/cellranger"
ENV CELLRANGER_BIN="$CELLRANGER_HOME/cellranger-latest/cellranger"
ENV PATH "$PATH:/$CELLRANGER_HOME/cellranger-latest"

# Configure SGE integration
ADD sge.template /tmp/sge.template

# Install cellranger
ADD install_cr_versions.sh /tmp/install_cr_versions.sh
ARG CELLRANGER_URL=""
RUN bash /tmp/install_cr_versions.sh "$CELLRANGER_URL" "$CELLRANGER_HOME" && \
    rm -f /tmp/install_cr_versions.sh

ENV BCL2FASTQ_HOME="/opt/bcl2fastq"
# Install bcl2fastq 2, if required
ADD install_bcl2fastq2.sh /tmp/install_bcl2fastq2.sh
ADD Xml.cpp.patch /tmp/Xml.cpp.patch
ARG WITH_BCL2FASTQ2="false"
RUN bash /tmp/install_bcl2fastq2.sh "$WITH_BCL2FASTQ2" && \
    rm -f /tmp/install_bcl2fastq2.sh

# Install bcl2fastq 1.8, if required
ADD install_bcl2fastq18.sh /tmp/install_bcl2fastq18.sh
ARG WITH_BCL2FASTQ18="false"
RUN bash /tmp/install_bcl2fastq18.sh "$WITH_BCL2FASTQ18" && \
    rm -f /tmp/install_bcl2fastq18.sh

WORKDIR $CELLRANGER_HOME
