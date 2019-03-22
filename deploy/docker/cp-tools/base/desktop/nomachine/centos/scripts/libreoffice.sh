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

# FIXME 1. Use own S3 distribution URL
if [ -z "$LIBREOFFICE_DISTR_URL" ]; then
    LIBREOFFICE_DISTR_URL="http://mirror.clarkson.edu/tdf/libreoffice/stable/6.1.5/rpm/x86_64/LibreOffice_6.1.5_Linux_x86-64_rpm.tar.gz"
fi

cd /opt && \
wget -q "$LIBREOFFICE_DISTR_URL" -O LibreOffice.tar.gz && \
tar -zxvf LibreOffice.tar.gz && \
yum localinstall -y LibreOffice*/RPMS/*.rpm && \
rm -rf LibreOffice*