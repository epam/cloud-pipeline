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

# lifescience/cloud-pipeline:idp-latest
FROM centos:7

RUN curl -sL https://rpm.nodesource.com/setup_10.x | bash -
RUN yum install gcc-c++ \
                make \
                nodejs \
                git \
                openssl -y

RUN cd /opt && \
    git clone https://github.com/rodichenko/saml-idp.git && \
    cd saml-idp && \
    git checkout tags/v1.2.3 && \
    npm install --global

ADD config.js /opt/saml-idp/
ADD logo.png /opt/saml-idp/public/

ADD init /init
RUN chmod +x /init
CMD ["/init"]
