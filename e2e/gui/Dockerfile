# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM consol/centos-xfce-vnc
ENV REFRESHED_AT 2020-08-10

# Switch to root user to install additional software
USER 0

# Install common
RUN apt-get update -y && \
    apt-get install -y wget \
                   curl \
                   unzip \
                   xvfb \
                   libxi6 \
                   libgconf-2-4 \
                   openjdk-8-jdk \
                   python \
                   python-pip \
                   python-dev \
                   gcc

# Install Chrome
RUN rm ~/google-chrome-63.0.3239.132-1.deb && \
    wget -N https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/e2e/google-chrome-63.0.3239.132-1.deb -P ~/ && \
    dpkg -i --force-depends ~/google-chrome-63.0.3239.132-1.deb && \
    apt-get -f install -y

# Install ChromeDriver. Version: 2.36
# ChromeDriver is taken from here: https://chromedriver.storage.googleapis.com/2.36/chromedriver_linux64.zip
RUN rm ~/chromedriver_linux64.zip && \
    rm /usr/local/bin/chromedriver && \
    wget -N https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/e2e/chromedriver -P ~/ && \
    mv -f ~/chromedriver /usr/local/bin/chromedriver && \
    chown root:root /usr/local/bin/chromedriver && \
    chmod 0755 /usr/local/bin/chromedriver && \

# Install screen recording tool, if required
ADD install_recording.sh /install_recording.sh
ARG RECORDING="false"
RUN bash install_recording.sh "$RECORDING" && \
    rm -f install_recording.sh

# Test prerequisites
ENV USER_HOME_DIR="/headless"
RUN mkdir -p /$USER_HOME_DIR/Downloads
WORKDIR /$USER_HOME_DIR/e2e/gui

# Run tests
ARG COMMAND="./gradlew clean test"
ARG STAND_NAME="TEST_NAME"
ADD run.sh /run.sh
RUN chmod +x /run.sh && \
    bash run.sh "$RECORDING" "$COMMAND" "$STAND_NAME"

# switch back to default user
USER 1000
