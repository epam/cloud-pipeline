#!/bin/bash

# Copyright 2017-2021 EPAM Systems, Inc. (https://www.epam.com/)
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

# The script publishes test reports to github.
#
# Supports multiple source and destination paths delimited with commas.
#
# Examples:
#
# ./e2e/scripts/publish_to_github.sh --username "USERNAME" \
#                                    --password "PASSWORD" \
#                                    --source "/local/absolute/path/to/report/source/file/or/dir" \
#                                    --destination "repository/relative/path/to/report/file/or/dir/publish/path" \
#                                    --tag "Specific test results"
#
# ./e2e/scripts/publish_to_github.sh --username "USERNAME" \
#                                    --password "PASSWORD" \
#                                    --source "/dir-1/source/path,/dir-2/source/path,/file/source/path" \
#                                    --destination "dir-1/publish/path,dir-2/publish/path,file/publish/path" \
#                                    --tag "Multiple test results"
#

while [[ $# -gt 0 ]]
do
    key="$1"

    case $key in
        --username)
        GITHUB_EXPORT_USERNAME="$2"
        shift
        shift
        ;;
        --password)
        GITHUB_EXPORT_PASSWORD="$2"
        shift
        shift
        ;;
        --source)
        GITHUB_EXPORT_SOURCE="$2"
        shift
        shift
        ;;
        --destination)
        GITHUB_EXPORT_DESTINATION="$2"
        shift
        shift
        ;;
        --tag)
        GITHUB_EXPORT_TAG="$2"
        shift
        shift
        ;;
        --tmpdir)
        GITHUB_EXPORT_TMPDIR="$2"
        shift
        shift
        ;;
        --branch)
        GITHUB_EXPORT_BRANCH="$2"
        shift
        shift
        ;;
    esac
done

GITHUB_EXPORT_TMPDIR="${GITHUB_EXPORT_TMPDIR:-cloud-pipeline-tmp}"
GITHUB_EXPORT_BRANCH="${GITHUB_EXPORT_BRANCH:-e2e-reports}"
GITHUB_EXPORT_TAG="${GITHUB_EXPORT_TAG:-Test Results}"

GITHUB_EXPORT_USERNAME="${GITHUB_EXPORT_USERNAME}"
GITHUB_EXPORT_PASSWORD="${GITHUB_EXPORT_PASSWORD}"
GITHUB_EXPORT_SOURCE="${GITHUB_EXPORT_SOURCE}"
GITHUB_EXPORT_DESTINATION="${GITHUB_EXPORT_DESTINATION}"

if [[ -z "$GITHUB_EXPORT_USERNAME" ]]; then
    echo "Option --username is missing"
    exit 1
fi
if [[ -z "$GITHUB_EXPORT_PASSWORD" ]]; then
    echo "Option --password is missing"
    exit 1
fi
if [[ -z "$GITHUB_EXPORT_SOURCE" ]]; then
    echo "Option --source is missing"
    exit 1
fi
if [[ -z "$GITHUB_EXPORT_DESTINATION" ]]; then
    echo "Option --destination is missing"
    exit 1
fi

IFS=',' read -r -a GITHUB_EXPORT_SOURCE_ARRAY <<< "$GITHUB_EXPORT_SOURCE"
IFS=',' read -r -a GITHUB_EXPORT_DESTINATION_ARRAY <<< "$GITHUB_EXPORT_DESTINATION"

if [[ "${#GITHUB_EXPORT_SOURCE_ARRAY[@]}" != "${#GITHUB_EXPORT_DESTINATION_ARRAY[@]}" ]]; then
    echo "Source and destination arrays should have the same size"
    exit 1
fi

rm -rf "${GITHUB_EXPORT_TMPDIR}"
git clone "https://${GITHUB_EXPORT_USERNAME}:${GITHUB_EXPORT_PASSWORD}@github.com/epam/cloud-pipeline.git" -b "${GITHUB_EXPORT_BRANCH}" "${GITHUB_EXPORT_TMPDIR}"

if [[ ! -d "${GITHUB_EXPORT_TMPDIR}" ]]; then
    echo "Cloning has failed"
    exit 1
fi

cd "${GITHUB_EXPORT_TMPDIR}" || exit 1

for i in "${!GITHUB_EXPORT_SOURCE_ARRAY[@]}"; do
    CURRENT_SOURCE="${GITHUB_EXPORT_SOURCE_ARRAY[$i]}"
    CURRENT_DESTINATION="${GITHUB_EXPORT_DESTINATION_ARRAY[$i]}"
    rm -rf "${CURRENT_DESTINATION}"
    mkdir -p "$(dirname "${CURRENT_DESTINATION}")"
    cp -r "${CURRENT_SOURCE}" "${CURRENT_DESTINATION}"
    git add "${CURRENT_DESTINATION}"
done

git commit -a -m "${GITHUB_EXPORT_TAG} [$(date +%Y.%m.%d)]"
git push origin "${GITHUB_EXPORT_BRANCH}"

cd .. || exit 1
rm -rf "${GITHUB_EXPORT_TMPDIR}"
