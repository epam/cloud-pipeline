# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM python:3.9

# Prerequisites
RUN apt-get install -y \
                    wget \
                    curl \
                    unzip

ARG CP_API_DIST_URL=""
ENV CP_DTS_TUNNEL_HOME="/opt/dts-tunnel"

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    mkdir -p $CP_DTS_TUNNEL_HOME && \
    unzip bin/pipeline.jar && \
    mv BOOT-INF/classes/static/pipe $CP_DTS_TUNNEL_HOME/pipe && \
    chmod +x $CP_DTS_TUNNEL_HOME/pipe && \
    rm -rf /tmp/*

ADD requirements.txt /requirements.txt
RUN pip install -r /requirements.txt

ADD init.py /init.py

CMD ["python", "/init.py"]
