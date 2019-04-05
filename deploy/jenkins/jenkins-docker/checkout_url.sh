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

URL="$1"

if [ -z "$URL" ]; then
    echo "Distribution URL is not defined"
    exit 1
fi

if [ -z "$JENKINS_ENV" ]; then
    echo "Environment variables are not set"
    exit 1
else
    set -o allexport
    source "$JENKINS_ENV"
    set +o allexport
fi

pwd=$(pwd)
if [ "$pwd" != "/" ]; then
    echo "Cleaning current dir $pwd"
    rm -rf $pwd/*
fi

# Get version info from the s3 distribution URL
# Format: cloud-pipeline.{major}.{minor}.{patch}.{build}.{commit}.tgz
path_index="-1"
IFS="." read -ra version <<< "$URL"
export dist_commit="${version[$path_index]}"
if [ "$dist_commit" == "tgz" ] || [ "$dist_commit" == "tar" ]; then
    path_index=$((path_index-1))
elif [ "$dist_commit" == "gz" ]; then
    path_index=$((path_index-2))
fi
export dist_commit="${version[$path_index]}"
export dist_build="${version[$((path_index-1))]}"
export dist_patch="${version[$((path_index-2))]}"
export dist_minor="${version[$((path_index-3))]}"
export dist_major="${version[$((path_index-4))]}"

IFS="/" read -ra url_parts <<< "$URL"
export dist_filename="${url_parts[-1]}"
export dist_branch="${url_parts[-2]}"

if [[ -z $dist_major || -z $dist_minor || -z $dist_patch || -z $dist_build || -z $dist_commit || -z $dist_filename || -z $dist_branch ]]; then
    echo 'One of the distribution version parts is not defined'
    exit 1
fi

git clone https://github.com/epam/cloud-pipeline.git
cd cloud-pipeline
git checkout $dist_commit
