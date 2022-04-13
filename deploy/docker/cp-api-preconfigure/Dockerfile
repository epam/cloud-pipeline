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
#
# Docker image based on https://github.com/vcclive/docker-winpack by Tamas Jalsovszky <tamas.jalsovszky@vcc.live>

# docker build . \
#   -t ${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline:api-preconfigure-${DOCKERS_VERSION} \
#   --build-arg CP_API_DIST_URL="https://..."
#   --build-arg myuid=${CP_CLOUD_DATA_PRECONFIGURE_UID:-11} --build-arg mygid=${CP_CLOUD_DATA_PRECONFIGURE_UID:-11}

FROM ubuntu:18.04

ARG myuid
ARG mygid

COPY waitonprocess.sh /scripts/
RUN chmod 755 /scripts/waitonprocess.sh

# get at least error information from wine
ENV WINEDEBUG -all,err+all

# first create user and group for all the X Window stuff
# required to do this first so we have consistent uid/gid between server and client container
RUN addgroup --system jenkins --gid $mygid \
	&& adduser \
	    --home /home/jenkins \
	    --disabled-password \
	    --shell /bin/bash \
	    --gecos "user for running an xclient application" \
	    --ingroup jenkins \
	    --quiet \
	    --uid $myuid \
	    jenkins

# Use xclient's home dir as working dir
WORKDIR /home/jenkins

# Install some tools required for creating the image
RUN dpkg --add-architecture i386
RUN sed -i "s/main/main contrib non-free/" /etc/apt/sources.list
RUN apt-get update -y \
	&& apt-get install -y --no-install-recommends \
	    curl \
	    zip \
	    unzip \
	    ca-certificates \
	    unrar \
	    wget \
	    acl \
	    jq \
	&& apt-get clean

# Install wine and related packages
RUN apt-get install -y --no-install-recommends \
	    wine64 \
	    wine32 \
	    winetricks \
	    xvfb \
	&& apt-get clean \
	&& rm -rf /var/lib/apt/lists/*

# Overwrite system bounded script because it doesn't work well in docker
ADD bin/xvfb-run /usr/bin/xvfb-run
RUN chmod 755 /usr/bin/xvfb-run

ENV WINE_MONO_VERSION 4.7.1
# Get latest version of mono for wine, installed by wine upon demand
RUN mkdir -p /usr/share/wine/mono \
	&& curl -SL 'http://dl.winehq.org/wine/wine-mono/$WINE_MONO_VERSION/wine-mono-$WINE_MONO_VERSION.msi' -o /usr/share/wine/mono/wine-mono-$WINE_MONO_VERSION.msi \
	&& chmod +x /usr/share/wine/mono/wine-mono-$WINE_MONO_VERSION.msi

# Wine really doesn't like to be run as root, so let's use a non-root user
USER jenkins
ENV JENKINS_HOME /home/jenkins
ENV WINEPREFIX /home/jenkins/.wine32
# set default wine arch
ENV WINEARCH win32
RUN wineboot --update

# Install Resource Hacker 4.7.x
RUN mkdir reshack \
	&& cd reshack \
	&& curl -SL "http://www.angusj.com/resourcehacker/resource_hacker.zip" -o resource_hacker.zip \
	&& unzip resource_hacker.zip \
	&& rm resource_hacker.zip

# Install .NET Framework 4.0
RUN wine wineboot --update \
	&& /scripts/waitonprocess.sh wineserver \
	&& winetricks --unattended dotnet40 dotnet_verifier \
	&& /scripts/waitonprocess.sh wineserver

# Install Wix Toolset
RUN mkdir wix \
	&& cd wix \
	&& curl -fsSLk -o wix.zip "https://github.com/wixtoolset/wix3/releases/download/wix3111rtm/wix311-binaries.zip" \
	&& unzip wix.zip \
	&& rm wix.zip

USER 0

# API distribution
ARG CP_API_DIST_URL=""
ENV CP_API_HOME="/opt/api"
ENV CP_COMMON="${CP_API_HOME}/cp-common"
ARG REQUIRED_JARS_REGEX="pipeline|jwt-generator|data-transfer-service"

RUN cd /tmp && \
    wget -q "$CP_API_DIST_URL" -O cloud-pipeline.tgz && \
    tar -zxf cloud-pipeline.tgz && \
    rm -rf bin/pipe-templates/__SYSTEM && \
    mkdir -p $CP_COMMON && \
    chmod 777 $CP_COMMON -R && \
    ls -dp bin/* \
        | grep '\.jar$' \
        | grep -v -E "/($REQUIRED_JARS_REGEX)\.jar$" \
        | xargs rm -f && \
    mv bin/* $CP_COMMON/ && \
    rm -f cloud-pipeline.tgz && \
    rm -rf bin

ADD preconfigure-api /preconfigure-api
RUN chmod +x /preconfigure-api

USER jenkins
WORKDIR /home/jenkins

CMD ["/preconfigure-api"]
