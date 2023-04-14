#!/bin/bash

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

_BUILD_DOCKER_IMAGE="${_BUILD_DOCKER_IMAGE:-python:3.7-stretch}"
_BUILD_SCRIPT_NAME=/tmp/build_pyinstaller_gpustat_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<EOL
set -e
_BUILD_TMP="/tmp/gpustat"
mkdir -p \$_BUILD_TMP

###
# Setup Pyinstaller
###
mkdir -p $PYINSTALLER_PATH
cd $PYINSTALLER_PATH
git clone --branch resolve_tmpdir https://github.com/mzueva/pyinstaller.git
cd pyinstaller/bootloader/
python3 ./waf all

###
# Build gpustat-web
###
cd \$_BUILD_TMP
git clone https://github.com/sidoruka/gpustat-web
cd gpustat-web
git checkout 08a98395f04c1d8886882e1aec1d3b08cfa16411
cat > requirements.txt <<EOF
six==1.7
termcolor
ansi2html
cryptography==2.6.1
asyncssh==1.16.0
aiohttp==3.6.3
aiohttp_jinja2==1.5
jinja2==3.0.0
aiohttp-devtools==0.8
altgraph==0.17.3
charset-normalizer==2.1.0
EOF
pip3 install -r requirements.txt

python3 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py \
                                -y \
                                --hidden-import=charset_normalizer \
                                --clean \
                                --runtime-tmpdir $GPUSTAT_RUNTIME_TMP_DIR \
                                --distpath \$_BUILD_TMP/gpustat-web/dist \
                                --add-data \$_BUILD_TMP/gpustat-web/gpustat_web/template:template \
                                \$_BUILD_TMP/gpustat-web/gpustat_web/app.py

chmod +x \$_BUILD_TMP/gpustat-web/dist/app/app

###
# Build gpustat
###
cd \$_BUILD_TMP
git clone https://github.com/sidoruka/gpustat
cd gpustat 
git checkout b24cf0d56faf8313a2d8ea00c7869462ac247ec1
cat > requirements.txt <<EOF
nvidia-ml-py>=11.450.129
psutil==5.6.0
blessed==1.17.1
shtab==1.5.7
EOF
cat > _version.py <<'EOF'
version='1.0'
version_tuple=('1','0')
EOF
pip3 install -r requirements.txt

python3 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py \
                                -y \
                                --hidden-import=charset_normalizer \
                                --clean \
                                --runtime-tmpdir $GPUSTAT_RUNTIME_TMP_DIR \
                                --distpath \$_BUILD_TMP/gpustat/dist \
                                \$_BUILD_TMP/gpustat/gpustat/cli.py

chmod +x \$_BUILD_TMP/gpustat/dist/cli/cli

###
# Create a dist tarball
###
mkdir -p ${GPUSTAT_DIST_PATH}/gpustat
mv \$_BUILD_TMP/gpustat-web/dist/app ${GPUSTAT_DIST_PATH}/gpustat/
mv \$_BUILD_TMP/gpustat/dist/cli ${GPUSTAT_DIST_PATH}/gpustat/
cd $GPUSTAT_DIST_PATH
tar -zcf gpustat.tar.gz gpustat

EOL

docker pull $_BUILD_DOCKER_IMAGE &> /dev/null
docker run -i --rm \
           --env PYINSTALLER_PATH=$PYINSTALLER_PATH \
           --env GPUSTAT_DIST_PATH=$GPUSTAT_DIST_PATH \
           --env GPUSTAT_RUNTIME_TMP_DIR="'"$GPUSTAT_RUNTIME_TMP_DIR"'" \
           -v $GPUSTAT_DIST_PATH:$GPUSTAT_DIST_PATH \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           $_BUILD_DOCKER_IMAGE \
           bash $_BUILD_SCRIPT_NAME

#rm -f $_BUILD_SCRIPT_NAME
