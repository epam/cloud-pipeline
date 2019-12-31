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

bkp_dir="${1:-/var/lib/postgresql/data/bkp/bkp-worker-wd}"
mkdir -p "$bkp_dir"
sql_bkp_file="$bkp_dir/cp-bkp-api-db-dump-$(date +%Y%m%d).sql.gz"

# Backup DB
pg_dump -U pipeline pipeline | gzip > $sql_bkp_file
