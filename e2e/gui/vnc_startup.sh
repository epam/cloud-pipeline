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

# Starts a VNC desktop (Xvnc and xfwm4) and noVNC, then runs the given command.
# Without a command, or with -w/--wait, it keeps the desktop running.

set -e

DISPLAY_NUM="${DISPLAY#:}"
mkdir -p "$HOME/.vnc"
echo "$VNC_PW" | vncpasswd -f > "$HOME/.vnc/passwd"
chmod 600 "$HOME/.vnc/passwd"

# Remove the X lock and socket that a previous start of this container left behind
rm -f "/tmp/.X${DISPLAY_NUM}-lock" "/tmp/.X11-unix/X${DISPLAY_NUM}"

Xvnc "$DISPLAY" -rfbport "$VNC_PORT" -rfbauth "$HOME/.vnc/passwd" -SecurityTypes VncAuth \
     -geometry "$VNC_RESOLUTION" -depth "$VNC_COL_DEPTH" -alwaysshared > "$HOME/.vnc/xvnc.log" 2>&1 &
XVNC_PID=$!

for _ in $(seq 1 30); do
    [ -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] && break
    kill -0 "$XVNC_PID" 2>/dev/null || break
    sleep 1
done
if [ ! -S "/tmp/.X11-unix/X${DISPLAY_NUM}" ] || ! kill -0 "$XVNC_PID" 2>/dev/null; then
    echo "Xvnc failed to start:" && cat "$HOME/.vnc/xvnc.log"
    exit 1
fi

dbus-launch xfwm4 > "$HOME/.vnc/wm.log" 2>&1 &
websockify --web /usr/share/novnc "$NO_VNC_PORT" "localhost:$VNC_PORT" > "$HOME/.vnc/novnc.log" 2>&1 &

echo "VNC server started on port $VNC_PORT, noVNC on http://<host>:$NO_VNC_PORT/vnc.html"

if [ $# -eq 0 ] || [[ "$1" =~ ^(-w|--wait)$ ]]; then
    wait
else
    echo "Executing command: '$*'"
    exec "$@"
fi
