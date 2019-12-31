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

bkp_dir="${1:-/opt/api/logs/bkp/bkp-worker-wd}"
mkdir -p "$bkp_dir"
settings_bkp_file="$bkp_dir/cp-bkp-api-settings-dump-$(date +%Y%m%d).tgz"

# Backup Settings
tar -czf $settings_bkp_file /opt/api/config \
                            /opt/api/ext \
                            /opt/api/folder-templates \
                            /opt/api/keystore \
                            /opt/api/pipe-templates \
                            /opt/api/pki \
                            /opt/api/sso \
                            /opt/api/static
