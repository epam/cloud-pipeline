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

ARG BASE_IMAGE=rockylinux/rockylinux:8.7
FROM ${BASE_IMAGE}

# CP_CA_CERT_URL (optional): the URL of the platform's CA certificate, e.g. https://<platform>/pipeline/ca.pem, if
# the platform's TLS certificate is not signed by a public CA. It is trusted in the image
ARG CP_CA_CERT_URL=""
RUN if [ "$CP_CA_CERT_URL" ]; then \
        curl -sfk "$CP_CA_CERT_URL" > /etc/pki/ca-trust/source/anchors/cp-ca.pem && \
        update-ca-trust extract; \
    fi && \
    ln -s /etc/ssl/certs/ca-bundle.crt /etc/cp-certs.pem
ENV REQUESTS_CA_BUNDLE=/etc/cp-certs.pem

RUN yum install -y curl \
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
                   python3 \
                   fuse \
                   tzdata \
                   acl \
                   openssh-server \
                   yum-utils \
                   procps-ng \
                   findutils && \
    yum install -y --allowerasing coreutils && \
    yum clean all

