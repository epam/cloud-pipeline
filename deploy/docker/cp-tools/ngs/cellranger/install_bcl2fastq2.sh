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

set -e

_WITH_BCL2FASTQ="$1"

if [ "$_WITH_BCL2FASTQ" != "true" ]; then
    echo "bcl2fastq2 will not be installed. If it is required - rebuild with \"--build-arg WITH_BCL2FASTQ2=true\""
    exit 0
fi

apt install -y build-essential g++ python-dev autotools-dev libicu-dev libbz2-dev zlib1g-dev unzip libboost-all-dev=1.58.0.1ubuntu1

_BCL2FASTQ_VERSION="v2-20-0"
export TMP=/tmp
cd $TMP
wget -q https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/bcl2fastq/bcl2fastq2-$_BCL2FASTQ_VERSION.tar.zip -O bcl2fastq2.tar.zip
unzip bcl2fastq2.tar.zip
tar -zxf bcl2fastq2-*.tar.gz
rm -f bcl2fastq2.tar.zip
rm -f bcl2fastq2-*.tar.gz

export SOURCE=${TMP}/bcl2fastq
export BUILD=${TMP}/bcl2fastq2-build
export INSTALL_DIR=$BCL2FASTQ_HOME/bcl2fastq-$_BCL2FASTQ_VERSION
export C_INCLUDE_PATH=/usr/include/x86_64-linux-gnu

# Patch for the newer version of boost 
# https://backwardincompatible.com/post/169360794395/compiling-illumina-bcl2fastq-220-on-ubuntu-with
cd $SOURCE
patch src/cxx/lib/io/Xml.cpp < /tmp/Xml.cpp.patch

mkdir -p ${BUILD}
cd ${BUILD}
chmod ugo+x ${SOURCE}/src/configure
chmod ugo+x ${SOURCE}/src/cmake/bootstrap/installCmake.sh
${SOURCE}/src/configure --prefix=${INSTALL_DIR}

make -j$(nproc)
make install

cd $INSTALL_DIR
rm -rf $SOURCE
rm -rf $BUILD

ln -s $INSTALL_DIR/bin/bcl2fastq /usr/local/bin/bcl2fastq
ln -s $INSTALL_DIR/bin/bcl2fastq /usr/local/bin/bcl2fastq2

chmod -R g+rwx $INSTALL_DIR
