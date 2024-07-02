# Copyright 2022 EPAM Systems, Inc. (https://www.epam.com/)
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

# docker build . --build-arg BASE_IMAGE="<CentOS-based NoMachine image>" -t phaedra:latest

ARG BASE_IMAGE
FROM $BASE_IMAGE

ARG PHAEDRA_VERSION="1.0.7"
ARG PHAEDRA_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/phaedra/phaedra_"$PHAEDRA_VERSION"_x86_64.rpm"
ARG PHAEDRA_ICON_URL="https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/phaedra/phaedra.png"
ENV PHAEDRA_HOME="/opt/phaedra"

RUN wget "$PHAEDRA_URL" -O phaedra.rpm && \
      yum install -y phaedra.rpm && \
      rm -f phaedra.rpm && \
      wget "$PHAEDRA_ICON_URL" -O "$PHAEDRA_HOME/phaedra.png"

ADD create_phaedra_launcher.sh /caps/create_phaedra_launcher.sh
ADD configure_phaedra.sh $PHAEDRA_HOME/configure_phaedra.sh

RUN chmod 777 $PHAEDRA_HOME/ -R && \
      echo "bash /caps/create_phaedra_launcher.sh" >> /caps.sh
