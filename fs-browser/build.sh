#!/bin/bash

# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

_BUILD_DOCKER_IMAGE="${_BUILD_DOCKER_IMAGE:-python:3.6-stretch}"
_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_fsbrowser_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<EOL
###
# Setup Pyinstaller
###
mkdir -p ${PYINSTALLER_PATH}
cd ${PYINSTALLER_PATH}
git clone --branch resolve_tmpdir https://github.com/mzueva/pyinstaller.git
cd pyinstaller/bootloader/
python ./waf all

###
# Setup dependencies
###
python -m pip install -r ${FSBROWSER_SOURCES_DIR}/requirements.txt

###
# Setup fsbrowser
###
python ${PYINSTALLER_PATH}/pyinstaller/pyinstaller.py -y --clean \
  --distpath ${FSBROWSER_DIST_PATH} \
  --runtime-tmpdir ${FSBROWSER_RUNTIME_TMP_DIR} \
  --add-data ${FSBROWSER_SOURCES_DIR}/fsbrowser:fsbrowser \
  --add-data /usr/local/lib/python3.6/site-packages/flasgger:flasgger \
  --hidden-import=pygit2 \
  --hidden-import=requests \
  --hidden-import=pkg_resources.py2_warn ${FSBROWSER_SOURCES_DIR}/fsbrowser/fsbrowser-cli.py
tar -zcf ${FSBROWSER_DIST_PATH}/fsbrowser.tar.gz -C ${FSBROWSER_DIST_PATH} fsbrowser-cli
EOL

docker pull $_BUILD_DOCKER_IMAGE &> /dev/null
docker run -i --rm \
           --env PYINSTALLER_PATH=$PYINSTALLER_PATH \
           --env FSBROWSER_SOURCES_DIR=$FSBROWSER_SOURCES_DIR \
           --env FSBROWSER_DIST_PATH=$FSBROWSER_DIST_PATH \
           --env FSBROWSER_RUNTIME_TMP_DIR="'"$FSBROWSER_RUNTIME_TMP_DIR"'" \
           -v $FSBROWSER_SOURCES_DIR:$FSBROWSER_SOURCES_DIR \
           -v $FSBROWSER_DIST_PATH:$FSBROWSER_DIST_PATH \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           $_BUILD_DOCKER_IMAGE \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
