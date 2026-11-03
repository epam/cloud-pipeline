# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM rockylinux/rockylinux:9.8

# Nodesource has no Node.js 10 build for el9, so Node.js comes from an AppStream module stream
RUN dnf module enable -y nodejs:24
# npm is only a weak dependency of nodejs, so it is listed explicitly
RUN dnf install gcc-c++ \
                make \
                nodejs \
                npm \
                git \
                openssl -y
# The API (Spring SAML 1.0) signs its AuthnRequests with RSA-SHA1, which the DEFAULT crypto policy of Rocky Linux 9 rejects
RUN update-crypto-policies --set DEFAULT:SHA1

# Since npm 7, a global install of a folder only links it and skips its dependencies, so they are installed in place first
RUN cd /opt && \
    git clone https://github.com/rodichenko/saml-idp.git && \
    cd saml-idp && \
    git checkout tags/v1.2.4 && \
    npm install && \
    npm install --global

ADD config.js /opt/saml-idp/
ADD logo.png /opt/saml-idp/public/

ADD init /init
RUN chmod +x /init
CMD ["/init"]
