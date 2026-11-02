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

# Prerequisites
RUN dnf install -y \
                wget \
                java-1.8.0-openjdk \
                unzip && \
    dnf clean all

ENV CP_VM_MONITOR_HOME="/opt/vm-monitor" \
    CP_VM_MONITOR_TEMPLATES_LOCATION="${CP_VM_MONITOR_HOME}/templates"

RUN mkdir -p $CP_VM_MONITOR_HOME
ADD config $CP_VM_MONITOR_HOME/config

ADD init /init
RUN chmod +x /init                
# API distribution
ARG CP_API_DIST_URL=""
RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    mv bin/vm-monitor.jar $CP_VM_MONITOR_HOME/ && \
    mv bin/vm-monitor-templates ${CP_VM_MONITOR_TEMPLATES_LOCATION} && \
    rm -rf /tmp/*

CMD ["/init"]
