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

usermod -a -G root "$OWNER"
echo "$OWNER ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

chmod g+rwx /user_home/ -R

rm -rf /tmp
mkdir /tmp
chmod 777 /tmp
chmod +t /tmp

mkdir /tmp/.ICE-unix
chmod 777 /tmp/.ICE-unix
chmod +t /tmp/.ICE-unix

su -l "$OWNER" bash -c "cp -rT /user_home/ \$HOME/"

CP_VNC_CAPS_FILE="/caps.sh"
if [ -f $CP_VNC_CAPS_FILE ]; then
    chmod +x $CP_VNC_CAPS_FILE
    bash -c $CP_VNC_CAPS_FILE
    rm -f $CP_VNC_CAPS_FILE
fi

su -l "$OWNER" bash -c "/dockerstartup/vnc_startup.sh"
