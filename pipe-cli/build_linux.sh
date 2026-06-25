#!/bin/bash

# Copyright 2017-2020 EPAM Systems, Inc. (https://www.epam.com/)
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

cd $PIPE_CLI_SOURCES_DIR
PIPE_COMMIT_HASH=$(git log --pretty=tformat:"%H" -n1 .)
cd -

_BUILD_PIPE_OMICS_SCRIPT_NAME=/tmp/build_pipe_omics_pytinstaller_linux_$(date +%s).sh
_BUILD_PIPE_OMICS_DOCKER_IMAGE="${_BUILD_PIPE_OMICS_DOCKER_IMAGE:-lifescience/cloud-pipeline:pipe-cli-rocky8-py312}"

# This dir will be mounted to the docker container to build pipe-omics and then to the container
# where pipe-cli will be built, to copy pipe-omics inside a pipe-cli
_PIPE_OMICS_BUILD_DIST_DIR="/tmp/pipe-omics"
mkdir -p $_PIPE_OMICS_BUILD_DIST_DIR

# NOTE: Need to install and configure openssl11 to use during python compilation (without it pip will throw ssl error)
cat > "$_BUILD_PIPE_OMICS_SCRIPT_NAME" <<EOL
###
# Build pipe-omics
###

python3 -m pip install -r \${PIPE_OMICS_SOURCES_DIR}/requirements.txt
pip install 'setuptools==68.2.2'

cd \$PIPE_OMICS_SOURCES_DIR && \
pyinstaller \
  --paths "\${PIPE_OMICS_SOURCES_DIR}" \
  --hidden-import=itertools \
  --hidden-import=collections \
  --hidden-import=base64 \
  --hidden-import=math \
  --hidden-import=reprlib \
  --hidden-import=functools \
  --hidden-import=re \
  --hidden-import=subprocess \
  -y \
  --clean \
  --distpath $_PIPE_OMICS_BUILD_DIST_DIR/dist \
  \${PIPE_OMICS_SOURCES_DIR}/pipe-omics.py

chmod +x /tmp/pipe-omics/dist/pipe-omics/pipe-omics
EOL

docker pull "$_BUILD_PIPE_OMICS_DOCKER_IMAGE" &> /dev/null
docker run -i --rm \
           -v "$PIPE_CLI_SOURCES_DIR":"$PIPE_CLI_SOURCES_DIR" \
           -v "$PIPE_CLI_LINUX_DIST_DIR":"$PIPE_CLI_LINUX_DIST_DIR" \
           -v "$_BUILD_PIPE_OMICS_SCRIPT_NAME":"$_BUILD_PIPE_OMICS_SCRIPT_NAME" \
           -v "$_PIPE_OMICS_BUILD_DIST_DIR":"$_PIPE_OMICS_BUILD_DIST_DIR" \
           --env PIPE_CLI_SOURCES_DIR="$PIPE_CLI_SOURCES_DIR" \
           --env PIPE_OMICS_SOURCES_DIR="$PIPE_OMICS_SOURCES_DIR" \
           --env PIPE_CLI_LINUX_DIST_DIR="$PIPE_CLI_LINUX_DIST_DIR" \
           "$_BUILD_PIPE_OMICS_DOCKER_IMAGE" \
           bash "$_BUILD_PIPE_OMICS_SCRIPT_NAME"

rm -f "$_BUILD_PIPE_OMICS_SCRIPT_NAME"

_BUILD_SCRIPT_NAME=/tmp/build_pytinstaller_linux_$(date +%s).sh
_BUILD_DOCKER_IMAGE="${_BUILD_DOCKER_IMAGE:-lifescience/cloud-pipeline:pipe-cli-rocky8-py312}"

cat >$_BUILD_SCRIPT_NAME <<EOL

###
# Resolve bundle os
###

build_os_id=''
build_os_version_id=''
if [ -f "/etc/os-release" ]; then
    source /etc/os-release
    build_os_id="\${ID}"
    build_os_version_id="\${VERSION_ID}"
elif [ -f "/etc/centos-release" ]; then
    build_os_id="centos"
    build_os_version_id=\$(cat /etc/centos-release | tr -dc '0-9.'|cut -d \. -f1)
fi

###
# Setup common dependencies
###
pip install -r ${PIPE_CLI_SOURCES_DIR}/requirements.txt

###
# Build pipe fuse
###

