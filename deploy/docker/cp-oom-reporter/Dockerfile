# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM library/centos:7

ENV COMMON_REPO_DIR=/usr/sbin/CommonRepo
ENV PATH=$PATH:$COMMON_REPO_DIR/shell
ARG CP_API_DIST_URL
ENV CP_PIP_EXTRA_ARGS="--index-url http://cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com/tools/python/pypi/simple --trusted-host cloud-pipeline-oss-builds.s3-website-us-east-1.amazonaws.com"

RUN if [ -z "$CP_API_DIST_URL" ]; then \
        echo "API dist URL is not set, can't install common scripts"; \
        exit 1; \
    fi

RUN yum install unzip -y

# Install pip
RUN curl -s https://bootstrap.pypa.io/get-pip.py | python2 && \
    python2 -m pip install $CP_PIP_EXTRA_ARGS -I -q setuptools==44.1.1

# Install "pipeline/CommonRepo" python package
RUN mkdir -p /tmp/cp && \
    curl -s -k "$CP_API_DIST_URL" > /tmp/cp/cloud-pipeline.tar.gz && \
    cd /tmp/cp &&  \
    tar -zxf cloud-pipeline.tar.gz && \
    cd bin && \
    unzip pipeline.jar && \
    mkdir -p $COMMON_REPO_DIR && \
    mv BOOT-INF/classes/static/pipe-common.tar.gz $COMMON_REPO_DIR/pipe-common.tar.gz && \
    rm -rf /tmp/cp && \
    cd $COMMON_REPO_DIR && \
    tar xf pipe-common.tar.gz && \
    python2 -m pip install . $CP_PIP_EXTRA_ARGS -I && \
    chmod 777 . -R && \
    rm -f pipe-common.tar.gz

# Install jq
RUN curl -sL "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -o /usr/bin/jq && \
    chmod +x /usr/bin/jq

ADD init /init
ADD monitor-service/monitor_service.sh /monitor_service.sh
RUN chmod +x /init && chmod +x /monitor_service.sh
ENV CP_PYTHON2_PATH=python2
CMD [ "/init" ]
