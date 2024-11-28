# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE=golang:1.15-alpine
FROM BASE_IMAGE

# BUILD REGISTRY
RUN apk add git make

# TODO: switch to 2.8.3
ENV GOPATH=/go
RUN mkdir -p $GOPATH/src/github.com/docker && \
    cd $GOPATH/src/github.com/docker && \
    git clone https://github.com/distribution/distribution && \
    cd distribution && \
    git checkout decc64eb5c5e846c9cfae657afe38b1d5df806ad && \
    make && \
    mv bin/registry /bin/registry && \
    rm -rf $GOPATH

# BASE IMAGE
RUN apk add --no-cache ca-certificates apache2-utils && \
    mkdir -p /etc/docker/registry
COPY docker-entrypoint.sh /entrypoint.sh

# Customization
ADD update_config.sh /update_config.sh
RUN chmod +x /update_config.sh

RUN apk add nginx \
            gettext && \
    mkdir -p /run/nginx
ADD update_nginx_config.sh /update_nginx_config.sh
RUN chmod +x /update_nginx_config.sh

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["/update_nginx_config.sh; /update_config.sh; /entrypoint.sh /etc/docker/registry/config.yml"]