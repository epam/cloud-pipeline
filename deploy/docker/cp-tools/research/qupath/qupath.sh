#!/bin/bash

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

# There is a base64 encoded string that represent config directory name, since it has very strange structure
# the only way we found to work with it in bash is encode it to base64 and decode again in variable to use it normally
QUPATH_USER_PREFS_DIR_NAME=`echo "XyEnayFidyF1ISdjIWFAIjAhJ2chZEAiaSEjNCFjQCIxISghIX1AIjAhJ2chLmchdyEjNCE6ZyF1ISQhPQ==" | base64 --decode`

if [[ ! -f ~/.java/.userPrefs/${QUPATH_USER_PREFS_DIR_NAME}/prefs.xml ]]; then
    mkdir -p ~/.java/.userPrefs/${QUPATH_USER_PREFS_DIR_NAME}
    _PWD_DIR=$(pwd)
    cd ~/.java/.userPrefs/${QUPATH_USER_PREFS_DIR_NAME}
    _MEMORY_UNIT=$(grep MemTotal /proc/meminfo | awk '{ print $3 }')
    if [[ "$_MEMORY_UNIT" == "kB" ]]; then
        QUPATH_MEMORY=`expr $(grep MemTotal /proc/meminfo | awk '{ print $2 }') / 1024 / 10 \* 6`
    elif [[ "$_MEMORY_UNIT" == "MB" ]]; then
        QUPATH_MEMORY=`expr $(grep MemTotal /proc/meminfo | awk '{ print $2 }') / 10 \* 6`
    elif [[ "$_MEMORY_UNIT" == "GB" ]]; then
        QUPATH_MEMORY=`expr $(grep MemTotal /proc/meminfo | awk '{ print $2 }') \* 1024 / 10 \* 6`
    fi

cat <<EOF >prefs.xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<!DOCTYPE map SYSTEM "http://java.sun.com/dtd/preferences.dtd">
<map MAP_XML_VERSION="1.0">
  <entry key="doAutoUpdateCheck" value="false"/>
  <entry key="lastUpdateCheck" value="1620393291684"/>
  <entry key="maxMemoryMB" value="${QUPATH_MEMORY}"/>
  <entry key="qupathSetupValue" value="1"/>
  <entry key="qupathStylesheet" value="Modena Light"/>
</map>
EOF

cd "$_PWD_DIR"
fi
$QUPATH_LAUNCHER_HOME/QuPath-0.2.3/bin/QuPath-0.2.3