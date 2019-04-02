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

pwd=$(pwd)
if [ "$pwd" != "/" ]; then
    echo "Cleaning current dir $pwd"
    rm -rf $pwd/*
fi

# Get version info from the s3 distribution URL
# Format: cloud-pipeline.{major}.{minor}.{patch}.{build}.{commit}.tgz
IFS="." read -ra version <<< "$URL"
commit="${version[-2]}"
build="${version[-3]}"
patch="${version[-4]}"
minor="${version[-5]}"
major="${version[-6]}"

if [[ -z $major || -z $minor || -z $patch || -z $build || -z $commit ]]; then
    echo 'One of the distribution version parts is not defined'
    exit 1
fi

git clone https://github.com/epam/cloud-pipeline.git
cd cloud-pipeline
git checkout $commit