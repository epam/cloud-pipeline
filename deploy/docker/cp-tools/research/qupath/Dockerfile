# Copyright 2021 EPAM Systems, Inc. (https://www.epam.com/)
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

ARG BASE_IMAGE
FROM $BASE_IMAGE

# Install deps
RUN apt update && \
    apt install -y  bzip2 \
                    vim \
                    nano \
                    wget \
                    acl

ENV QUPATH_LAUNCHER_HOME=/opt/local/qupath/

ADD qupath.sh qupath_install.sh $QUPATH_LAUNCHER_HOME/
ADD create_qupath_launcher.sh /caps/create_qupath_launcher.sh

RUN chmod 777 $QUPATH_LAUNCHER_HOME/ -R && \
    $QUPATH_LAUNCHER_HOME/qupath_install.sh && \
    echo "bash /caps/create_qupath_launcher.sh" >> /caps.sh
