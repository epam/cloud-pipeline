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

container_id="$1"
limit="$2"
upload_rate="$3"
download_rate="$4"
command="/bin/wondershaper"

set -x

interfaces=$(sudo docker exec -it $container_id ls /sys/class/net | tr -d '\r')
for i in $interfaces
  do
    if [ "$i" != "lo" ]; then
      link_num=$(sudo docker exec -it $container_id cat /sys/class/net/$i/iflink | tr -d '\r')
      interface_id=$(sudo ip ad | grep "^$link_num:" | awk -F ': ' '{print $2}' | awk -F '@' '{print $1}')
      if [ ! -z $interface_id ]; then
        if [ $limit == "true" ]; then
#          download_rate and upload_rate in kilobits per second
          result=$($command -a $interface_id -d $download_rate -u $upload_rate 2>&1)
          if [ ! -z "$result" ]; then
            pipe_log_warn "[WARN] Failed to apply bandwidth limiting"
          else
            pipe_log_info "[INFO] Bandwidth limiting applied successfully"
          fi
        else
            result=$($command -c -a $interface_id 2>&1)
          if [ ! -z "$result" ]; then
            pipe_log_warn "[WARN] Failed to clear bandwidth limitation"
          else
            pipe_log_info "[INFO] Bandwidth limitation cleared"
          fi
        fi
      fi
    fi
done
