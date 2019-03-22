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


#############
# Common functions/constants
#############

uuencode=0
binary=0
b64=1

tmp_location=~/.pipe/tmp/deploy

function extract_payload()
{
	payload_location=$1
	mkdir -p $payload_location
	match=$(grep --text --line-number '^PAYLOAD:$' $0 | cut -d ':' -f 1)
	payload_start=$((match + 1))
	if [[ $binary -ne 0 ]]; then
		tail -n +$payload_start $0 | tar -zxf - -C $payload_location
	fi
	if [[ $uuencode -ne 0 ]]; then
		tail -n +$payload_start $0 | uudecode | tar -zxf - -C $payload_location
	fi
	if [[ $b64 -ne 0 ]]; then
		tail -n +$payload_start $0 | base64 --decode | tar -zxf - -C $payload_location
	fi
	chmod +x $payload_location/*
}

extract_payload "$tmp_location"
install_home=$tmp_location/install/app
if [ ! -d $install_home ]; then
    echo "ERROR: $install_home not found, corrupted installer"
fi
prev_location=$(pwd)
cd $install_home
bash install.sh "$@"
rm -rf $tmp_location
cd $prev_location

exit 0
