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

# start from base
FROM centos:7
# install tools
RUN yum install -y \
        git \
        wget \
        unzip
# url jdk 1.8.0
ARG JAVA_SOURCE="https://github.com/AdoptOpenJDK/openjdk8-binaries/releases/download/jdk8u222-b10/OpenJDK8U-jdk_x64_linux_hotspot_8u222b10.tar.gz"
# set /usr/lib as WORKDIR
WORKDIR /usr/lib
# download jdk and unzip it
RUN wget -P /usr/lib/ $JAVA_SOURCE && \
    tar -zxf OpenJDK8U-jdk_x64_linux_hotspot_8u222b10.tar.gz && \
    rm -f OpenJDK8U-jdk_x64_linux_hotspot_8u222b10.tar.gz
# set JAVA_HOME enviroment
ENV JAVA_HOME="/usr/lib/jdk8u222-b10/"
# set PATH enviroment
ENV PATH="$JAVA_HOME/bin:${PATH}"
# copy script
COPY get_jar-file.sh /init
# change rule
RUN chmod +x /init
WORKDIR /opt
# start script
RUN ["/init", "tes-dev", "https://github.com/SilinPavel/cloud-pipeline.git"]
# copy application.properties
RUN mkdir config
COPY config/application.properties config/
# run jar
CMD ["java", "-jar", "tes-adapter-1.0-SNAPSHOT.jar"]
# expose port
EXPOSE 8080