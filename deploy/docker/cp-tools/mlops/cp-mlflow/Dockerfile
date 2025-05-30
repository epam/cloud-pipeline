ARG BASE_IMG=library/rockylinux:8.9
FROM $BASE_IMG

RUN yum install curl yum-utils -y

# Install common dependencies
RUN yum install -y wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   python2 \
                   fuse \
                   tzdata \
                   acl \
                   openssh-server \
                   yum-utils \
                   findutils && \
    yum clean all
RUN dnf install -y --allowerasing coreutils

# Install pip
RUN curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python2 && \
    python2 -m pip install \
    --index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple \
    --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com \
    -I -q setuptools==44.1.1

# MLFlow base
RUN mkdir -p /opt/local/mamba && \
    cd /opt/local/mamba && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    export PATH=$PATH:/opt/local/mamba/bin && \
    export MAMBA_ROOT_PREFIX=/opt/local/mamba && \
    eval "$(micromamba shell hook --shell bash)" && \
    micromamba create -n mlflow && \
    micromamba activate mlflow && \
    micromamba install -y python==3.10 && \
    pip install mlflow

# Patch MLFlow
ADD patches /tmp/patches
RUN cd /opt/local/mamba/envs/mlflow/lib/python3.10/site-packages/mlflow/tracking && \
    patch -p1 --dry-run < /tmp/patches/fluent.py.patch
