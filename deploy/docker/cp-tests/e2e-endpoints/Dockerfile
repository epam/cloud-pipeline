# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM library/centos:7.7.1908
RUN yum install -y epel-release && \
    yum install -y curl \
                   nginx \
                   wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   python \
                   fuse \
                   tzdata \
                   acl \
                   coreutils \
                   openssh-server \
                   yum-utils


RUN curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

RUN mkdir /hosted
COPY index.html /hosted/
COPY jwt-decode.js /hosted/
RUN chmod -R 777 /hosted

ADD	nginx.conf /etc/nginx/nginx.conf

ADD	start.sh /start.sh
RUN chmod 777 /start.sh
