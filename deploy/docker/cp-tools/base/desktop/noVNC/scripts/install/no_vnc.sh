#!/usr/bin/env bash
#
# Copyright 2017-2023 EPAM Systems, Inc. (https://www.epam.com/)
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
#

### every exit != 0 fails the script
set -e

echo "Install noVNC - HTML5 based VNC viewer"
mkdir -p $NO_VNC_HOME/utils/websockify
wget -qO- https://github.com/novnc/noVNC/archive/v1.0.0.tar.gz | tar xz --strip 1 -C $NO_VNC_HOME
# use older version of websockify to prevent hanging connections on offline containers, see https://github.com/ConSol/docker-headless-vnc-container/issues/50
wget -qO- https://github.com/novnc/websockify/archive/v0.6.1.tar.gz | tar xz --strip 1 -C $NO_VNC_HOME/utils/websockify
chmod +x -v $NO_VNC_HOME/utils/*.sh
## create index.html to forward automatically to `vnc.html`
ln -s $NO_VNC_HOME/vnc.html $NO_VNC_HOME/index.html

# Allow resizing
sed -i "s/UI.getSetting('resize') === 'scale'/true/g" $NO_VNC_HOME/app/ui.js
sed -i "s/UI.getSetting('resize') === 'remote'/true/g" $NO_VNC_HOME/app/ui.js

# Default path
sed -i "s/UI.getSetting('path')/window.location.pathname/g" $NO_VNC_HOME/app/ui.js

# Default pass
sed -i "s/WebUtil.getConfigVar('password')/'vncpassword'/g" $NO_VNC_HOME/app/ui.js
