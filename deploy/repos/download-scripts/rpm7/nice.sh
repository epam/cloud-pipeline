#!/usr/bin/env bash
# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

set -e

# Common packages
yum install -y epel-release
yum install -y --downloadonly --downloaddir=/rpmcache \
                       glx-utils mesa-dri-drivers xorg-x11-server-Xorg \
                       xorg-x11-utils xorg-x11-xauth xorg-x11-xinit xvattr \
                       xorg*fonts* xterm libXvMC mesa-libxatracker freeglut \
                       gnome-desktop3 gnome-terminal gnome-system-log \
                       gnome-system-monitor nautilus evince gnome-color-manager \
                       gnome-font-viewer gnome-shell gnome-calculator gedit gdm \
                       metacity gnome-session gnome-classic-session \
                       gnome-session-xsession gnu-free-fonts-common \
                       gnu-free-mono-fonts gnu-free-sans-fonts \
                       gnu-free-serif-fonts desktop-backgrounds-gnome
yum -y --downloadonly --downloaddir=/rpmcache groupinstall "Xfce"
