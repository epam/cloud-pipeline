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

ARG BASE_IMAGE="ubuntu:18.04"
FROM $BASE_IMAGE

RUN apt update && \
        apt install -y \
                    wget \
                    curl \
                    unzip \
                    gcc \
                    python3 python3-venv python3-pip python3-dev \
                    build-essential libssl-dev libffi-dev python3-setuptools
RUN python3 -m pip install --upgrade pip \
    && python3 -m pip install setuptools_rust

ENV COMMON_REPO_DIR="/usr/sbin/CommonRepo"
ENV CP_SLS_HOME="/opt/cp-sls"
ENV CP_SLS_RUN_MODE=""

ADD dist/bin/sls.tar.gz $CP_SLS_HOME
RUN cd $CP_SLS_HOME/ \
    && mv sls* sls \
    && chown -R root:root sls \
    && chmod -R 777 sls \
    && cd $CP_SLS_HOME/sls \
    && python3 setup.py install

ADD dist/pipe-common.tar.gz $COMMON_REPO_DIR
RUN cd $COMMON_REPO_DIR \
    && chmod 777 . -R \
    && python3 -m pip install . $CP_PIP_EXTRA_ARGS -I

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

ADD start.sh /start.sh
RUN chmod +x /start.sh

CMD ["/start.sh"]
