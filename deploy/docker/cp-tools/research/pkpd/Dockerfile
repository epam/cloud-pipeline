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

# BASE_IMAGE - CentOS-based NoMachine image
ARG BASE_IMAGE
FROM $BASE_IMAGE

ARG R_VERSION=4.0.0
ARG R_PACKAGE_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/r/R-${R_VERSION}-1-1.el7.x86_64.rpm"
RUN wget -q $R_PACKAGE_URL -O rpackage.rpm && \
        yum install -y rpackage.rpm && \
        rm -f rpackage.rpm && \
        chmod 777 -R /opt/R && \
        ln -s /opt/R/${R_VERSION}/bin/R /usr/local/bin/R && \
        ln -s /opt/R/${R_VERSION}/bin/Rscript /usr/local/bin/Rscript

ARG R_STUDIO_VERSION=1.4.1717
ARG R_STUDIO_URL="https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/r/rstudio-desktop-$R_STUDIO_VERSION.el7.x86_64.rpm"
RUN wget -q $R_STUDIO_URL -O rstudio-desktop.rpm && \
    yum install -y rstudio-desktop.rpm && \
    rm -f rstudio-desktop.rpm

ADD create_rstudio_launcher.sh /caps/create_rstudio_launcher.sh
RUN echo "bash /caps/create_rstudio_launcher.sh" >> /caps.sh
