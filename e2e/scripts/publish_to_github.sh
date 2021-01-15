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
# Example:
#
# GITHUB_EXPORT_USERNAME="USERNAME"
# GITHUB_EXPORT_PASSWORD="PASSWORD"
# GITHUB_EXPORT_TAG="AWS GUI test results"
# GITHUB_EXPORT_PATH="e2e/reports/AWS/test"
# GITHUB_EXPORT_SOURCE="e2e/gui/build/reports/tests/test
# GITHUB_EXPORT_DESTINATION="${GITHUB_EXPORT_TMPDIR}/${GITHUB_EXPORT_PATH}"
# 
# ./e2e/scripts/publish_to_github.sh --username "USERNAME" \
#                                    --password "PASSWORD" \
#                                    --source "e2e/gui/build/reports/tests/test" \
#                                    --destination "e2e/reports/AWS/test" \
#                                    --tag "AWS GUI test results"
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
        GITHUB_EXPORT_PATH="$2"
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
GITHUB_EXPORT_PATH="${GITHUB_EXPORT_PATH}"
GITHUB_EXPORT_SOURCE="${GITHUB_EXPORT_SOURCE}"

if [[ -z "$GITHUB_EXPORT_USERNAME" ]]; then echo "Option --username is missing"; exit 1; fi
if [[ -z "$GITHUB_EXPORT_PASSWORD" ]]; then echo "Option --password is missing"; exit 1; fi
if [[ -z "$GITHUB_EXPORT_SOURCE" ]]; then echo "Option --source is missing"; exit 1; fi
if [[ -z "$GITHUB_EXPORT_PATH" ]]; then echo "Option --destination is missing"; exit 1; fi

GITHUB_EXPORT_DESTINATION="${GITHUB_EXPORT_TMPDIR}/${GITHUB_EXPORT_PATH}"

rm -rf "${GITHUB_EXPORT_TMPDIR}"
git clone "https://$GITHUB_EXPORT_USERNAME:$GITHUB_EXPORT_PASSWORD@github.com/epam/cloud-pipeline.git" -b "${GITHUB_EXPORT_BRANCH}" "${GITHUB_EXPORT_TMPDIR}"
if [[ ! -d "${GITHUB_EXPORT_TMPDIR}" ]]; then echo "Cloning has failed"; exit 1; fi
rm -rf "${GITHUB_EXPORT_DESTINATION}"
mkdir -p "$(dirname "${GITHUB_EXPORT_DESTINATION}")"
cp -r "${GITHUB_EXPORT_SOURCE}" "${GITHUB_EXPORT_DESTINATION}"
git add "${GITHUB_EXPORT_DESTINATION}"
git commit -a -m "${GITHUB_EXPORT_TAG} [$(date +%Y.%m.%d)]"
git push origin "${GITHUB_EXPORT_BRANCH}"
cd ..
rm -rf "${GITHUB_EXPORT_TMPDIR}"
