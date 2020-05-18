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

ARG BASE_IMAGE="library/centos:7"

FROM $BASE_IMAGE

ENV LANG="en_US.UTF-8" 
ENV LANGUAGE=en_US

RUN yum -y install epel-release && \
    yum update -y && \
    yum install -y  wget \
                    vim \
                    xterm \
                    pulseaudio \
                    cups \
                    curl \
                    GConf2 \
                    nss-3.36.0 \
                    libXScrnSaver-1.2.2 \
                    xdg-utils \
                    pango-1.40.4 \
                    liberation-fonts \
                    gcc \
                    gcc-c++ \
                    python \
                    sudo \
                    which \
                    mesa-libGLU

RUN curl https://bootstrap.pypa.io/get-pip.py | python - && \
    pip install --upgrade pip flask

RUN pip install -I setuptools

# Install git from sources, as it conflicts with nvidia/cuda docker base image
ADD centos/scripts/install_git.sh /opt/install_git.sh
RUN chmod +x /opt/install_git.sh && \
    /opt/install_git.sh && \
    rm -f /opt/install_git.sh

# Install desktop environment and Xfce4
RUN yum --enablerepo=epel groupinstall "Server with GUI" -y && \
    yum --enablerepo=epel -y -x gnome-keyring --skip-broken groups install "Xfce" && \
    yum -y groups install "Fonts" && \
    yum erase -y *power* *screensaver* && \
    yum clean all && \
    rm -f /etc/xdg/autostart/xfce-polkit* && \
    /bin/dbus-uuidgen > /etc/machine-id

# Install keyboard layout switcher (xkb)
RUN yum install -y xfce4-xkb-plugin

RUN cd /opt && \
    wget -q "https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/nomachine/nomachine_6.5.6_9_x86_64.rpm" -O nomachine.rpm && \
    yum install -y nomachine.rpm && \
    rm -f nomachine.rpm

RUN sed -i '/DefaultDesktopCommand/c\DefaultDesktopCommand "/usr/bin/startxfce4"' /usr/NX/etc/node.cfg

RUN wget -q "https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/nomachine/scramble_alg.cpp" -O /usr/local/bin/scramble.cpp && \
    g++ /usr/local/bin/scramble.cpp -o /usr/local/bin/scramble && \
    rm -f /usr/local/bin/scramble.cpp

ADD serve_nxs.py /etc/nomachine/serve_nxs.py
ADD template.nxs /etc/nomachine/template.nxs
ADD xfce/ /etc/nomachine/xfce/
RUN mv /etc/nomachine/xfce/.config/xfce4/panel/xkb-plugin.rc /etc/nomachine/xfce/.config/xfce4/panel/xkb-plugin-6.rc

RUN sed -i 's@_XKB_PLUGIN_NAME_@xkb-plugin@g' /etc/nomachine/xfce/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml && \
    sed -i 's@_XKB_PLUGIN_ID_@6@g' /etc/nomachine/xfce/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml
ADD nomachine_launcher.sh /etc/nomachine/nomachine_launcher.sh

ARG LIBREOFFICE_DISTR_URL=
ADD centos/scripts/libreoffice.sh /opt/libreoffice.sh
RUN chmod +x /opt/libreoffice.sh && /opt/libreoffice.sh && rm -f /opt/libreoffice.sh

# Install Chrome
RUN wget -q "https://dl.google.com/linux/direct/google-chrome-stable_current_x86_64.rpm" -O /opt/google-chrome-stable_current_x86_64.rpm && \
    yum install -y /opt/google-chrome-stable_current_*.rpm && \
    rm -f /opt/google-chrome-stable_current_*.rpm


# Add Chrome desktop icon
ADD create_chrome_launcher.sh /caps/create_chrome_launcher.sh
RUN echo "bash /caps/create_chrome_launcher.sh" >> /caps.sh
RUN cp /usr/NX/etc/server.cfg /usr/NX/etc/server.cfg.template
