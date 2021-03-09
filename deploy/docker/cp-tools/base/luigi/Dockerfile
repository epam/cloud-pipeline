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

FROM centos:7

# Common applications
RUN yum install -y wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   curl \
                   epel-release \
                   python && \
    yum clean all

RUN curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python - && \
    pip install requests==2.21.0 \
                ipaddress==1.0.22 \
                rsa==4.0 \
                pykube==0.15.0 \
                psycopg2==2.7.7 \
                sqlalchemy==1.3.2 \
                luigi==2.8.3

COPY ./luigi/* /etc/luigi/
