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

if [ -z $CP_NM_LOCAL_PORT ]; then
    export CP_NM_LOCAL_PORT=8888
fi

if [ -z $CP_NM_NOMACHINE_PORT ]; then
    export CP_NM_NOMACHINE_PORT=4000
fi

if [ -z $CP_NM_PROXY_HOST ]; then
    echo "NoMachine proxy host is not set (CP_NM_PROXY_HOST). Exiting..."
    exit 1
fi
if [ -z $CP_NM_PROXY_PORT ]; then
    echo "NoMachine proxy port is not set (CP_NM_PROXY_PORT). Exiting..."
    exit 1
fi

su -l "$OWNER" bash -c "cp -rT /etc/nomachine/xfce/ \$HOME/"

mkdir -p /home/${OWNER}/Desktop
chown ${OWNER} /home/${OWNER}/Desktop
chmod o+rwx /home/${OWNER}/Desktop

cp /usr/NX/etc/server.cfg.template /usr/NX/etc/server.cfg

sed -i '/#CreateDisplay/c\CreateDisplay 1' /usr/NX/etc/server.cfg
sed -i "/#DisplayOwner/c\DisplayOwner $OWNER" /usr/NX/etc/server.cfg

sed -i '/#UserScriptAfterSessionStart/c\UserScriptAfterSessionStart "/etc/nomachine/set_xkb.sh"' /usr/NX/etc/node.cfg

cat >/etc/nomachine/set_xkb.sh <<'EOL'
#!/bin/sh
DISPLAY=:1001.0
XAUTHORITY=/home/$2/.Xauthority /usr/bin/setxkbmap -model pc105 -layout us,fr,de -option grp:ctrl_shift_toggle -display $DISPLAY
EOL
chmod +x /etc/nomachine/set_xkb.sh

CP_VNC_CAPS_FILE="/caps.sh"
if [ -f $CP_VNC_CAPS_FILE ]; then
    chmod +x $CP_VNC_CAPS_FILE
    bash -c $CP_VNC_CAPS_FILE
    rm -f $CP_VNC_CAPS_FILE
fi


nohup python /etc/nomachine/serve_nxs.py    --local-port "${CP_NM_LOCAL_PORT}" \
                                            --nomachine-port "${CP_NM_NOMACHINE_PORT}" \
                                            --proxy "${CP_NM_PROXY_HOST}" \
                                            --proxy-port "${CP_NM_PROXY_PORT}" > /etc/nomachine/serve_nxs.log 2>&1 &
/etc/NX/nxserver --startup
tail -f /usr/NX/var/log/nxserver.log