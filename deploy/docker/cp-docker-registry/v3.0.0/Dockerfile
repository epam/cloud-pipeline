# Copyright 2017-2025 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE=alpine:3.21.3
FROM $BASE_IMAGE

# INSTALL REGISTRY
RUN apk add wget gcc python3 py3-pip && \
    pip install --break-system-packages cryptography python-jose

RUN cd /tmp && \
    wget "https://github.com/distribution/distribution/releases/download/v3.0.0-rc.3/registry_3.0.0-rc.3_linux_amd64.tar.gz" && \
    tar -zxvf registry_*.tar.gz && \
    mv registry /bin/registry && \
    rm -f *

ADD generate_jwks.py /generate_jwks.py

# BASE IMAGE
RUN apk add --no-cache ca-certificates apache2-utils && \
    mkdir -p /etc/docker/registry
COPY docker-entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

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