# Copyright 2025 EPAM Systems, Inc. (https://www.epam.com/)
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

# Built like cp-storage-lifecycle-service: the service and pipe-common both come out of the API
# distribution, so the image carries no copy of the repository.
#
# 24.04 and not the 18.04 the lifecycle service uses: setup.py requires Python 3.12.
FROM ubuntu:24.04

# nfs-common: the service mounts NFS shares itself. procps: pgrep, which liveness.sh uses.
RUN apt-get update && \
        apt-get install -y --no-install-recommends \
                    wget \
                    curl \
                    unzip \
                    ca-certificates \
                    procps \
                    nfs-common \
                    gcc \
                    build-essential libssl-dev libffi-dev \
                    python3 python3-venv python3-dev && \
        rm -rf /var/lib/apt/lists/*

# API distribution
ARG CP_API_DIST_URL=""
# For an installation that builds behind a local index. Nothing in this repository passes it, so it
# is empty unless a build sets it by hand.
ARG CP_PIP_EXTRA_ARGS=""
ENV COMMON_REPO_DIR=/usr/sbin/CommonRepo
ENV CP_SATS_HOME="/opt/cp-sats"
# Its own virtual environment: 24.04 marks the system interpreter externally managed (PEP 668).
ENV CP_SATS_VENV="$CP_SATS_HOME/venv"
# Image defaults. The pod spec overrides any `CP_SATS_*` variable without touching the entry script.
ENV CP_SATS_LOG_DIR="$CP_SATS_HOME/logs"
ENV CP_SATS_WORK_DIR="$CP_SATS_HOME/work"

RUN python3 -m venv "$CP_SATS_VENV" && \
    "$CP_SATS_VENV/bin/python" -m pip install --upgrade pip setuptools wheel

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    cd bin && \
    mkdir -p $CP_SATS_HOME && \
    mv sats.tar.gz $CP_SATS_HOME/ && \
    unzip -q pipeline.jar && \
    mkdir -p $COMMON_REPO_DIR && \
    mv BOOT-INF/classes/static/pipe-common.tar.gz $COMMON_REPO_DIR/pipe-common.tar.gz && \
    # pipe-common, the platform's API client. On no package index - see requirements.txt.
    cd $COMMON_REPO_DIR && \
    tar xf pipe-common.tar.gz && \
    "$CP_SATS_VENV/bin/python" -m pip install . $CP_PIP_EXTRA_ARGS -I && \
    chmod 777 . -R && \
    rm -f pipe-common.tar.gz && \
    # The tarball is removed first, so `sats*` cannot match it.
    cd $CP_SATS_HOME/ && \
    tar -zxf $CP_SATS_HOME/sats.tar.gz && \
    rm -rf $CP_SATS_HOME/sats.tar.gz && \
    mv sats* sats && \
    chown -R root:root sats && chmod -R 777 sats && \
    "$CP_SATS_VENV/bin/python" -m pip install ./sats $CP_PIP_EXTRA_ARGS && \
    mkdir -p "$CP_SATS_LOG_DIR" "$CP_SATS_WORK_DIR" && \
    rm -rf /tmp/*

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

ADD start.sh /start.sh
RUN chmod +x /start.sh

CMD ["/start.sh"]
