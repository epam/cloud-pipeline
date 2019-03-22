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

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_linux_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<EOL

mkdir -p $PYINSTALLER_PATH

cd $PYINSTALLER_PATH

git clone --single-branch --branch resolve_tmpdir https://github.com/mzueva/pyinstaller.git
cd pyinstaller/bootloader/

python2 ./waf all

#echo "Runtime tmpdir: $PIPE_CLI_RUNTIME_TMP_DIR"

python2 -m pip install  'dis3==0.1.3' \
                        'altgraph==0.16.1' \
                        'click==6.7' \
                        'PTable==0.9.2' \
                        'requests==2.18.4' \
                        'pytz==2018.3' \
                        'tzlocal==1.5.1' \
                        'mock==2.0.0' \
                        'requests_mock==1.4.0' \
                        'pytest==3.2.5' \
                        'pytest-cov==2.5.1' \
                        'boto3==1.6.9' \
                        'botocore==1.9.9' \
                        'future' \
                        'PyJWT==1.6.1' \
                        'pypac==0.8.1' \
                        'beautifulsoup4==4.6.1' \
                        'azure-storage-blob==1.5.0' && \
cd $PIPE_CLI_SOURCES_DIR && \
python2 $PYINSTALLER_PATH/pyinstaller/pyinstaller.py \
                                --add-data "$PIPE_CLI_SOURCES_DIR/res/effective_tld_names.dat.txt:tld/res/" \
                                --onefile \
                                --hidden-import=UserList \
                                --hidden-import=UserString \
                                --hidden-import=commands \
                                --hidden-import=ConfigParser \
                                --hidden-import=UserDict \
                                --hidden-import=itertools \
                                --hidden-import=collections \
                                --hidden-import=future.backports.misc \
                                --hidden-import=commands \
                                --hidden-import=base64 \
                                --hidden-import=__builtin__ \
                                --hidden-import=math \
                                --hidden-import=reprlib \
                                --hidden-import=functools \
                                --hidden-import=re \
                                --hidden-import=subprocess \
                                -y \
                                --clean \
                                --runtime-tmpdir $PIPE_CLI_RUNTIME_TMP_DIR \
                                --distpath $PIPE_CLI_LINUX_DIST_DIR/dist \
                                ${PIPE_CLI_SOURCES_DIR}/pipe.py
EOL

docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:$PIPE_CLI_SOURCES_DIR \
           -v $PIPE_CLI_LINUX_DIST_DIR:$PIPE_CLI_LINUX_DIST_DIR \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           --env PIPE_CLI_SOURCES_DIR=$PIPE_CLI_SOURCES_DIR \
           --env PIPE_CLI_LINUX_DIST_DIR=$PIPE_CLI_LINUX_DIST_DIR \
           --env PIPE_CLI_RUNTIME_TMP_DIR="'"$PIPE_CLI_RUNTIME_TMP_DIR"'" \
           --env PYINSTALLER_PATH=$PYINSTALLER_PATH \
           python:2.7-stretch \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
