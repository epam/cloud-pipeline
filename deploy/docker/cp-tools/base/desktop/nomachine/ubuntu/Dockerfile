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

ARG BASE_IMAGE="library/ubuntu:16.04"

FROM $BASE_IMAGE

ENV LANG="en_US.UTF-8" 
ENV LANGUAGE=en_US
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update -y && \
    apt-get install -y locales && \
    locale-gen en_US.UTF-8 && \
    locale-gen en_US && \
    dpkg-reconfigure --frontend=noninteractive locales && \
    update-locale LANG=$LANG

RUN apt-get update -y && \
    apt-get install -y  wget \
                        vim \
                        xterm \
                        pulseaudio \
                        cups \
                        curl \
                        libgconf2-4 \
                        libnss3-dev \
                        libxss1 \
                        xdg-utils \
                        libpango1.0-0 \
                        fonts-liberation \
                        g++ \
                        git \
                        python \
                        sudo

RUN curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python - && \
    pip install --upgrade pip flask

RUN pip install -I setuptools

ARG GUI_ENV="xfce4 xfce4-xkb-plugin"
RUN apt-get update -y && \
    apt-get install -y ${GUI_ENV}              

RUN cd /opt && \
    wget -q "https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/nomachine/nomachine_6.5.6_9_amd64.deb" -O nomachine.deb && \
    dpkg -i nomachine.deb && \
    rm -f nomachine.deb

RUN wget -q "https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/nomachine/scramble_alg.cpp" -O /usr/local/bin/scramble.cpp && \
    g++ /usr/local/bin/scramble.cpp -o /usr/local/bin/scramble && \
    rm -f /usr/local/bin/scramble.cpp

ADD serve_nxs.py /etc/nomachine/serve_nxs.py
ADD template.nxs /etc/nomachine/template.nxs
ADD xfce/ /etc/nomachine/xfce/
RUN mv /etc/nomachine/xfce/.config/xfce4/panel/xkb-plugin.rc /etc/nomachine/xfce/.config/xfce4/panel/xkb-plugin-9.rc

# If it's ubuntu 18 - use xkb instead of xkb-plugin
# This magic string is like [[ "$BASE_IMAGE" == *"18."* ]] but more general and will execute correctly with older shells
RUN if [ -z "${BASE_IMAGE##*18.*}" ]; then \
        sed -i 's@_XKB_PLUGIN_NAME_@xkb@g' /etc/nomachine/xfce/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml ; \
    else \
        sed -i 's@_XKB_PLUGIN_NAME_@xkb-plugin@g' /etc/nomachine/xfce/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml ; \
    fi
RUN sed -i 's@_XKB_PLUGIN_ID_@9@g' /etc/nomachine/xfce/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml

ADD nomachine_launcher.sh /etc/nomachine/nomachine_launcher.sh

ADD ubuntu/scripts/libreoffice.sh /opt/libreoffice.sh
RUN chmod +x /opt/libreoffice.sh && /opt/libreoffice.sh && rm -f /opt/libreoffice.sh

# Install Chrome
RUN wget -q -O - https://dl-ssl.google.com/linux/linux_signing_key.pub | apt-key add - && \
    echo 'deb [arch=amd64] http://dl.google.com/linux/chrome/deb/ stable main' | tee /etc/apt/sources.list.d/google-chrome.list && \
    apt-get update && \
    apt-get install -y google-chrome-stable

# Add Chrome desktop icon
ADD create_chrome_launcher.sh /caps/create_chrome_launcher.sh
RUN echo "bash /caps/create_chrome_launcher.sh" >> /caps.sh
RUN cp /usr/NX/etc/server.cfg /usr/NX/etc/server.cfg.template
