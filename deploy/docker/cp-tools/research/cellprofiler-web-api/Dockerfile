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
        blosc \
        ImageMagick \
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
ARG GENERATE_OFFSETS_VERSION=0.1.7

COPY requirements.txt $CELLPROFILER_API_HOME/
RUN python3.8 -m pip install wheel numpy==$NUMPY_VERSION \
                                   Cython==$CYTHON_VERSION && \
    python3.8 -m pip install cellprofiler==$CELL_PROFILER_VERSION && \
    python3.8 -m pip install -r $CELLPROFILER_API_HOME/requirements.txt && \
    python3.8 -m pip install generate-tiff-offsets==$GENERATE_OFFSETS_VERSION

ARG BIOFORMATS_TO_RAW_VERSION=0.6.1
ARG BIOFORMATS_TO_RAW_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION.zip"

ARG RAW_TO_OMETIFF_VERSION=0.4.1
ARG RAW_TO_OMETIFF_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION.zip"

ARG BFTOOLS_VERSION=6.13.0
ARG BFTOOLS_DISTR_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/bftools/bftools-cli-$BFTOOLS_VERSION.zip"

ENV HCS_TOOLS_HOME="/opt/local/hcs-tools"

RUN mkdir -p "$HCS_TOOLS_HOME" && \
    wget -q "$BIOFORMATS_TO_RAW_DISTR_URL" -O "$HCS_TOOLS_HOME/bioformats2raw.zip" && \
    unzip "$HCS_TOOLS_HOME/bioformats2raw.zip" -d "$HCS_TOOLS_HOME/bioformats2raw" && \
    rm -f "$HCS_TOOLS_HOME/bioformats2raw.zip" && \
    chmod +x "$HCS_TOOLS_HOME/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION/bin/bioformats2raw" && \
    wget -q "$RAW_TO_OMETIFF_DISTR_URL" -O "$HCS_TOOLS_HOME/raw2ometiff.zip" && \
    unzip "$HCS_TOOLS_HOME/raw2ometiff.zip" -d "$HCS_TOOLS_HOME/raw2ometiff" && \
    rm -f "$HCS_TOOLS_HOME/raw2ometiff.zip" && \
    chmod +x "$HCS_TOOLS_HOME/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION/bin/raw2ometiff" && \
    wget "$BFTOOLS_DISTR_URL" -O "$HCS_TOOLS_HOME/bftools-cli.zip" && \
    unzip "$HCS_TOOLS_HOME/bftools-cli.zip" -d "$HCS_TOOLS_HOME" && \
    rm -f "$HCS_TOOLS_HOME/bftools-cli.zip"

ENV PATH="$HCS_TOOLS_HOME/bftools:$HCS_TOOLS_HOME/bioformats2raw/bioformats2raw-$BIOFORMATS_TO_RAW_VERSION/bin:$HCS_TOOLS_HOME/raw2ometiff/raw2ometiff-$RAW_TO_OMETIFF_VERSION/bin:$PATH"

COPY convert_to_ome_tiff.sh "$HCS_TOOLS_HOME/scripts/convert_to_ome_tiff.sh"
RUN chmod +x $HCS_TOOLS_HOME/scripts/convert_to_ome_tiff.sh

COPY app/ $CELLPROFILER_API_HOME/
COPY start.sh $CELLPROFILER_API_HOME/
RUN chmod +x $CELLPROFILER_API_HOME/start.sh

CMD ["/opt/cellprofiler-api-wrapper/start.sh"]
