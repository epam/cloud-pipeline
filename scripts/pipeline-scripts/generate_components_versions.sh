#!/bin/bash

# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

project_root_dir="$1"
versions_file_path="$2"

components_list="pipe-cli data-transfer-service cloud-pipeline-webdav-client"
components_json=""
for component in $components_list; do
    echo $component
    component_commit_hash=$(git log --pretty=tformat:"%H" -n1 "${project_root_dir}/$component")
    components_json="${components_json}, \"$component\": \"$component_commit_hash\""
done

components_json=$(echo "$components_json" | sed 's/,//')
echo "{ $components_json }" > "$versions_file_path"