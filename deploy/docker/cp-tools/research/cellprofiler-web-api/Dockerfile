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
RUN python3.8 -m pip install wheel numpy==$NUMPY_VERSION \
                                   Cython==$CYTHON_VERSION && \
    python3.8 -m pip install cellprofiler==$CELL_PROFILER_VERSION && \
    python3.8 -m pip install -r $CELLPROFILER_API_HOME/requirements.txt

COPY app/ $CELLPROFILER_API_HOME/
COPY start.sh $CELLPROFILER_API_HOME/
RUN chmod +x $CELLPROFILER_API_HOME/start.sh

CMD ["/opt/cellprofiler-api-wrapper/start.sh"]
