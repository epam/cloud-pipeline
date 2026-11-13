#!/bin/bash

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

# Starts a VNC desktop (Xvnc + xfwm4) and noVNC, then executes the given command (or waits, if none/--wait is given).
# Replaces /dockerstartup/vnc_startup.sh of the consol/centos-xfce-vnc image.

set -e

echo "USER_ID: $(id -u), GROUP_ID: $(id -g)"

DISPLAY_NUM="${DISPLAY#:}"
VNC_DIR="$HOME/.vnc"
mkdir -p "$VNC_DIR"
echo "$VNC_PW" | vncpasswd -f > "$VNC_DIR/passwd"
chmod 600 "$VNC_DIR/passwd"

# Remove stale locks to be a re-attachable container
rm -f "/tmp/.X${DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${DISPLAY_NUM}"

echo "Starting Xvnc on $DISPLAY: VNC_COL_DEPTH=$VNC_COL_DEPTH, VNC_RESOLUTION=$VNC_RESOLUTION"
Xvnc "$DISPLAY" -rfbport "$VNC_PORT" -rfbauth "$VNC_DIR/passwd" -SecurityTypes VncAuth \
     -geometry "$VNC_RESOLUTION" -depth "$VNC_COL_DEPTH" -alwaysshared > "$VNC_DIR/xvnc.log" 2>&1 &

for _ in $(seq 1 30); do
    [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] && break
    sleep 1
done
if [ ! -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ]; then
    echo "Xvnc failed to start:"
    cat "$VNC_DIR/xvnc.log"
    exit 1
fi

dbus-launch xfwm4 > "$VNC_DIR/wm.log" 2>&1 &
websockify --web /usr/share/novnc "$NO_VNC_PORT" "localhost:$VNC_PORT" > "$VNC_DIR/novnc.log" 2>&1 &

echo "VNC server started on port $VNC_PORT, noVNC on http://<host>:$NO_VNC_PORT/vnc.html"

if [ $# -eq 0 ] || [[ "$1" =~ ^(-w|--wait)$ ]]; then
    wait
else
    echo "Executing command: '$*'"
    exec "$@"
fi
