ARG BASE_IMAGE
FROM $BASE_IMAGE

ARG PYTHON_3_9_DISTRIBUTION_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/python/3/Python-3.9.6.tgz"

ENV CELLPROFILER_API_HOME=/opt/cellprofiler-api-wrapper

RUN yum install -y \
        build-essential \
        g++ \
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
        which && \
    wget "$PYTHON_3_9_DISTRIBUTION_URL" -O python3.tgz  && \
        tar xzf python3.tgz && \
        cd Python-* && \
        ./configure --enable-optimizations && \
        make altinstall && \
        cd .. && \
        rm -rf Python-* && \
    mkdir -p "$CELLPROFILER_API_HOME"

ENV JAVA_HOME="/usr/lib/jvm/java-1.8.0-openjdk"

ARG CELL_PROFILER_VERSION=4.2.1
ARG NUMPY_VERSION=1.22.4
ARG CYTHON_VERSION=0.29.30

COPY requirements.txt $CELLPROFILER_API_HOME/
RUN python3.9 -m pip install wheel numpy==$NUMPY_VERSION \
                                   Cython==$CYTHON_VERSION && \
    python3.9 -m pip install cellprofiler==$CELL_PROFILER_VERSION && \
    python3.9 -m pip install -r $CELLPROFILER_API_HOME/requirements.txt

COPY app/ $CELLPROFILER_API_HOME/
COPY start.sh $CELLPROFILER_API_HOME/
RUN chmod +x $CELLPROFILER_API_HOME/start.sh

CMD ["/opt/cellprofiler-api-wrapper/start.sh"]
