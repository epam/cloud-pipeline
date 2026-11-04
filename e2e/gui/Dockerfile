# Copyright 2017-2026 EPAM Systems, Inc. (https://www.epam.com/)
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

FROM rockylinux/rockylinux:9.8

# Desktop: TigerVNC, the xfwm4 window manager and noVNC (xfwm4 and noVNC come from EPEL)
# Java 8: the Gradle 3.4.1 wrapper of the tests does not run on a newer Java
RUN dnf install -y epel-release dnf-plugins-core && \
    dnf config-manager --set-enabled crb && \
    dnf install -y wget \
                   procps-ng \
                   java-1.8.0-openjdk-devel \
                   tigervnc-server \
                   xfwm4 \
                   dbus-x11 \
                   novnc \
                   python3-websockify \
                   liberation-fonts \
                   google-noto-sans-cjk-ttc-fonts && \
    dnf clean all

# Install Chrome. Version: 63 (its GConf2 and lsb_release dependencies come from EPEL)
# The package adds the Google repository, which is removed to keep this version.
# Its post-install script logs "key import failed", as dnf holds the rpm lock: this does not fail the build
RUN wget -q -N https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/e2e/google-chrome-stable-63.0.3239.132-1.x86_64.rpm -P /tmp && \
    dnf install -y /tmp/google-chrome-stable-63.0.3239.132-1.x86_64.rpm && \
    rm -f /tmp/google-chrome-stable-63.0.3239.132-1.x86_64.rpm /etc/yum.repos.d/google-chrome.repo && \
    dnf clean all

# Install ChromeDriver. Version: 2.36
# ChromeDriver is taken from here: https://chromedriver.storage.googleapis.com/2.36/chromedriver_linux64.zip
RUN wget -q -N https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/e2e/chromedriver -P /tmp && \
    install -o root -g root -m 0755 /tmp/chromedriver /usr/local/bin/chromedriver && \
    rm -f /tmp/chromedriver

# Install screen recording tool, if required
ADD install_recording.sh /tmp/install_recording.sh
ARG RECORDING="false"
ENV RECORD=$RECORDING
RUN bash /tmp/install_recording.sh "$RECORDING" && \
    rm -f /tmp/install_recording.sh

# Test user: uid 1000, gid 0, home /headless
ENV USER_HOME_DIR="/headless" \
    HOME="/headless" \
    DISPLAY=":1" \
    VNC_PORT="5901" \
    NO_VNC_PORT="6901" \
    VNC_RESOLUTION="1280x1024" \
    VNC_COL_DEPTH="24" \
    VNC_PW="vncpassword"
RUN useradd -u 1000 -g 0 -d $USER_HOME_DIR -M -s /bin/bash default && \
    mkdir -p $USER_HOME_DIR/Downloads $USER_HOME_DIR/e2e/gui && \
    chown -R 1000:0 $USER_HOME_DIR && \
    chmod -R g+rwX $USER_HOME_DIR

# Start the desktop, then run the tests
ADD vnc_startup.sh /dockerstartup/vnc_startup.sh
ADD run.sh /tmp/run.sh
RUN chmod +x /dockerstartup/vnc_startup.sh /tmp/run.sh

WORKDIR $USER_HOME_DIR/e2e/gui
EXPOSE $VNC_PORT $NO_VNC_PORT

USER 1000

ENTRYPOINT ["/dockerstartup/vnc_startup.sh"]
CMD bash /tmp/run.sh $RECORD
