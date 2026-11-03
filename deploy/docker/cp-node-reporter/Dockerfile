# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE="rockylinux/rockylinux:9.8"

FROM $BASE_IMAGE

ENV CP_NODE_REPORTER_HOME="/opt/node-reporter"
ENV CP_NODE_REPORTER_OOM_HOME="$CP_NODE_REPORTER_HOME/oom" \
    CP_NODE_REPORTER_STATS_HOME="$CP_NODE_REPORTER_HOME/stats" \
    CP_NODE_REPORTER_COMMON_HOME="$CP_NODE_REPORTER_HOME/common"

ENV CP_PYTHON_PATH=python

RUN mkdir -p "$CP_NODE_REPORTER_HOME" \
             "$CP_NODE_REPORTER_OOM_HOME" \
             "$CP_NODE_REPORTER_STATS_HOME"

# bzip2: unpacks micromamba below
RUN dnf install -y unzip \
                   bzip2 && \
    dnf clean all

# Install jq
RUN curl -sL "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -o /usr/bin/jq && \
    chmod +x /usr/bin/jq

# Python 3.12 (conda-forge, via micromamba), the version pipe-common pins its dependencies for. Its bin goes first
# on PATH, so "python", "python3", "pip" and "gunicorn" are the env's. dnf keeps /usr/bin/python3.
ENV CP_NODE_REPORTER_PYTHON_HOME="/opt/local/mamba/envs/node-reporter"
ENV PATH="$CP_NODE_REPORTER_PYTHON_HOME/bin:$PATH"

RUN mkdir -p /opt/local/mamba && \
    cd /opt/local/mamba && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    /opt/local/mamba/bin/micromamba create -y \
        --prefix "$CP_NODE_REPORTER_PYTHON_HOME" \
        --root-prefix /opt/local/mamba \
        -c conda-forge \
        python=3.12 && \
    MAMBA_ROOT_PREFIX=/opt/local/mamba /opt/local/mamba/bin/micromamba clean -y --all

# Install "pipeline/CommonRepo" python package
ARG CP_API_DIST_URL
ARG CP_PIPE_COMMON_DIST_URL

RUN if [ -z "$CP_API_DIST_URL" ] && [ -z "$CP_PIPE_COMMON_DIST_URL" ]; then \
        echo "Neither of API dist URL or PIPE COMMON dist URL is set. Common scripts cannot be installed."; \
        exit 1; \
    fi

RUN if [ "$CP_API_DIST_URL" ]; then \
        mkdir -p /tmp/cp && \
        curl -s -k "$CP_API_DIST_URL" > /tmp/cp/cloud-pipeline.tar.gz && \
        cd /tmp/cp &&  \
        tar -zxf cloud-pipeline.tar.gz && \
        cd bin && \
        unzip pipeline.jar && \
        mkdir -p $CP_NODE_REPORTER_COMMON_HOME && \
        mv BOOT-INF/classes/static/pipe-common.tar.gz $CP_NODE_REPORTER_COMMON_HOME/pipe-common.tar.gz && \
        rm -rf /tmp/cp && \
        cd $CP_NODE_REPORTER_COMMON_HOME && \
        tar xf pipe-common.tar.gz && \
        python -m pip install . -I && \
        chmod 777 . -R && \
        rm -f pipe-common.tar.gz; \
    fi

RUN if [ "$CP_PIPE_COMMON_DIST_URL" ] && [ -z "$CP_API_DIST_URL" ]; then \
        mkdir -p $CP_NODE_REPORTER_COMMON_HOME && \
        curl -s -k "$CP_PIPE_COMMON_DIST_URL" > $CP_NODE_REPORTER_COMMON_HOME/pipe-common.tar.gz && \
        cd $CP_NODE_REPORTER_COMMON_HOME && \
        tar xf pipe-common.tar.gz && \
        python -m pip install . -I && \
        chmod 777 . -R && \
        rm -f pipe-common.tar.gz; \
    fi

ENV PATH="$PATH:$CP_NODE_REPORTER_COMMON_HOME/shell"

ADD oom/reporter.sh "$CP_NODE_REPORTER_OOM_HOME/reporter.sh"
ADD stats/reporter.py "$CP_NODE_REPORTER_STATS_HOME/reporter.py"
ADD stats/requirements.txt "$CP_NODE_REPORTER_STATS_HOME/requirements.txt"
ADD init /init

RUN pip install -r "$CP_NODE_REPORTER_STATS_HOME/requirements.txt"

ENV PYTHONPATH="$PYTHONPATH:$CP_NODE_REPORTER_STATS_HOME"

RUN chmod +x /init

CMD [ "/init" ]
