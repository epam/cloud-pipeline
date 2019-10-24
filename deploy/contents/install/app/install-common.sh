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


function check_installed {
    command -v "$1" >/dev/null 2>&1
    return $?
}

print_info "Installing common packages"
yum install -y \
            curl \
            wget \
            git

git config --global http.sslVerify "false"

if ! check_installed "jq"; then
    print_info "Installing jq"
    wget -q "https://cloud-pipeline-oss-builds.s3.amazonaws.com/tools/jq/jq-1.6/jq-linux64" -O /usr/bin/jq
    chmod +x /usr/bin/jq
fi
