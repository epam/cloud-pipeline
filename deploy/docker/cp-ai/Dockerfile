FROM rockylinux:8.8

RUN yum update -y && \
    yum install -y vim \
    wget \
    curl \
    unzip \
    git \
    nginx \
    bzip2

ARG GOOGLE_GENAI_MODEL
ARG GOOGLE_API_KEY
ARG GITHUB_TOKEN

COPY requirements.txt /tmp/ai/

ENV AI_CONDA_ENVIRONMENT_NAME ai

# Install micromamba
RUN mkdir -p /opt/local/mamba && \
    cd /opt/local/mamba && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    export PATH=$PATH:/opt/local/mamba/bin && \
    export MAMBA_ROOT_PREFIX=/opt/local/mamba && \
    micromamba config append channels conda-forge && \
    micromamba config set channel_priority strict && \
    eval "$(micromamba shell hook --shell bash)" && \
    micromamba create -y -n "$AI_CONDA_ENVIRONMENT_NAME" python==3.10 && \
    micromamba activate "$AI_CONDA_ENVIRONMENT_NAME" && \
    cd /tmp/ai && \
    pip install -r requirements.txt --no-cache-dir && \
    micromamba deactivate

ENV AI_APP_DIR /opt/ai
RUN mkdir -p $AI_APP_DIR
COPY api $AI_APP_DIR/api/
COPY launch.sh $AI_APP_DIR/

ENV GOOGLE_GENAI_MODEL $GOOGLE_GENAI_MODEL
ENV GOOGLE_API_KEY $GOOGLE_API_KEY
ENV GITHUB_TOKEN $GITHUB_TOKEN
ENV CHROMA_DB_PATH /opt/ai/chroma-db

RUN chmod +x $AI_APP_DIR/launch.sh

EXPOSE 7860

CMD ["$AI_APP_DIR/launch.sh"]