if [[ "\$build_os_id" == "centos" ]] && [[ "\$build_os_version_id" == "6" ]]; then
  libfuse_version="2.8.3"
else
  libfuse_version="2.9.2"
fi
cp ${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.\${libfuse_version} ${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.frozen

pip install -r ${PIPE_MOUNT_SOURCES_DIR}/requirements.txt
pip install 'setuptools==68.2.2'
cd $PIPE_MOUNT_SOURCES_DIR && \
pyinstaller \
                                --paths "${PIPE_MOUNT_SOURCES_DIR}" \
                                --paths "${PIPE_CLI_SOURCES_DIR}" \
                                --collect-submodules pipefuse \
                                --collect-submodules src \
                                --collect-submodules google.auth \
                                --collect-submodules google.oauth2 \
                                --collect-submodules google.cloud \
                                --collect-submodules google.resumable_media \
                                --collect-data botocore \
                                --hidden-import=fuse \
                                --hidden-import=cachetools \
                                --hidden-import=intervals \
                                --hidden-import=pygtrie \
                                --additional-hooks-dir="${PIPE_MOUNT_SOURCES_DIR}/hooks" \
                                -y \
                                --clean \
                                --distpath /tmp/mount/dist \
                                --add-data "${PIPE_MOUNT_SOURCES_DIR}/libfuse/libfuse.so.frozen:libfuse" \
                                ${PIPE_MOUNT_SOURCES_DIR}/pipe-fuse.py

chmod +x /tmp/mount/dist/pipe-fuse/pipe-fuse

###
# Build pipe
###
function build_pipe {
    local distpath="\$1"
    local onefile="\$2"

    version_file="${PIPE_CLI_SOURCES_DIR}/src/version.py"
    sed -i '/__bundle_info__/d' \$version_file

    bundle_type="one-folder"
    [ "\$onefile" ] && bundle_type="one-file"

    echo "__bundle_info__ = { 'bundle_type': '\$bundle_type', 'build_os_id': '\$build_os_id', 'build_os_version_id': '\$build_os_version_id' }" >> \$version_file

    sed -i '/__component_version__/d' \$version_file
    echo "__component_version__='\${PIPE_COMMIT_HASH}'" >> \$version_file

    cd $PIPE_CLI_SOURCES_DIR
    pyinstaller \
                                    --paths "$PIPE_CLI_SOURCES_DIR" \
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
                                    --hidden-import=pygtrie \
                                    --hidden-import=pypac \
                                    --hidden-import=treelib \
                                    --hidden-import=psutil \
                                    --hidden-import=scp \
                                    --hidden-import=tzlocal \
                                    --hidden-import=prettytable \
                                    --hidden-import=jwt \
                                    --hidden-import=paramiko \
                                    --add-data "$PIPE_CLI_SOURCES_DIR/res/effective_tld_names.dat.txt:tld/res/" \
                                    --additional-hooks-dir="$PIPE_CLI_SOURCES_DIR/hooks" \
                                    -y \
                                    --clean \
                                    --distpath \$distpath \
                                    --add-data /tmp/mount/dist/pipe-fuse:mount \
                                    --add-data /tmp/pipe-omics/dist/pipe-omics:pipe-omics \
                                    ${PIPE_CLI_SOURCES_DIR}/pipe.py \$onefile
}
build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-file --onefile
build_pipe $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder
tar -zcf $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder/pipe.tar.gz \
        -C $PIPE_CLI_LINUX_DIST_DIR/dist/dist-folder \
        pipe

EOL

docker pull $_BUILD_DOCKER_IMAGE &> /dev/null
docker run -i --rm \
           -v $PIPE_CLI_SOURCES_DIR:$PIPE_CLI_SOURCES_DIR \
           -v $PIPE_CLI_LINUX_DIST_DIR:$PIPE_CLI_LINUX_DIST_DIR \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           -v $_PIPE_OMICS_BUILD_DIST_DIR:$_PIPE_OMICS_BUILD_DIST_DIR \
           --env PIPE_CLI_SOURCES_DIR=$PIPE_CLI_SOURCES_DIR \
           --env PIPE_MOUNT_SOURCES_DIR=$PIPE_MOUNT_SOURCES_DIR \
           --env PIPE_CLI_LINUX_DIST_DIR=$PIPE_CLI_LINUX_DIST_DIR \
           --env PIPE_COMMIT_HASH=$PIPE_COMMIT_HASH \
           $_BUILD_DOCKER_IMAGE \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
