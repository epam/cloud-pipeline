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

## Connection ports for controlling the UI:
# VNC port:5901
# noVNC webport, connect via http://IP:8080/?password=vncpassword
ENV DISPLAY=:1 \
    VNC_PORT=5901 \
    NO_VNC_PORT=8080
EXPOSE $VNC_PORT $NO_VNC_PORT

### Envrionment config
ENV HOME=/user_home \
    TERM=xterm \
    STARTUPDIR=/dockerstartup \
    INST_SCRIPTS=$HOME/install \
    NO_VNC_HOME=$HOME/noVNC \
    VNC_COL_DEPTH=24 \
    VNC_RESOLUTION=1280x1024 \
    VNC_PW=vncpassword \
    VNC_VIEW_ONLY=false
WORKDIR $HOME

### Install some common tools
RUN yum install -y vim \
                   wget \
                   net-tools \
                   locales \
                   bzip2 \
                   sudo \
                   python-numpy \
                   mailcap

### Install xvnc-server & noVNC - HTML5 based VNC viewer
ADD common/install/no_vnc.sh $INST_SCRIPTS/no_vnc.sh
RUN wget -qO- "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/noVNC/tigervnc-1.8.0.x86_64.tar.gz" | tar xz --strip 1 -C / && \
    bash $INST_SCRIPTS/no_vnc.sh

### Install xfce UI
ADD common/xfce/ $HOME/
RUN yum --enablerepo=epel groupinstall "Server with GUI" -y && \
    yum --enablerepo=epel -y -x gnome-keyring --skip-broken groups install "Xfce" && \
    yum -y groups install "Fonts" && \
    yum erase -y *power* *screensaver* && \
    yum clean all && \
    rm -f /etc/xdg/autostart/xfce-polkit* && \
    /bin/dbus-uuidgen > /etc/machine-id && \
    yum install -y xfce4-xkb-plugin
RUN sed -i 's@_XKB_PLUGIN_NAME_@xkb-plugin@g' $HOME/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml && \
    sed -i 's@_XKB_PLUGIN_ID_@6@g' $HOME/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml

### configure startup
ADD common/scripts $STARTUPDIR
ADD common/install/set_user_permission.sh $INST_SCRIPTS/set_user_permission.sh
RUN bash $INST_SCRIPTS/set_user_permission.sh $STARTUPDIR $HOME

### Install LibreOffice
RUN cd /opt && \
    wget -q "https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/libreoffice/LibreOffice_6.1.5_Linux_x86-64_rpm.tar.gz" -O LibreOffice.tar.gz && \
    tar -zxf LibreOffice.tar.gz && \
    yum localinstall -y LibreOffice*/RPMS/*.rpm && \
    rm -rf LibreOffice*

### Install Chrome
RUN wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/chrome/google-chrome-104.0.5112.79-1.rpm" -O /opt/google-chrome-stable_current_x86_64.rpm && \
    yum install -y /opt/google-chrome-stable_current_*.rpm && \
    rm -f /opt/google-chrome-stable_current_*.rpm

ADD launchers/create_chrome_launcher.sh /caps/create_chrome_launcher.sh
RUN echo "bash /caps/create_chrome_launcher.sh" >> /caps.sh

USER 0
