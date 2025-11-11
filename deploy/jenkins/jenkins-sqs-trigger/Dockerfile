# Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM rockylinux:8.7

RUN yum install -y wget \
                   curl \
                   git \
                   bzip2 \
                   zip \
                   unzip

RUN mkdir -p /opt/local/mamba && \
    cd /opt/local/mamba && \
    curl -Ls https://micro.mamba.pm/api/micromamba/linux-64/latest | tar -xvj bin/micromamba && \
    export PATH=$PATH:/opt/local/mamba/bin && \
    export MAMBA_ROOT_PREFIX=/opt/local/mamba && \
    eval "$(micromamba shell hook --shell bash)" && \
    micromamba create -n py3 && \
    micromamba activate py3 && \
    micromamba install -y python==3.10 && \
    pip install awscli

ENV AWSCLI_BIN=/opt/local/mamba/envs/py3/bin/aws

RUN wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
    chmod +x /usr/bin/jq

ADD poll_queue.sh /poll_queue.sh
RUN chmod +x /poll_queue.sh

ENTRYPOINT /poll_queue.sh
