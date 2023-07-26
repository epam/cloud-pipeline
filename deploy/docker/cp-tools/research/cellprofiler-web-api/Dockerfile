# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM library/centos:7.7.1908

RUN yum install -y wget bzip2 \
                        gcc \
                        zlib-devel \
                        bzip2-devel \
                        xz-devel \
                        make \
                        ncurses-devel \
                        unzip \
                        git \
                        curl \
                        epel-release && \
    yum clean all && \
    curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

ARG PYTHON_3_DISTRIBUTION_URL="https://www.python.org/ftp/python/3.8.13/Python-3.8.13.tgz"

ENV CELLPROFILER_API_HOME=/opt/cellprofiler-api-wrapper

RUN yum install -y \
        make \
        autoconf \
        gtk3-devel \
        gcc-c++ \
        cmake \
        java-1.8.0-openjdk-devel \
        mysql-devel \
        libffi-devel \
        openssl-devel \
        openssh-server \
        libXtst-devel \
        libGLU-devel \
        sqlite-devel \
        which \
        libreoffice-writer \
        libreoffice-calc && \
    wget "$PYTHON_3_DISTRIBUTION_URL" -O python3.tgz  && \
        tar xzf python3.tgz && \
        cd Python-* && \
        ./configure --enable-optimizations --enable-shared && \
        make altinstall && \
        cd .. && \
        rm -rf Python-* && \
    mkdir -p "$CELLPROFILER_API_HOME"

ENV JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk"
ENV LD_LIBRARY_PATH=/usr/local/lib/:$LD_LIBRARY_PATH

ARG CELL_PROFILER_VERSION=4.2.1
ARG NUMPY_VERSION=1.23.1
ARG CYTHON_VERSION=0.29.30

COPY requirements.txt $CELLPROFILER_API_HOME/
RUN python3.8 -m pip install wheel && \
    wget "https://files.pythonhosted.org/packages/61/c7/e1a31b6a092a5b91952fe96801b2d3167fcb3bad8386c023dd83de4c4ab8/centrosome-1.2.0.tar.gz" -O /tmp/centrosome.tar.gz && \
    cd /tmp && \
    tar -zxf centrosome.tar.gz && \
    cd centrosome* && \
    sed -i "s/setup_requires=\[\"cython\", \"numpy\", \"pytest\",\],/setup_requires=\[\"cython==$CYTHON_VERSION\", \"numpy==$NUMPY_VERSION\", \"pytest\",\],/g" setup.py && \
    python3.8 -m pip install . && \
    rm -rf /tmp/centrosome*
RUN python3.8 -m pip install -r $CELLPROFILER_API_HOME/requirements.txt

RUN yum install -y \
        blosc \
        ImageMagick

ARG GENERATE_OFFSETS_VERSION=0.1.7
RUN python3.8 -m pip install generate-tiff-offsets==$GENERATE_OFFSETS_VERSION

ARG BIOFORMATS_TO_RAW_VERSION=0.7.0
ARG BIOFORMATS_TO_RAW_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION.zip"

ARG RAW_TO_OMETIFF_VERSION=0.4.1
ARG RAW_TO_OMETIFF_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION.zip"

ENV HCS_TOOLS_HOME="$CELLPROFILER_API_HOME/hcs-tools"

RUN mkdir -p "$HCS_TOOLS_HOME" && \
    wget -q "$BIOFORMATS_TO_RAW_DISTR_URL" -O "$HCS_TOOLS_HOME/bioformats2raw.zip" && \
    unzip "$HCS_TOOLS_HOME/bioformats2raw.zip" -d "$HCS_TOOLS_HOME/bioformats2raw" && \
    rm -f "$HCS_TOOLS_HOME/bioformats2raw.zip" && \
    chmod +x "$HCS_TOOLS_HOME/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION/bin/bioformats2raw" && \
    wget -q "$RAW_TO_OMETIFF_DISTR_URL" -O "$HCS_TOOLS_HOME/raw2ometiff.zip" && \
    unzip "$HCS_TOOLS_HOME/raw2ometiff.zip" -d "$HCS_TOOLS_HOME/raw2ometiff" && \
    rm -f "$HCS_TOOLS_HOME/raw2ometiff.zip" && \
    chmod +x "$HCS_TOOLS_HOME/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION/bin/raw2ometiff"

ENV PATH="$HCS_TOOLS_HOME/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION/bin:$HCS_TOOLS_HOME/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION/bin:$PATH"

COPY convert_to_ome_tiff.sh "$HCS_TOOLS_HOME/convert_to_ome_tiff.sh"
RUN chmod +x $HCS_TOOLS_HOME/convert_to_ome_tiff.sh

COPY app/ $CELLPROFILER_API_HOME/
COPY start.sh $CELLPROFILER_API_HOME/
RUN chmod +x $CELLPROFILER_API_HOME/start.sh

CMD ["/opt/cellprofiler-api-wrapper/start.sh"]
