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

function update_perl {
    local file="$1"
    local interpreter="$2"
    sed -i "1s|.*|#!$interpreter|" "$file"
}

_WITH_BCL2FASTQ="$1"

if [ "$_WITH_BCL2FASTQ" != "true" ]; then
    echo "bcl2fastq18 will not be installed. If it is required - rebuild with \"--build-arg WITH_BCL2FASTQ18=true\""
    exit 0
fi

apt install -y  build-essential \
                libexpat1-dev \
                libexpat1 \
                expat \
                xsltproc \
                imagemagick \
                gnuplot

export _BCL2FASTQ_VERSION="v1-8-4"
export TMP=/tmp

# According to https://gist.github.com/moonwatcher/99994f244d721c5f4b78 (see header links)
cd $TMP
wget -q https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/bcl2fastq/bcl2fastq2-$_BCL2FASTQ_VERSION.deb -O bcl2fastq.deb
dpkg --install bcl2fastq.deb
rm -f bcl2fastq.deb

export PERLBREW_PERL_VERSION=perl-5.14.4
export PERLBREW_ROOT=/opt/perl5
export PERLBREW_BIN=/opt/perl5/bin/perlbrew
export CPANM_BIN=/opt/perl5/bin/cpanm
curl -kL http://install.perlbrew.pl | bash
$PERLBREW_BIN init
source /opt/perl5/etc/bashrc
$PERLBREW_BIN install $PERLBREW_PERL_VERSION --no-patchperl --notest -j $(nproc)
$PERLBREW_BIN use $PERLBREW_PERL_VERSION
$PERLBREW_BIN install-cpanm

export PATH=/opt/perl5/bin:$PERLBREW_ROOT/perls/$PERLBREW_PERL_VERSION/bin:$PATH
$CPANM_BIN --notest XML/Simple.pm

export INSTALL_DIR=$BCL2FASTQ_HOME/bcl2fastq-$_BCL2FASTQ_VERSION
mkdir -p $INSTALL_DIR

# Update perl interpreter for bcl2fastq, as it will not work with anything != 5.14.4
# Also symlink bcl2fastq executable to the default location into /opt/bcl2fastq. We do not install it directly into /opt/... as it breaks perl dependencies
for b2f_pl in $(ls /usr/local/bin/*.pl); do
    update_perl "$b2f_pl" "$PERLBREW_ROOT/perls/$PERLBREW_PERL_VERSION/bin/perl"
    ln -s "$b2f_pl" "$INSTALL_DIR/$(basename $b2f_pl)"
done

chmod -R g+rwx $INSTALL_DIR
chmod -R g+rwx $PERLBREW_ROOT
