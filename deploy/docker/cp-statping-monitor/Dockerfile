# Copyright 2023 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM rockylinux:8.7

ARG VERSION=v0.90.80

# Install common dependencies
RUN yum update -y && \
    yum install -y \
            tzdata \
            curl \
            jq \
            iputils \
            gettext \
            make

# Install additional dependencies for Ruby
RUN dnf --enablerepo=powertools install -y autogen \
                                           libyaml-devel \
                                           libffi-devel \
                                           redhat-rpm-config \
                                           autoconf \
                                           automake \
                                           libtool

# Install Ruby 2.7
RUN yum install -y @ruby:2.7 \
                    ruby-devel

# Install Ruby libraries
RUN gem install bundler \
                sass

ENV IS_DOCKER=true
ENV STATPING_DIR=/opt/statping
ENV CP_STATPING_HOME=$STATPING_DIR
ENV PORT=8080

RUN MACHINE_ARCH="$(uname -m)" && \
    case "$MACHINE_ARCH" in \
        x86_64) export ARCH='amd64' ;; \
        armhf|arm|armv7l|armv7) export ARCH='arm-7' ;; \
        arm64|aarch64|armv8b|armv8l|aarch64_be) export ARCH='arm64' ;; \
    esac && \
    cd /tmp && \
    curl -L -o statping-linux-"$ARCH".tar.gz https://github.com/statping-ng/statping-ng/releases/download/"$VERSION"/statping-linux-"$ARCH".tar.gz && \
    tar -xvzf statping-linux-"$ARCH".tar.gz && \
    chmod +x statping && \
    mv statping /usr/local/bin/statping && \
    rm -rf statping-linux-"$ARCH".tar.gz

WORKDIR $STATPING_DIR
EXPOSE $PORT

ADD init /init
RUN chmod +x /init

CMD ["/init"]
