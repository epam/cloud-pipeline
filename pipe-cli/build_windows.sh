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

#######################################
# Step 1: Build NTLM APS with Python 2
#######################################

CP_PYINSTALL_WIN32_PY2_DOCKER="${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline:pyinstaller-win32-py2"
docker pull $CP_PYINSTALL_WIN32_PY2_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_PYINSTALL_WIN32_PY2_DOCKER image, it will be rebuilt"
    docker build docker/win32 -t $CP_PYINSTALL_WIN32_PY2_DOCKER
fi

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_win32_py2_$(date +%s).sh
export PIPE_CLI_BUILD_VERSION="${PIPE_CLI_BUILD_VERSION:-0}"

cat >$_BUILD_SCRIPT_NAME <<'EOL'

cat > /tmp/ntlmp-win-version-info.txt <<< "$(envsubst < /pipe-cli/res/ntlmp-win-version-info.txt)"

pip install --upgrade 'setuptools<=45.1.0' && \
pip install -r /pipe-cli/requirements.txt

cd /tmp
git clone https://github.com/sidoruka/ntlmaps.git && \
cd ntlmaps && \
git checkout 5f798a88369eddbe732364b98fbd445aacc809d0

pyinstaller main.py -y \
        --clean \
        --distpath /tmp/ntlmaps/dist \
        -p /tmp/ntlmaps/lib \
        --add-data "./server.cfg;./" \
        --name ntlmaps \
        --version-file /tmp/ntlmp-win-version-info.txt

cp -r /tmp/ntlmaps/dist/ntlmaps /pipe-cli/
EOL

docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:/pipe-cli \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           -e PIPE_CLI_MAJOR_VERSION=$PIPE_CLI_MAJOR_VERSION \
           -e PIPE_CLI_MINOR_VERSION=$PIPE_CLI_MINOR_VERSION \
           -e PIPE_CLI_PATCH_VERSION=$PIPE_CLI_PATCH_VERSION \
           -e PIPE_CLI_BUILD_VERSION=$(cut -d. -f1 <<< "$PIPE_CLI_BUILD_VERSION") \
           $CP_PYINSTALL_WIN32_PY2_DOCKER \
           bash $_BUILD_SCRIPT_NAME

_distr_path_ntlmaps="${PIPE_CLI_SOURCES_DIR}/ntlmaps"
if [ ! -f "$_distr_path_ntlmaps" ] && [ ! -d "$_distr_path_ntlmaps" ] ; then
    echo "[ERROR] 'ntlmaps' cannot be found at ${_distr_path_ntlmaps}." \
         "Which means there were errors during compilation, please see any output above." \
         "Will not proceed with the mount/pipe compilation."
    exit 1
fi

rm -f $_BUILD_SCRIPT_NAME


#######################################
# Step 1: Build pipe-omics with python3.10
#######################################
CP_PYINSTALL_WIN64_PY310_DOCKER="${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline:pyinstaller-win64-py310"
docker pull $CP_PYINSTALL_WIN64_PY310_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_PYINSTALL_WIN64_PY310_DOCKER image, it will be rebuilt"
    docker build docker/win64-py310 -t $CP_PYINSTALL_WIN64_PY310_DOCKER
fi

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_win64_py310_$(date +%s).sh

cat > $_BUILD_SCRIPT_NAME <<EOL

version_file="/pipe-cli/src/version.py"
sed -i '/__component_version__/d' \$version_file
echo "__component_version__='\${PIPE_COMMIT_HASH}'" >> \$version_file

cat > /tmp/pipe-win-version-info.txt <<< "\$(envsubst < /pipe-cli/res/pipe-win-version-info.txt)" && \
pip install --upgrade 'setuptools<=45.1.0' && \
pip install -r /pipe-cli/pipe-omics/requirements.txt && \
pip install pywin32==302 && \
cd /pipe-cli/pipe-omics && \
pyinstaller \
  --paths "/pipe-cli/pipe-omics" \
  --hidden-import=itertools \
  --hidden-import=boto3 \
  --hidden-import=botocore \
  --hidden-import=collections \
  --hidden-import=base64 \
  --hidden-import=math \
  --hidden-import=reprlib \
  --hidden-import=functools \
  --hidden-import=re \
  --hidden-import=subprocess \
  --hidden-import=charset_normalizer.md__mypyc \
  --hidden-import=chardet \
  --add-data "/wine/drive_c/Python310/Lib/site-packages/botocore/data;botocore/data" \
  --version-file /tmp/pipe-win-version-info.txt \
  -y \
  --clean \
  --distpath /tmp/pipe-omics/dist \
  /pipe-cli/pipe-omics/pipe-omics.py

chmod +x /tmp/pipe-omics/dist/pipe-omics
cp -r /tmp/pipe-omics/dist/pipe-omics /pipe-cli/pipe-omics/dist
EOL

cd $PIPE_CLI_SOURCES_DIR
PIPE_COMMIT_HASH=$(git log --pretty=tformat:"%H" -n1 .)
cd -

docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:/pipe-cli \
           -v $PIPE_CLI_WIN_DIST_DIR:/pipe-cli/dist/win64 \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           -e PIPE_CLI_MAJOR_VERSION=$PIPE_CLI_MAJOR_VERSION \
           -e PIPE_CLI_MINOR_VERSION=$PIPE_CLI_MINOR_VERSION \
           -e PIPE_CLI_PATCH_VERSION=$PIPE_CLI_PATCH_VERSION \
           -e PIPE_CLI_BUILD_VERSION=$(cut -d. -f1 <<< "$PIPE_CLI_BUILD_VERSION") \
           -e PIPE_COMMIT_HASH=$PIPE_COMMIT_HASH \
           $CP_PYINSTALL_WIN64_PY310_DOCKER \
           bash $_BUILD_SCRIPT_NAME

_distr_path_pipe_omics="${PIPE_CLI_SOURCES_DIR}/pipe-omics/dist"
if [ ! -f "$_distr_path_pipe_omics" ] && [ ! -d "$_distr_path_pipe_omics" ] ; then
    echo "[ERROR] 'pipe-omics/dist' cannot be found at ${_distr_path_pipe_omics}." \
         "Which means there were errors during compilation, please see any output above." \
         "Will not proceed with the mount/pipe compilation."
    exit 1
fi

rm -f $_BUILD_SCRIPT_NAME

#######################################
# Step 2: pipe CLI
#######################################

CP_PYINSTALL_WIN64_DOCKER="${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline:pyinstaller-win64"
docker pull $CP_PYINSTALL_WIN64_DOCKER
if [ $? -ne 0 ]; then
    echo "Unable to pull $CP_PYINSTALL_WIN64_DOCKER image, it will be rebuilt"
    docker build docker/win64 -t $CP_PYINSTALL_WIN64_DOCKER
fi

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_win64_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<'EOL'

version_file="/pipe-cli/src/version.py"
sed -i '/__component_version__/d' \$version_file
echo "__component_version__='\${PIPE_COMMIT_HASH}'" >> \$version_file

cat > /tmp/pipe-win-version-info.txt <<< "$(envsubst < /pipe-cli/res/pipe-win-version-info.txt)" && \
pip install --upgrade 'setuptools<=45.1.0' && \
pip install -r /pipe-cli/requirements.txt && \
pip install pywin32==300 && \
cd /pipe-cli/mount && \
cp libfuse/dokanfuse1.dll.1.5.0.3000 libfuse/dokanfuse1.dll.frozen && \
pip install -r /pipe-cli/mount/requirements.txt && \
pyinstaller --paths "/pipe-cli" \
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
            --hidden-import=pkg_resources.py2_warn \
            --additional-hooks-dir="/pipe-cli/mount/hooks" \
            -y \
            --clean \
            --distpath /tmp/mount/dist \
            --add-data "/pipe-cli/mount/libfuse/dokanfuse1.dll.frozen;libfuse" \
            /pipe-cli/mount/pipe-fuse.py && \
cd /pipe-cli && \
pyinstaller --add-data "/pipe-cli/res/effective_tld_names.dat.txt;tld/res/" \
            --hidden-import=boto3 \
            --hidden-import=pytz \
            --hidden-import=tkinter \
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
            --hidden-import=pkg_resources.py2_warn \
            --additional-hooks-dir="/pipe-cli/hooks" \
            -y \
            --clean \
            --workpath /tmp \
            --distpath /pipe-cli/dist/win64 \
            pipe.py \
            --add-data "/pipe-cli/ntlmaps;ntlmaps" \
            --add-data "/tmp/mount/dist/pipe-fuse;mount" \
            --add-data "/pipe-cli/pipe-omics/dist;pipe-omics" \
            --version-file /tmp/pipe-win-version-info.txt \
            --icon /pipe-cli/res/cloud-pipeline.ico \
            --name pipe-cli && \
cd /pipe-cli/dist/win64 && \
cp /pipe-cli/pipe.bat pipe-cli/pipe.bat && \
cp /pipe-cli/pipe.bat pipe-cli/pipe.exe.bat && \
mv pipe-cli pipe && \
zip -r -q pipe.zip pipe
EOL

cd $PIPE_CLI_SOURCES_DIR
PIPE_COMMIT_HASH=$(git log --pretty=tformat:"%H" -n1 .)
cd -

docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:/pipe-cli \
           -v $PIPE_CLI_WIN_DIST_DIR:/pipe-cli/dist/win64 \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           -e PIPE_CLI_MAJOR_VERSION=$PIPE_CLI_MAJOR_VERSION \
           -e PIPE_CLI_MINOR_VERSION=$PIPE_CLI_MINOR_VERSION \
           -e PIPE_CLI_PATCH_VERSION=$PIPE_CLI_PATCH_VERSION \
           -e PIPE_CLI_BUILD_VERSION=$(cut -d. -f1 <<< "$PIPE_CLI_BUILD_VERSION") \
           -e PIPE_COMMIT_HASH=$PIPE_COMMIT_HASH \
           $CP_PYINSTALL_WIN64_DOCKER \
           bash $_BUILD_SCRIPT_NAME

_distr_path_pipe="${PIPE_CLI_WIN_DIST_DIR}/pipe.zip"
if [ ! -f "$_distr_path_pipe" ] && [ ! -d "$_distr_path_pipe" ] ; then
    echo "[ERROR] 'pipe.zip' cannot be found at ${_distr_path_pipe}." \
         "Which means there were errors during compilation, please see any output above."
    exit 1
fi

rm -f $_BUILD_SCRIPT_NAME
