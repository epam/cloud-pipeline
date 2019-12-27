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

bkp_dir="$1"
if [ ! -d "$bkp_dir" ]; then
    echo "[ERROR] Cannot find the backup directory at $bkp_dir"
    exit 1
fi
settings_bkp_file="$bkp_dir/api-settings-dump-$(date +%Y%m%d).tgz"

# Backup Settings
tar -czf $settings_bkp_file /opt/api/acl-template \
                            /opt/api/config \
                            /opt/api/folder-templates \
                            /opt/api/pipe-common \
                            /opt/api/pipe-templates \
                            /opt/api/resources \
                            /opt/api/scripts \
                            /opt/api/sshkey \
                            /opt/api/sso
