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

_BUILD_SCRIPT_NAME=/tmp/build_dav_linux_$(date +%s).sh

cat >$_BUILD_SCRIPT_NAME <<EOL
export GOPATH=/root

apt-get update && \
apt-get install golang -y && \
apt-get install git -y && \
apt-get install fuse -y && \
apt-get install wget -y && \
cd && \
mkdir pkg bin src && \
cd src && \
mkdir -p github.com/miquels && \
cd github.com/miquels && \
git clone https://github.com/miquels/webdavfs && \
cd webdavfs && \
go get && \
GOOS=linux GOARCH=amd64 go build -o webdavfs_linux_amd64

mkdir /root/fs_drv && \
cp webdavfs_linux_amd64 /root/fs_drv/wfs && \
wget "https://github.com/kahing/goofys/releases/download/v0.19.0/goofys" -O /root/fs_drv/s3fs && \
wget "https://github.com/stedolan/jq/releases/download/jq-1.6/jq-linux64" -O /root/fs_drv/jq && \
cd /root/fs_drv/ && \
chmod +x * && \
tar -zcvf /root/fs.tgz .

cd $DAV_SOURCES_DIR
export DAV_BUILD_OUTPUT_PATH="$DAV_DIST_DIR/pipe-mount"
bash dav-build --base64 /root/fs.tgz


EOL

docker run -i --rm \
           -v $DAV_SOURCES_DIR:$DAV_SOURCES_DIR \
           -v $DAV_DIST_DIR:$DAV_DIST_DIR \
           -v $_BUILD_SCRIPT_NAME:$_BUILD_SCRIPT_NAME \
           --env DAV_SOURCES_DIR=$DAV_SOURCES_DIR \
           --env DAV_DIST_DIR=$DAV_DIST_DIR \
           ubuntu:16.04 \
           bash $_BUILD_SCRIPT_NAME

rm -f $_BUILD_SCRIPT_NAME
