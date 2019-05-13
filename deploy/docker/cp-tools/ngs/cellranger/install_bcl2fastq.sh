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

_WITH_BCL2FASTQ="$1"

if [ "$_WITH_BCL2FASTQ" != "true" ]; then
    echo "bcl2fastq will not be installed. If it is required - rebuild with \"--build-arg WITH_BCL2FASTQ=true\""
    exit 0
fi

apt install -y build-essential libbz2-dev libxml2-dev libboost-all-dev

_BCL2FASTQ_VERSION="v2-20-0"
export TMP=/tmp
cd $TMP
wget ftp://webdata2:webdata2@ussd-ftp.illumina.com/downloads/software/bcl2fastq/bcl2fastq2-$_BCL2FASTQ_VERSION-tar.zip -O bcl2fastq2.tar.zip
unzip bcl2fastq2.tar.zip
tar -zxf bcl2fastq2-*.tar.gz
rm -f bcl2fastq2.tar.zip
rm -f bcl2fastq2-*.tar.gz

export SOURCE=${TMP}/bcl2fastq
export BUILD=${TMP}/bcl2fastq2-build
export INSTALL_DIR=/opt/bcl2fastq-$_BCL2FASTQ_VERSION
export C_INCLUDE_PATH=/usr/include/x86_64-linux-gnu

mkdir -p ${BUILD}
cd ${BUILD}
chmod ugo+x ${SOURCE}/src/configure
chmod ugo+x ${SOURCE}/src/cmake/bootstrap/installCmake.sh
${SOURCE}/src/configure --prefix=${INSTALL_DIR}

make -j$(nproc)
make install

apt remove -y build-essential libbz2-dev libxml2-dev libboost-all-dev=1.58.0.1ubuntu1
cd $INSTALL_DIR
rm -rf $SOURCE
rm -rf $BUILD
