ARG BASE_IMG=library/rockylinux:8.7
COPY . /opt/local/mcp-remote
ENV PATH=$PATH:/opt/local/mamba/bin \
    MAMBA_ROOT_PREFIX=/opt/local/mamba
RUN mkdir -p /opt/local/mamba && \
    cd /opt/local/mamba && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    eval "$(micromamba shell hook --shell bash)" && \
    micromamba create -n mcp && \
    micromamba activate mcp && \
    micromamba install -y python==3.12 && \
    pip install mlflow psycopg2-binary boto3 && \
    pip install -e /opt/local/mcp-remote

