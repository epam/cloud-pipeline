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
ARG BFTOOLS_VERSION="5.8.2"
ARG VIPS_VERSION="8.11.2"
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
    make install

ENV PATH="$WSI_PARSER_HOME/bftools:$PATH"
ADD create_deepzoom.sh "$WSI_PARSER_HOME/create_deepzoom.sh"
ADD process_files.py "$WSI_PARSER_HOME/process_files.py"
