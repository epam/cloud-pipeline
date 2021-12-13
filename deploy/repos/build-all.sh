#!/usr/bin/env bash

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

############################################################################################
# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
# Any package with "plus" ("+") symbol shall be uploded with spaces instead. E.g.: 
# 1. perl-Net-SSLeay-1.88-1.module_el8.3.0+410+ff426aa3.x86_64.rpm shall be uploaded as 
#    perl-Net-SSLeay-1.88-1.module_el8.3.0 410 ff426aa3.x86_64.rpm
# 2. Otherwise AWS S3 URL encodes the "+" to hex and yum can't find them. While spaces are replaced
#    with the "pluses"
# !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
############################################################################################

distros_debian=(debian:8 debian:9 ubuntu:16.04 ubuntu:18.04 ubuntu:19.04)
distros_centos=(centos:7 centos:8)

repo_bucket=cloud-pipeline-oss-builds
repo_bucket_prefix=tools/repos
current_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

for distro in "${distros_debian[@]}"; do
   bash $current_dir/build-repo-deb.sh --gpg-private "$gpg_private" \
                                       --gpg-public "$gpg_public" \
                                       --os "$distro" \
                                       --bucket "$repo_bucket" \
                                       --prefix "$repo_bucket_prefix" \
                                       $current_dir/download-scripts/debian/*
done

for distro in "${distros_centos[@]}"; do
   IFS=':' read -ra _os_parts <<< "$distro"
   _os_name="${_os_parts[0]}"
   _os_version="${_os_parts[1]}"
   bash $current_dir/build-repo-rpm.sh --os "$distro" \
                                       --bucket "$repo_bucket" \
                                       --prefix "$repo_bucket_prefix" \
                                       $current_dir/download-scripts/rpm${_os_version}/*
done
