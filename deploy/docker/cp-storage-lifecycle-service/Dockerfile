# Copyright 2017-2019 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM ubuntu:18.04
RUN apt update && \
        apt install -y \
                    wget \
                    curl \
                    unzip \
                    gcc \
                    python3 python3-venv python3-pip python3-dev \
                    build-essential libssl-dev libffi-dev python3-setuptools
RUN python3 -m pip install --upgrade pip && python3 -m pip install setuptools_rust

# API distribution
ARG CP_API_DIST_URL=""
ENV COMMON_REPO_DIR=/usr/sbin/CommonRepo
ENV CP_SLS_HOME="/opt/cp-sls"
ENV CP_SLS_RUN_MODE=""

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    cd bin && \
    mkdir -p $CP_SLS_HOME && \
    mv sls.tar.gz $CP_SLS_HOME/ && \
    unzip pipeline.jar && \
    mkdir -p $COMMON_REPO_DIR && \
    mv BOOT-INF/classes/static/pipe-common.tar.gz $COMMON_REPO_DIR/pipe-common.tar.gz && \
    # Installing pipe-common
    cd $COMMON_REPO_DIR && \
    tar xf pipe-common.tar.gz && \
    python3 -m pip install . $CP_PIP_EXTRA_ARGS -I && \
    chmod 777 . -R && \
    rm -f pipe-common.tar.gz && \
    # Copy sls distro
    cd $CP_SLS_HOME/ && \
    tar -zxf $CP_SLS_HOME/sls.tar.gz && \
    rm -rf $CP_SLS_HOME/sls.tar.gz  && \
    mv sls* sls && \
    chown -R root:root sls && chmod -R 777 sls && \
    rm -rf /tmp/*

RUN cd $CP_SLS_HOME/sls && \
    python3 setup.py install

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

ADD start.sh /start.sh
RUN chmod +x /start.sh

CMD ["/start.sh"]
