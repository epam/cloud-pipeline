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

CP_PYINSTALL_WIN64_DOCKER="${CP_PYINSTALL_WIN64_DOCKER:-lifescience/cloud-pipeline:pyinstaller-win64-py312}"
docker pull $CP_PYINSTALL_WIN64_DOCKER

cd $PIPE_CLI_SOURCES_DIR
PIPE_COMMIT_HASH=$(git log --pretty=tformat:"%H" -n1 .)
cd -

#######################################
# Step 1: Build pipe-omics with python3
#######################################
_BUILD_SCRIPT_NAME=/tmp/build_pyinstaller_pipe_omics_win64_$(date +%s).sh

cat > $_BUILD_SCRIPT_NAME <<EOL

version_file="/pipe-cli/src/version.py"
sed -i '/__component_version__/d' \$version_file
echo "__component_version__='\${PIPE_COMMIT_HASH}'" >> \$version_file

cat > /tmp/pipe-win-version-info.txt <<< "\$(envsubst < /pipe-cli/res/pipe-win-version-info.txt)" && \
pip install -r /pipe-cli/pipe-omics/requirements.txt && \
pip install pywin32==306 && \
pip install 'setuptools==68.2.2' && \
cd /pipe-cli/pipe-omics && \
rm -rf /tmp/pipe-omics/dist && \
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
  --collect-data botocore \
  --version-file /tmp/pipe-win-version-info.txt \
  -y \
  --clean \
  --distpath /tmp/pipe-omics/dist \
  /pipe-cli/pipe-omics/pipe-omics.py

chmod +x /tmp/pipe-omics/dist/pipe-omics
cp -r /tmp/pipe-omics/dist/pipe-omics /pipe-cli/pipe-omics/dist
EOL

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
_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_pipe_cli_win64_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<'EOL'

version_file="/pipe-cli/src/version.py"
sed -i '/__component_version__/d' $version_file
echo "__component_version__='${PIPE_COMMIT_HASH}'" >> $version_file

cat > /tmp/pipe-win-version-info.txt <<< "$(envsubst < /pipe-cli/res/pipe-win-version-info.txt)" && \
pip install -r /pipe-cli/requirements.txt && \
pip install pywin32==306 && \
cd /pipe-cli/mount && \
cp libfuse/dokanfuse1.dll.1.5.0.3000 libfuse/dokanfuse1.dll.frozen && \
pip install -r /pipe-cli/mount/requirements.txt && \
pip install 'setuptools==68.2.2' && \
pyinstaller --paths "/pipe-cli/mount" \
            --paths "/pipe-cli" \
            --collect-submodules pipefuse \
            --collect-submodules google.auth \
            --collect-submodules google.oauth2 \
            --collect-submodules google.cloud \
            --collect-submodules google.resumable_media \
            --collect-data botocore \
            --hidden-import=itertools \
            --hidden-import=collections \
            --hidden-import=base64 \
            --hidden-import=math \
            --hidden-import=reprlib \
            --hidden-import=functools \
            --hidden-import=re \
            --hidden-import=subprocess \
            --hidden-import=cachetools \
            --hidden-import=intervals \
            --hidden-import=pygtrie \
            --additional-hooks-dir="/pipe-cli/mount/hooks" \
            -y \
            --clean \
            --distpath /tmp/mount/dist \
            --add-data "/pipe-cli/mount/libfuse/dokanfuse1.dll.frozen;libfuse" \
            /pipe-cli/mount/pipe-fuse.py && \
cd /pipe-cli && \
pyinstaller --paths "/pipe-cli" \
            --add-data "/pipe-cli/res/effective_tld_names.dat.txt;tld/res/" \
            --collect-submodules src \
            --collect-submodules google.auth \
            --collect-submodules google.oauth2 \
            --collect-submodules google.cloud \
            --collect-submodules google.resumable_media \
            --collect-submodules azure.core \
            --collect-submodules azure.storage \
            --collect-data botocore \
            --collect-data azure \
            --collect-data tldextract \
            --hidden-import=boto3 \
            --hidden-import=pytz \
            --hidden-import=tkinter \
            --hidden-import=itertools \
            --hidden-import=collections \
            --hidden-import=base64 \
            --hidden-import=math \
            --hidden-import=reprlib \
            --hidden-import=functools \
            --hidden-import=re \
            --hidden-import=subprocess \
            --hidden-import=pygtrie \
            --hidden-import=pypac \
            --hidden-import=treelib \
            --hidden-import=psutil \
            --hidden-import=scp \
            --hidden-import=tzlocal \
            --hidden-import=prettytable \
            --hidden-import=jwt \
            --hidden-import=paramiko \
            --additional-hooks-dir="/pipe-cli/hooks" \
            -y \
            --clean \
            --workpath /tmp \
            --distpath /pipe-cli/dist/win64 \
            pipe.py \
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

rm -f $_BUILD_SCRIPT_NAME
