#!/bin/bash

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

ARG BASE_IMAGE
FROM $BASE_IMAGE

# Install deps
RUN apt update && \
    apt install -y  bzip2 \
                    vim \
                    nano \
                    acl

# Install anaconda
ENV ANACONDA_HOME=/opt/local/anaconda3
ENV SPYDER_LAUNCHER_HOME=/opt/local/spyder/
ARG ANACONDA_VERSION="3-5.3.1"

ADD spyder.sh spyder.png anaconda_install.sh $SPYDER_LAUNCHER_HOME/
ADD create_spyder_launcher.sh /caps/create_spyder_launcher.sh

RUN mkdir -p $ANACONDA_HOME

RUN chmod 777 $SPYDER_LAUNCHER_HOME/ -R && \
    $SPYDER_LAUNCHER_HOME/anaconda_install.sh $ANACONDA_HOME $ANACONDA_VERSION && \
    echo "bash /caps/create_spyder_launcher.sh" >> /caps.sh
