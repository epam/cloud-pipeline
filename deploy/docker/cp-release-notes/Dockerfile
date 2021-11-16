# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM openjdk:8u312-jdk-slim

ARG CP_API_DIST_URL=""
ENV CP_RELEASE_NOTES_HOME="/opt/release-notes"

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    mkdir -p $CP_RELEASE_NOTES_HOME && \
    mv bin/release-notes-agent.jar $CP_RELEASE_NOTES_HOME/ && \
    rm -rf /tmp/*

ADD config $CP_RELEASE_NOTES_HOME/config
ADD init /
RUN chmod +x /init
