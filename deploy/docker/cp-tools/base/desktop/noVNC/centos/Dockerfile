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

ARG BASE_IMAGE=library/centos:7.7.1908
FROM $BASE_IMAGE

RUN yum install -y wget \
                   bzip2 \
                   gcc \
                   zlib-devel \
                   bzip2-devel \
                   xz-devel \
                   make \
                   ncurses-devel \
                   unzip \
                   git \
                   curl \
                   epel-release && \
    yum clean all && \
    curl https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/pip/2.7/get-pip.py | python -

ENV LANG='en_US.UTF-8' \
    LANGUAGE='en_US:en' \
    LC_ALL='en_US.UTF-8'

### Environment config
ENV HOME=/user_home \
    TERM=xterm \
    STARTUPDIR=/dockerstartup \
    INST_SCRIPTS=$HOME/install \
    NO_VNC_HOME=$HOME/noVNC
WORKDIR $HOME

RUN yum install -y vim \
                   wget \
                   net-tools \
                   locales \
                   bzip2 \
                   sudo \
                   python-numpy \
                   mailcap

### Install VNC (server)
RUN wget -qO- "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/noVNC/tigervnc-1.8.0.x86_64.tar.gz" | tar xz --strip 1 -C /

### Install noVNC (web client)
ADD scripts/install/no_vnc.sh $INST_SCRIPTS/no_vnc.sh
RUN bash $INST_SCRIPTS/no_vnc.sh

### Install xfce UI
RUN yum --enablerepo=epel groupinstall "Server with GUI" -y \
    && yum --enablerepo=epel -y -x gnome-keyring --skip-broken groups install "Xfce" \
    && yum -y groups install "Fonts" \
    && yum erase -y *power* *screensaver* \
    && yum clean all \
    && rm -f /etc/xdg/autostart/xfce-polkit* \
    && /bin/dbus-uuidgen > /etc/machine-id \
    && yum install -y xfce4-xkb-plugin
ENV XKB_PLUGIN_ID=6
RUN wget -q -O /tmp/nomachine-common.tar.gz "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/nomachine/nomachine-common.tar.gz" \
    && tar -C $HOME -xzf /tmp/nomachine-common.tar.gz --strip-components=2 ./xfce/.config \
    && rm -f /tmp/nomachine-common.tar.gz \
    && rm -f $HOME/.config/xfce4/panel/xkb-plugin-${XKB_PLUGIN_ID}.rc \
    && mv $HOME/.config/xfce4/panel/xkb-plugin.rc $HOME/.config/xfce4/panel/xkb-plugin-${XKB_PLUGIN_ID}.rc \
    && sed -i 's@_XKB_PLUGIN_NAME_@xkb-plugin@g' $HOME/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml \
    && sed -i 's@_XKB_PLUGIN_ID_@'"$XKB_PLUGIN_ID"'@g' $HOME/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml

### Install Chrome
RUN wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/chrome/google-chrome-104.0.5112.79-1.rpm" -O /opt/google-chrome.rpm && \
    yum install -y /opt/google-chrome.rpm && \
    rm -f /opt/google-chrome.rpm

### Configure startup
ADD scripts/startup $STARTUPDIR
ADD scripts/install/set_user_permission.sh $INST_SCRIPTS/set_user_permission.sh
RUN bash $INST_SCRIPTS/set_user_permission.sh $STARTUPDIR $HOME

ADD launchers/create_chrome_launcher.sh /caps/create_chrome_launcher.sh
RUN echo "bash /caps/create_chrome_launcher.sh" >> /caps.sh
