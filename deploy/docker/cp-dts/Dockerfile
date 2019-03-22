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

FROM library/centos:7

RUN mkdir -p /opt/dts

RUN yum install java-1.8.0-openjdk -y

COPY data-transfer-service.jar /opt/dts/data-transfer-service.jar
COPY dts.sh /opt/dts/dts.sh
RUN chmod +x /opt/dts/dts.sh

RUN mkdir /opt/dts/config && mkdir /opt/dts/template
COPY application.properties /opt/dts/config/
COPY qsub.sh /opt/dts/template/
