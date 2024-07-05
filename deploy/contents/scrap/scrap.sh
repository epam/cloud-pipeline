#!/bin/bash

# Copyright 2024 EPAM Systems, Inc. (https://www.epam.com/)
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
# See the License for the specific language governing peKrmissions and
# limitations under the License.

#This script connects and gather Kubernetes ConfigMap, Cloud-Pipeline preferences, List of the installed cp services and installed pipectl version
# from provided server address and (optionally) IP of the Kubernetes Node.

#Example of usage ./scrap.sh  -k <ssh_key name>  -u <user who connect to server> -s <server adress name> -t <API_TOKEN> -o <output directory name> 

source get_configmap.sh
source get_pref.sh
source get_services.sh
source get_version.sh
source get_tools.sh


while [[ $# -gt 0 ]]
    do
    key="$1"

    case $key in
        -k)
        ssh_key="$2"
        shift # past argument
        shift # past value
        ;;
        -u)
        user="$2"
        shift # past argument
        shift # past value
        ;;
        -s)
        API_URL="$2"
        shift # past argument
        shift # past value
        ;;
        -i)
        node_ip="$2"
        shift # past argument
        shift # past value
        ;;
        -t)
        API_TOKEN="$2"
        shift # past argument
        shift # past value
        ;;
        -n)
        namespace="$2"
        shift # past argument
        shift # past value
        ;;
        -c)
        configmap_name="$2"
        shift # past argument
        shift # past value
        ;;
        -o)
        output_dir="$2"
        shift # past argument
        shift # past value
        ;;
        -f|--force)
        export force_write=1
        shift # past argument
        shift # past value
        ;;
esac        
done

if [ -z "$ssh_key" ]; then
    echo "Please provide the ssh key"
    exit 1
fi

if [ -z "$API_URL" ]; then
    echo "Please provide the API_URL"
    exit 1
fi

if [ -z "$API_TOKEN" ]; then
    echo "Please provide a API_TOKEN"
    exit 1
fi

if [ -z "$node_ip" ]; then
   node_ip=$(echo "$API_URL" | grep -oP "(?<=https://)([^/]+)")
fi 

if [ -z "$configmap_name" ]; then
   configmap_name="cp-config-global"
fi

if [ -z "$namespace" ]; then
   namespace="default"
fi

if [ ! -d "$output_dir" ]; then
    echo "Checking if output directory exist. Directory does not exist. Creating directory"
    mkdir -p "$output_dir"
    echo "Directory $output_dir created."
else
    echo "Checking if output directory exist. Directory exists"
fi

if [ "$(ls -A $output_dir)" ]; then
   if [ "$force_write" != 1 ]; then
      echo "Directory is not empty, if you still want to write there please use -f or --force flags." 1>&2 
      exit 1
   fi
fi 

echo "===Retrieving configmap from server==="
get_configmap $ssh_key $user $node_ip $namespace $configmap_name $output_dir


if [ $? -eq 0 ]; then
    echo "Configmap,0" > $output_dir/revision_metadata.csv
else
    echo "Configmap,1" > $output_dir/revision_metadata.csv     
fi

echo "===Retrieving preferences from server==="
get_pref $API_URL $API_TOKEN $output_dir

if [ $? -eq 0 ]; then
    echo "Application_preference,0" >> $output_dir/revision_metadata.csv
else
    echo "Application_preference,$?" >> $output_dir/revision_metadata.csv     
fi

echo "===Retrieving list on installed services from server==="
get_services $ssh_key $user $node_ip $output_dir

if [ $? -eq 0 ]; then
    echo "Application_services,0" >> $output_dir/revision_metadata.csv
else
    echo "Application_services,$?" >> $output_dir/revision_metadata.csv     
fi

echo "===Retrieving installed pipectl version from server==="
get_version $API_URL $API_TOKEN $output_dir

if [ $? -eq 0 ]; then
    echo "Application_version,0" >> $output_dir/revision_metadata.csv
else
    echo "Application_version,$?" >> $output_dir/revision_metadata.csv     
fi

echo "===Retrieving installed docker tools from server==="
get_tools $API_URL $API_TOKEN $output_dir

if [ $? -eq 0 ]; then
    echo "Docker_tools,0" >> $output_dir/../revision_metadata.csv
else
    echo "Docker_tools,$?" >> $output_dir/../revision_metadata.csv     
fi

echo "*****All files saved in directory $output_dir*****"
