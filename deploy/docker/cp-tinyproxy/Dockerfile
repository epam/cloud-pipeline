# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM centos:7

# 1. TinyProxy (HTTP proxy)
RUN yum install wget \
                gcc \
                make -y

RUN mkdir -p /opt/tinyproxy && \
    cd /opt/tinyproxy && \
    wget https://github.com/tinyproxy/tinyproxy/releases/download/1.10.0/tinyproxy-1.10.0.tar.gz -O tinyproxy.tgz && \
    tar -zxf tinyproxy.tgz && \
    rm -f tinyproxy.tgz && \
    cd tinyproxy* && \
    ./autogen.sh && \
    ./configure && \
    make && \
    make install

# This is a template file to format usage statistics (curl tinyproxy.stats)
# See http://tinyproxy.github.io/: "StatHost" option
ADD stats.json /opt/tinyproxy/stats.json

# 2. NGINX (TCP/UDP forward proxy)
RUN yum install -y epel-release
# Since 1.20 we shall install "stream" explicitly
RUN yum install -y nginx nginx-mod-stream

# 3. SMTP proxy setup
RUN yum install -y nginx-mod-mail gettext && \
    wget "https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh" -O /tmp/miniconda.sh && \
    bash /tmp/miniconda.sh -b -p /opt/miniconda && \
    source /opt/miniconda/etc/profile.d/conda.sh && \
    conda create -y -n auth-srv python=3.7 && \
    conda activate auth-srv && \
    pip install dnspython requests
ADD smtp-auth-srv.py /opt/smtp/smtp-auth-srv.py

# 4. Add file beat logging
RUN wget -q https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/filebeat/filebeat-6.8.3-x86_64.rpm \
    && rpm -vi filebeat-6.8.3-x86_64.rpm && rm -rf filebeat-6.8.3-x86_64.rpm
ADD filebeat-template.yml /etc/filebeat/


ADD init /init
RUN chmod +x /init
