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


# 1. Local build: bash build-pipectl.sh --base64 ../contents/
# 2. Docker build: docker run -i --rm -v $(dirname $(pwd)):/opt/build centos:7 /bin/bash -c "cd /opt/build/pipectl; bash build-pipectl.sh --base64 ../contents/"

BUILD_SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"

base64=1
if [[ "$1" == '--binary' ]]; then
	binary=1
	uuencode=0
	b64=0
	shift
fi
if [[ "$1" == '--uuencode' ]]; then
	binary=0
	uuencode=1
	b64=0
	shift
fi
if [[ "$1" == '--base64' ]]; then
	binary=0
	uuencode=0
	b64=1
	shift
fi

payload_location="$1"

if [[ ! "$payload_location" ]]; then
	echo "Usage: $0 [--binary | --uuencode | --base64] PAYLOAD_LOCATION"
	exit 1
fi

if [ ! -d "$payload_location" ]; then
    echo "$payload_location does not exist or not a directory"
    exit 1
fi

tarball=/tmp/cp-deploy.tgz
prev_location=$(pwd)
cd $payload_location
tar -zcf $tarball .
cd $prev_location

output_name=${DEPLOY_BUILD_OUTPUT_PATH:-pipectl}
mkdir -p $(dirname $output_name)
rm -f $output_name

if [[ $binary -ne 0 ]]; then
	sed \
		-e 's/uuencode=./uuencode=0/' \
		-e 's/binary=./binary=1/' \
		-e 's/b64=./b64=0/' \
			 $BUILD_SCRIPT_PATH/pipectl-template.sh >$output_name
	echo "PAYLOAD:" >> $output_name

	cat $tarball >>"$output_name"
fi
if [[ $uuencode -ne 0 ]]; then
	sed \
		-e 's/uuencode=./uuencode=1/' \
		-e 's/binary=./binary=0/' \
		-e 's/b64=./b64=0/' \
			 $BUILD_SCRIPT_PATH/pipectl-template.sh >$output_name
	echo "PAYLOAD:" >> $output_name

	cat $tarball | uuencode - >>"$output_name"
fi
if [[ $b64 -ne 0 ]]; then
	sed \
		-e 's/uuencode=./uuencode=0/' \
		-e 's/binary=./binary=0/' \
		-e 's/b64=./b64=1/' \
			 $BUILD_SCRIPT_PATH/pipectl-template.sh >$output_name
	echo "PAYLOAD:" >> "$output_name"

	cat $tarball | base64 >>$output_name
fi

chmod +x $output_name
rm -f $tarball