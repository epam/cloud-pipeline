# Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

RUN sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/mirror.centos.org/vault.centos.org/g' /etc/yum.repos.d/*.repo

ENV LANG="en_US.UTF-8"
ENV LANGUAGE=en_US

# Install common dependencies
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
                        unzip \
                        bzip2

ENV WSI_PARSER_HOME=/opt/local/wsi-parser

ARG BFTOOLS_VERSION="6.8.0"
ARG VIPS_VERSION="8.11.2"

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

ENV ANACONDA_HOME=/opt/local/anaconda
RUN mkdir -p $ANACONDA_HOME && \
    setfacl -d -m g::rwx "$ANACONDA_HOME" && \
    cd $ANACONDA_HOME && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    echo "export PATH=$PATH:$ANACONDA_HOME/bin" > $ANACONDA_HOME/profile.sh && \
    echo "export MAMBA_ROOT_PREFIX=$ANACONDA_HOME" >> $ANACONDA_HOME/profile.sh && \
    echo 'eval "$(micromamba shell hook --shell bash)"' >> $ANACONDA_HOME/profile.sh

RUN source $ANACONDA_HOME/profile.sh && \
    micromamba create -y -n parser && \
    micromamba activate parser && \
    micromamba install -y python==3.12.9
    yum install -y epel-release && \
    yum clean all && \
    yum install -y blosc \
                   ImageMagick \
                   java-1.8.0-openjdk-devel && \
    pip3 install generate-tiff-offsets==0.1.7 numpy==2.2.3 && \
    ln -s $ANACONDA_HOME/envs/parser/bin/generate_tiff_offsets /usr/local/bin/generate_tiff_offsets

RUN source $ANACONDA_HOME/profile.sh && \
    micromamba create -y -n indica_tiff_parser && \
    micromamba activate indica_tiff_parser && \
    micromamba install python==3.13.2 -y && \
    pip3 install tifffile[all]==2025.2.18

# Install AWS CLI
ARG AWS_CLI_DISTR_URL="https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip"
RUN cd $PARSER_HOME && \
    wget "$AWS_CLI_DISTR_URL" -O "awscliv2.zip" && \
    unzip awscliv2.zip && \
    ./aws/install -i /usr/local/aws-cli -b /usr/local/bin && \
    rm -f awscliv2.zip

ARG BIOFORMATS_TO_RAW_VERSION=0.6.1
ARG BIOFORMATS_TO_RAW_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION.zip"

ARG RAW_TO_OMETIFF_VERSION=0.4.1
ARG RAW_TO_OMETIFF_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION.zip"

RUN mkdir -p "$WSI_PARSER_HOME" && \
    wget -q "$BIOFORMATS_TO_RAW_DISTR_URL" -O "$WSI_PARSER_HOME/bioformats2raw.zip" && \
    unzip "$WSI_PARSER_HOME/bioformats2raw.zip" -d "$WSI_PARSER_HOME/bioformats2raw" && \
    rm -f "$WSI_PARSER_HOME/bioformats2raw.zip" && \
    chmod +x "$WSI_PARSER_HOME/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION/bin/bioformats2raw" && \
    wget -q "$RAW_TO_OMETIFF_DISTR_URL" -O "$WSI_PARSER_HOME/raw2ometiff.zip" && \
    unzip "$WSI_PARSER_HOME/raw2ometiff.zip" -d "$WSI_PARSER_HOME/raw2ometiff" && \
    rm -f "$WSI_PARSER_HOME/raw2ometiff.zip" && \
    chmod +x "$WSI_PARSER_HOME/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION/bin/raw2ometiff"

ENV PATH="$WSI_PARSER_HOME/bftools:$WSI_PARSER_HOME/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION/bin:$WSI_PARSER_HOME/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION/bin:/usr/local/bin:$PATH"

ADD create_deepzoom.sh "$WSI_PARSER_HOME/create_deepzoom.sh"
ADD create_label.sh "$WSI_PARSER_HOME/create_label.sh"
ADD create_pyramid_ome_tiff.sh "$WSI_PARSER_HOME/create_pyramid_ome_tiff.sh"
ADD indica_tiff_to_ome_tiff.py "$WSI_PARSER_HOME/indica_tiff_to_ome_tiff.py"
ADD process_files.py "$WSI_PARSER_HOME/process_files.py"
ADD start.sh /start.sh
RUN chmod +x $WSI_PARSER_HOME/*.sh
