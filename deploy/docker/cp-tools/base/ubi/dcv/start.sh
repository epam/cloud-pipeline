#!/bin/bash

# Copyright 2017-2024 EPAM Systems, Inc. (https://www.epam.com/)
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

DCV_INSTALL_TASK="NiceDCVInitialization"
export CP_DCV_DESKTOP_PORT=8099
export CP_DCV_WEB_PORT=8100

_dcv_distro_url="${CP_CAP_DCV_DISTRO_URL:-https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/dcv}"
_NICE_DCV_DISTRIBUTION=nice-dcv-2021.2-11445-el8-x86_64
wget "$_dcv_distro_url/${_NICE_DCV_DISTRIBUTION}.tgz" && \
tar -xvzf ${_NICE_DCV_DISTRIBUTION}.tgz && \
cd ${_NICE_DCV_DISTRIBUTION} && \
yum install -y nice-dcv-server-*.x86_64.rpm \
                nice-dcv-web-viewer-*.x86_64.rpm \
                nice-xdcv-2021.2.*.x86_64.rpm && \
systemctl start dbus

cd ..
rm -rf "${_NICE_DCV_DISTRIBUTION}*"

usermod -aG video dcv
CP_CAP_DCV_AUTH=${CP_CAP_DCV_AUTH:-"none"}
sed -i "s|#authentication=\"none\"|authentication=\"$CP_CAP_DCV_AUTH\"|g" /etc/dcv/dcv.conf
sed -i -E "s|#web-port=.*|web-port=$CP_DCV_WEB_PORT|g" /etc/dcv/dcv.conf
if [ "$CP_CAP_DCV_LICENSE" ]; then
    sed -i "s|#license-file = \"\"|license-file = \"$CP_CAP_DCV_LICENSE\"|g" /etc/dcv/dcv.conf
fi

cat <<EOF >>/etc/dcv/dcv.conf
[clipboard]
primary-selection-copy=true
primary-selection-paste=true
EOF

systemctl start dcvserver

export _RETRIES_TIMEOUT="${CP_DCV_RETRY_TIMEOUT:-30}"
export _RETRIES_COUNT="${CP_DCV_RETRIES_COUNT:-60}"
for _RETRY_ITERATION in $(seq 1 "$_RETRIES_COUNT"); do
    curl -k -s "https://localhost:$CP_DCV_WEB_PORT/" &> /dev/null
    _CHECK_RESULT=$?

    if [ $_CHECK_RESULT -ne 0 ]; then
        pipe_log_warn "[WARNING] DCV server is still not running. Try #${_RETRY_ITERATION}." "$DCV_INSTALL_TASK"
        sleep "$_RETRIES_TIMEOUT"
    else
        pipe_log_info "[INFO] DCV server is running. Proceeding." "$DCV_INSTALL_TASK"
        break
    fi
done

_dcv_user=${OWNER%@*}
dcv create-session --owner $_dcv_user --user $_dcv_user session


pipe_log_info "Run DCV desktop launcher" "$DCV_INSTALL_TASK"
nohup "${CP_PYTHON2_PATH}" "/etc/dcv-resource/serve_desktop.py" \
    --serving-port "${CP_DCV_DESKTOP_PORT}" \
    --desktop-port "${CP_DCV_WEB_PORT}" \
    --template-path "/etc/dcv-resource/linux/template.dcv" \
    &> "/var/log/nice_dcv_desktop.log" &

cat > $OWNER_HOME/Desktop/google-chrome.desktop <<EOF
[Desktop Entry]
Version=1.0
Name=Google Chrome
Exec=/usr/bin/google-chrome-stable %U
StartupNotify=true
Terminal=false
Icon=google-chrome
Type=Application
Categories=Network;WebBrowser;
MimeType=text/html;text/xml;application/xhtml_xml;image/webp;x-scheme-handler/http;x-scheme-handler/https;x-scheme-handler/ftp;
Actions=new-window;new-private-window;
EOF
chmod +rx $OWNER_HOME/Desktop/google-chrome.desktop
chown $OWNER:$OWNER $OWNER_HOME/Desktop/google-chrome.desktop


rm -f $OWNER_HOME/Desktop/spyder3.desktop


sleep infinity
