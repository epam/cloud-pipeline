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

yum install -y  make \
                autoconf \
                zlib-devel \
                bzip2-devel \
                xz-devel

yum install -y  autoconf \
                gettext-devel \
                openssl-devel \
                perl-CPAN \
                perl-devel \
                curl-devel

wget https://github.com/git/git/archive/v2.15.0.tar.gz -O git.tar.gz && \
    tar -zxf git.tar.gz && \
    cd git-2.15.0 && \
    make configure && \
    ./configure --prefix=/usr/local && \
    make install && \
    cd .. && \
    rm -f git.tar.gz && \
    rm -rf git-2.15.0