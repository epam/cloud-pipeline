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

ARG BASE_IMAGE="centos:7"
FROM $BASE_IMAGE

# Prerequisites
RUN yum install -y \
                wget \
                curl \
                java-1.8.0-openjdk \
                python \
                unzip

ENV CP_NOTIFIER_HOME="/opt/notifier"

ADD dist/bin/notifier.jar $CP_NOTIFIER_HOME/notifier.jar

ADD config $CP_NOTIFIER_HOME/config

ADD init /init
RUN chmod +x /init

ADD liveness.sh /liveness.sh
RUN chmod +x /liveness.sh

CMD ["/init"]
