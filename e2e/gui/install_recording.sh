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

set -e

_RECORDING="$1"

if [ "$_RECORDING" != "true" ]; then
    echo "Screen recording tool will not be installed. If it is required - rebuild with \"--build-arg RECORDING=true\""
    exit 0
fi

# Install screen recording tool vnc2flv
export TMP=/tmp
cd $TMP
wget -N https://files.pythonhosted.org/packages/1e/8e/40c71faa24e19dab555eeb25d6c07efbc503e98b0344f0b4c3131f59947f/vnc2flv-20100207.tar.gz -O vnc2flv-20100207.tar.gz
tar -zxvf vnc2flv-20100207.tar.gz
rm -f vnc2flv-20100207.tar.gz
export RECORD=${TMP}/vnc2flv-20100207
cd $RECORD
python setup.py install
