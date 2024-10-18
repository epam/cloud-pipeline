# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

RUN sed -i 's/^#baseurl=/baseurl=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^metalink=/#metalink=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/^mirrorlist=/#mirrorlist=/g' /etc/yum.repos.d/*.repo && \
    sed -i 's/mirror.centos.org/vault.centos.org/g' /etc/yum.repos.d/*.repo

RUN yum install -y curl python3

RUN  curl -s https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/3.6/get-pip.py | python3 - && \
     pip3 install -I kubernetes==12.0.1 \
                     requests==2.27.1


RUN mkdir /policy-manager
ADD templates /policy-manager/templates
ADD policy_manager.py /policy-manager

ADD init /policy-manager/init
RUN chmod +x /policy-manager/init

CMD ["/policy-manager/init"]
