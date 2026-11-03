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

# CentOS 7 repositories are offline, so its yum cannot install python3. curl comes from the base curl-minimal
FROM rockylinux/rockylinux:9.8

RUN dnf install -y wget \
                   python3 \
                   python3-pip \
                   git && \
    dnf clean all
RUN pip3 install awscli
RUN wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq && \
    chmod +x /usr/bin/jq

ADD poll_queue.sh /poll_queue.sh
RUN chmod +x /poll_queue.sh

ENTRYPOINT /poll_queue.sh
