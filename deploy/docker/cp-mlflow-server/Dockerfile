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
    pip install mlflow psycopg2-binary

# MLflow GUI
ENV NVM_DIR=/opt/local/nvm
RUN mkdir -p $NVM_DIR && \
    curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.3/install.sh | bash && \
    source "$NVM_DIR/nvm.sh" && \
    nvm install 22 && \
    corepack enable yarn

ENV MLFLOW_JS_BUILD_DIR=/opt/local/mamba/envs/mlflow/lib/python3.10/site-packages/mlflow/server/js/build
RUN source "$NVM_DIR/nvm.sh" && \
    cd /tmp && \
    git clone https://github.com/rodichenko/mlflow.git -b embedded-mode && \
    cd mlflow/mlflow/server/js && \
    yes " " | yarn install && \
    yarn run build && \
    rm -rf $MLFLOW_JS_BUILD_DIR/* && \
    cp -r build/* $MLFLOW_JS_BUILD_DIR/ && \
    rm -rf /tmp/mlflow

# Scripts
ADD init /init
RUN chmod +x /init
ADD serve_model.sh /opt/local/scripts/serve_model.sh
RUN chmod +x /opt/local/scripts/serve_model.sh
ADD liveness-mlflow.sh /liveness-mlflow.sh
RUN chmod +x /liveness-mlflow.sh
