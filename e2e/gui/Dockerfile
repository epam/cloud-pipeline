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

# GUI (Selenium) e2e tests runner: a VNC desktop with Chrome, ChromeDriver and JDK 8 (required by the Gradle 3.4.1 wrapper).
# It replaces the CentOS 7 based consol/centos-xfce-vnc image.
#
# docker build . -t e2e-gui-runner:latest \
#     --build-arg CHROME_VERSION="120.0.6099.109" \
#     --build-arg RECORDING="false"
#
# Extra CA certificates (e.g. of a TLS-intercepting proxy, or of the platform) can be placed into ./certs/*.pem
# before the build: they are added to the system trust store, which the JDK uses as well.
# The tests themselves are mounted into /headless/e2e/gui at runtime (see run-e2e.sh).

FROM rockylinux/rockylinux:9.8

ARG CHROME_VERSION="120.0.6099.109"
ARG CHROME_DIST_URL="https://storage.googleapis.com/chrome-for-testing-public/${CHROME_VERSION}/linux64/chrome-linux64.zip"
ARG CHROMEDRIVER_DIST_URL="https://storage.googleapis.com/chrome-for-testing-public/${CHROME_VERSION}/linux64/chromedriver-linux64.zip"

# Additional CA certificates
COPY certs/ /tmp/certs/
RUN find /tmp/certs -maxdepth 1 -type f \( -name '*.pem' -o -name '*.crt' \) -exec cp {} /etc/pki/ca-trust/source/anchors/ \; && \
    update-ca-trust extract && \
    rm -rf /tmp/certs

# Desktop: TigerVNC X server, xfwm4 window manager (EPEL), noVNC.
# Chrome for Testing is a plain archive, so its shared libraries are installed explicitly.
RUN dnf install -y epel-release dnf-plugins-core && \
    dnf config-manager --set-enabled crb && \
    dnf install -y \
                wget \
                unzip \
                procps-ng \
                which \
                java-1.8.0-openjdk-devel \
                tigervnc-server \
                xfwm4 \
                dbus-x11 \
                novnc \
                python3-websockify \
                liberation-fonts \
                google-noto-sans-cjk-ttc-fonts \
                alsa-lib \
                atk \
                at-spi2-atk \
                cups-libs \
                gtk3 \
                libdrm \
                libXcomposite \
                libXdamage \
                libXrandr \
                libxkbcommon \
                libxshmfence \
                mesa-libgbm \
                nss \
                pango \
                vulkan-loader \
                xdg-utils && \
    dnf clean all

# Chrome and ChromeDriver at the paths the tests use (AbstractBfxPipelineTest: /usr/local/bin/chrome, /usr/local/bin/chromedriver)
RUN cd /tmp && \
    wget -q "$CHROME_DIST_URL" -O chrome.zip && \
    unzip -q chrome.zip && \
    mv chrome-linux64 /opt/chrome && \
    ln -sf /opt/chrome/chrome /usr/local/bin/chrome && \
    wget -q "$CHROMEDRIVER_DIST_URL" -O chromedriver.zip && \
    unzip -q -j chromedriver.zip '*/chromedriver' -d /usr/local/bin/ && \
    chown root:root /usr/local/bin/chromedriver && \
    chmod 0755 /usr/local/bin/chromedriver && \
    rm -f chrome.zip chromedriver.zip && \
    _missing="$(ldd /opt/chrome/chrome /usr/local/bin/chromedriver | grep 'not found' || true)" && \
    if [ "$_missing" ]; then echo "Chrome libraries are missing: $_missing"; exit 1; fi && \
    /usr/local/bin/chrome --version && \
    /usr/local/bin/chromedriver --version

# Screen recording tool, if required
ADD install_recording.sh /tmp/install_recording.sh
ARG RECORDING="false"
ENV RECORD=$RECORDING
RUN bash /tmp/install_recording.sh "$RECORDING" && \
    rm -f /tmp/install_recording.sh

# Same user layout as the consol image: uid 1000, gid 0, HOME=/headless
ENV USER_HOME_DIR="/headless" \
    HOME="/headless" \
    GRADLE_USER_HOME="/headless/.gradle" \
    DISPLAY=":1" \
    VNC_PORT="5901" \
    NO_VNC_PORT="6901" \
    VNC_RESOLUTION="1920x1080" \
    VNC_COL_DEPTH="24" \
    VNC_PW="vncpassword" \
    LANG="en_US.UTF-8"
RUN dnf install -y glibc-langpack-en && dnf clean all && \
    useradd -u 1000 -g 0 -d $USER_HOME_DIR -M -s /bin/bash default && \
    mkdir -p $USER_HOME_DIR/Downloads $USER_HOME_DIR/e2e/gui $GRADLE_USER_HOME && \
    chown -R 1000:0 $USER_HOME_DIR && \
    chmod -R g+rwX $USER_HOME_DIR

ADD docker/vnc_startup.sh docker/gradle_proxy_props.sh /dockerstartup/
ADD run.sh /tmp/run.sh
RUN chmod +x /dockerstartup/*.sh /tmp/run.sh

# Gradle 3.4.1 distribution and the tests dependencies are cached in the image, so a run does not depend on
# services.gradle.org/Maven Central (run.sh uses --offline). Only the build files are needed for that.
# The build-time proxy (if any) is not kept in the image.
COPY --chown=1000:0 gradlew build.gradle settings.gradle /tmp/gradle-warmup/
COPY --chown=1000:0 gradle /tmp/gradle-warmup/gradle
COPY --chown=1000:0 docker/gradle-warmup.gradle /tmp/gradle-warmup/
USER 1000
RUN bash /dockerstartup/gradle_proxy_props.sh && \
    cd /tmp/gradle-warmup && \
    ./gradlew --no-daemon -q -I gradle-warmup.gradle warmup && \
    cd / && rm -rf /tmp/gradle-warmup $GRADLE_USER_HOME/daemon $GRADLE_USER_HOME/gradle.properties

WORKDIR $USER_HOME_DIR/e2e/gui
EXPOSE $VNC_PORT $NO_VNC_PORT

ENTRYPOINT ["/dockerstartup/vnc_startup.sh"]
CMD bash /tmp/run.sh $RECORD
