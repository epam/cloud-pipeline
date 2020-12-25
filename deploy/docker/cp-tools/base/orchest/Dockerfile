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

FROM library/centos:7

RUN yum install git jq -y

RUN cd /usr/bin/ && \
    git clone https://github.com/orchest/orchest.git && cd /usr/bin/orchest

ADD docker-image-load.sh /usr/bin/docker-image-load.sh
RUN chmod +x /usr/bin/docker-image-load.sh

RUN mkdir /orchest-images && cd /orchest-images && \
    docker-image-load.sh /orchest-images/rabbitmq rabbitmq:3 \
    && docker-image-load.sh /orchest-images/jupyter-server orchest/jupyter-server:latest \
    && docker-image-load.sh /orchest-images/base-kernel-py orchest/base-kernel-py:latest \
    && docker-image-load.sh /orchest-images/auth-server orchest/auth-server:latest \
    && docker-image-load.sh /orchest-images/jupyter-enterprise-gateway orchest/jupyter-enterprise-gateway:latest \
    && docker-image-load.sh /orchest-images/orchest-webserver orchest/orchest-webserver:latest \
    && docker-image-load.sh /orchest-images/memory-server orchest/memory-server:latest \
    && docker-image-load.sh /orchest-images/celery-worker orchest/celery-worker:latest \
    && docker-image-load.sh /orchest-images/orchest-ctl orchest/orchest-ctl:latest \
    && docker-image-load.sh /orchest-images/orchest-api orchest/orchest-api:latest \
    && docker-image-load.sh /orchest-images/nginx-proxy orchest/nginx-proxy:latest \
    && docker-image-load.sh /orchest-images/update-server orchest/update-server:latest  \
    && docker-image-load.sh /orchest-images/file-manager orchest/file-manager:latest

ENV ORCHEST_HOME /usr/bin/orchest
ADD init /init

RUN chmod +x /init

