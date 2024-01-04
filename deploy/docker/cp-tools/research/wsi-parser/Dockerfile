# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE="library/centos:7"
FROM $BASE_IMAGE

RUN  yum install -y -q  gcc \
                        gcc-c++ \
                        make \
                        java-11-openjdk-headless \
                        ImageMagick \
                        glib2-devel \
                        expat-devel \
                        libtiff-devel \
                        libjpeg-turbo-devel \
                        libgsf-devel \
                        libpng-devel \
                        pkgconfig \
                        perl-XML-XPath \
                        wget \
                        unzip

# Install tools
ARG BFTOOLS_VERSION="6.8.0"
ARG VIPS_VERSION="8.11.2"
ARG AWS_CLI_DISTR_URL="https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip"
ENV WSI_PARSER_HOME=/opt/local/wsi-parser

RUN mkdir -p $WSI_PARSER_HOME && \
    wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bftools/bftools-cli-$BFTOOLS_VERSION.zip" \
                    -O "$WSI_PARSER_HOME/bftools-cli.zip" && \
    unzip "$WSI_PARSER_HOME/bftools-cli.zip" -d "$WSI_PARSER_HOME" && \
    rm -f "$WSI_PARSER_HOME/bftools-cli.zip" && \
    wget "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/libvips/vips-src-$VIPS_VERSION.tar.gz" \
                    -O "$WSI_PARSER_HOME/vips.tar.gz" && \
    tar -xzf "$WSI_PARSER_HOME/vips.tar.gz" -C "$WSI_PARSER_HOME" && \
    rm -f "$WSI_PARSER_HOME/vips.tar.gz" && \
    cd "$WSI_PARSER_HOME/vips-$VIPS_VERSION" && \
    ./configure && \
    make && \
    make install && \
    wget "$AWS_CLI_DISTR_URL" -O "awscliv2.zip" && \
    unzip awscliv2.zip && \
    ./aws/install -i /usr/local/aws-cli -b /usr/local/bin && \
    rm -f awscliv2.zip

ARG ANACONDA_VERSION="3-latest"
ENV ANACONDA_HOME=/opt/local/anaconda
ENV PATH="$PATH:$ANACONDA_HOME/bin"

ARG CP_PIP_EXTRA_ARGS="--index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com"
RUN mkdir -p "$ANACONDA_HOME" && \
    setfacl -d -m g::rwx "$ANACONDA_HOME" && \
    wget -q "https://repo.anaconda.com/miniconda/Miniconda${ANACONDA_VERSION}-Linux-x86_64.sh" -O /tmp/Anaconda_Install.sh && \
    bash /tmp/Anaconda_Install.sh -f -b -p "$ANACONDA_HOME" && \
    rm -f /tmp/Anaconda_Install.sh && \
    conda init bash && \
    source $ANACONDA_HOME/etc/profile.d/conda.sh && \
    conda create -y -n wsi-parser && \
    conda activate wsi-parser && \
    pip3 install $CP_PIP_EXTRA_ARGS -I -q generate-tiff-offsets==0.1.7 && \
    conda deactivate

ENV LANG="en_US.UTF-8"
ENV LANGUAGE=en_US

ENV TOOLS_HOME="$WSI_PARSER_HOME"/tools

ARG BIOFORMATS_TO_RAW_VERSION=0.4.0
ARG BIOFORMATS_TO_RAW_DISTR_URL=https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bioformats2raw/bioformats2raw-0.4.0.zip

ARG RAW_TO_OMETIFF_VERSION=0.3.0
ARG RAW_TO_OMETIFF_DISTR_URL=https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/raw2ometiff/raw2ometiff-0.3.0.zip

RUN mkdir -p "$TOOLS_HOME" && \
    wget -q "$BIOFORMATS_TO_RAW_DISTR_URL" -O "$TOOLS_HOME/bioformats2raw.zip" && \
    unzip "$TOOLS_HOME/bioformats2raw.zip" -d "$TOOLS_HOME/bioformats2raw" && \
    rm -f "$TOOLS_HOME/bioformats2raw.zip" && \
    chmod +x "$TOOLS_HOME/bioformats2raw/bin/bioformats2raw" && \
    wget -q "$RAW_TO_OMETIFF_DISTR_URL" -O "$TOOLS_HOME/raw2ometiff.zip" && \
    unzip "$TOOLS_HOME/raw2ometiff.zip" -d "$TOOLS_HOME/raw2ometiff" && \
    rm -f "$TOOLS_HOME/raw2ometiff.zip" && \
    chmod +x "$TOOLS_HOME/raw2ometiff/bin/raw2ometiff"

ENV PATH="$WSI_PARSER_HOME/bftools:$TOOLS_HOME/bioformats2raw/bin:$TOOLS_HOME/raw2ometiff/bin:/usr/local/bin:$PATH"

ADD create_deepzoom.sh "$WSI_PARSER_HOME/create_deepzoom.sh"
ADD create_pyramid_ome_tiff.sh "$WSI_PARSER_HOME/create_pyramid_ome_tiff.sh"
ADD process_files.py "$WSI_PARSER_HOME/process_files.py"
ADD start.sh /start.sh
RUN chmod +x $WSI_PARSER_HOME/*.sh

ENTRYPOINT ["python", "/opt/local/wsi-parser/process_files.py"]
