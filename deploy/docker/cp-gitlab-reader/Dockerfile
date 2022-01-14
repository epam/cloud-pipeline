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

# Prerequisites
RUN apt update && \
    apt install -y \
                wget \
                curl \
                git \
                nginx gcc \
                python3 python3-venv python3-pip python3-dev \
                build-essential libssl-dev libffi-dev python3-setuptools

RUN python3 -m pip install --upgrade pip && pip3 install setuptools_rust==0.12.1

# API distribution
ARG CP_API_DIST_URL=""
ENV CP_GITLAB_READER_HOME="/opt/gitlab-reader"

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    mkdir -p $CP_GITLAB_READER_HOME && \
    mv bin/gitreader.tar.gz $CP_GITLAB_READER_HOME/ && \
    rm -rf /tmp/* && \
    cd $CP_GITLAB_READER_HOME/ && \
    tar -zxf $CP_GITLAB_READER_HOME/gitreader.tar.gz && \
    rm -rf $CP_GITLAB_READER_HOME/gitreader.tar.gz  && \
    mv gitreader* gitreader && \
    chown -R root:root gitreader && chmod -R 777 gitreader

RUN cd $CP_GITLAB_READER_HOME/gitreader && \
    python3 -m pip install wheel && \
    python3 -m pip install uwsgi==2.0.20 && \
    python3 setup.py install


ADD init.sh /init.sh
RUN chmod +x /init.sh

CMD ["/init.sh"]
