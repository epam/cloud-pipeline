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
 
#############################################################
# Setup the parameters
#############################################################
# Cloud Pipeline API entrypoint, e.g. https://<host>/pipeline
API_URL=""
#   Shall be generated in the Cloud Pipeline GUI -> Settings -> CLI -> Generate access key
API_TOKEN=""
 
# Data location:
#   S3 bucket, that is going to be used as a "working directory"
#   Local fastq files and processing results will uploaded there
#   Example value: s3://my_bucket/workdir
WORKDIR=""
# Path to a local directory, that holds the FASTQ files for processing
# E.g. ~/tiny-fastq
FASTQS=""
# Path to the cellranger transcriptome
# It shall be located in the S3 bucket already
# In this example we use the "tiny" reference, which is only 300Mb and allows to debug jobs quickly
TRANSCRIPTOME_S3="s3://genome-bucket/human/transcriptome/tiny"
# Path to the S3 location, that will hold the data processing results
RESULTS_S3="$WORKDIR/results"
 
# Job parameters:
#   The docker image, which holds a cellranger binary and environment
DOCKER_IMAGE="ngs/cellranger:latest"
#   The size of the machine, that is going to process the data
#   If it's not set - the default hardware for the $DOCKER_IMAGE is going to used
INSTANCE_TYPE="r5.xlarge"
#   The size of disk volume in Gb, that will be attached to the machine defined by $INSTANCE_TYPE
#   If it's not set - the default hardware for the $DOCKER_IMAGE is going to used
INSTANCE_DISK="100"
 
 
#############################################################
# Get the S3 working directory bucket ID
#############################################################
BUCKET_NAME=$(cut -d/ -f 3 <<< $WORKDIR)
RESPONSE=$(curl -ks -X GET -H "Authorization: Bearer $API_TOKEN" -H "Accept: application/json" "$API_URL/restapi/datastorage/find?id=$BUCKET_NAME")
BUCKET_ID=$(jq -r ".payload.id" <<< $RESPONSE)
 
#############################################################
# Get the S3 working directory bucket access token by it's ID
#############################################################
RESPONSE=$(curl -ks -X POST -H "Authorization: Bearer $API_TOKEN" -H "Content-Type: application/json" -H "Accept: application/json" -d "[
            {
                \"id\": $BUCKET_ID,
                \"read\": true,
                \"write\": true
            }
            ]" "$API_URL/restapi/datastorage/tempCredentials/")
 
# The resulting keys, shall be used to configure the AWS SDK for the data transfer
# Here we use AWS CLI SDK, but this will work for any other (e.g. Java/JS/Go/Python/...)
export AWS_ACCESS_KEY_ID=$(jq -r '.payload.keyID' <<< $RESPONSE)
export AWS_SECRET_ACCESS_KEY=$(jq -r '.payload.accessKey' <<< $RESPONSE)
export AWS_SESSION_TOKEN=$(jq -r '.payload.token' <<< $RESPONSE)
 
 
#############################################################
# Transfer the local fastq files to the S3 working directory using the direct call to AWS SDK
#############################################################
# Files will be uploaded to e.g. "s3://my_bucket/workdir/fastq/tiny-fastq/"
FASTQS_S3="$WORKDIR/fastq/$(basename $FASTQS)"
aws s3 cp "$FASTQS" "$FASTQS_S3/" --recursive
 
#############################################################
# Run processing
#############################################################
RESPONSE=$(curl -ks -X POST -H "Authorization: Bearer $API_TOKEN" -H "Content-Type: application/json" -H "Accept: application/json" -d "{
            \"cmdTemplate\": \"cellranger count --id cloud-cellranger --fastqs \$fastqs --transcriptome \$transcriptome\",
            \"dockerImage\": "single-cell/cellranger:latest",
            \"hddSize\": $INSTANCE_DISK,
            \"instanceType\": \"$INSTANCE_TYPE\",
            \"params\": {
                    \"fastqs\": {
                        \"type\": \"input\",
                        \"value\": \"$FASTQS_S3\"
                    },
                    \"results\": {
                        \"type\":\"output\",
                        \"value\":\"$RESULTS_S3\"
                    },
                    \"transcriptome\": {
                        \"type\": \"input\",
                        \"value\": \"$TRANSCRIPTOME_S3\"
                    }
                }
            }" "$API_URL/restapi/run")
RUN_ID=$(jq -r ".payload.id" <<< $RESPONSE)
 
#############################################################
# Poll the run status each 30s, until it's finished
#############################################################
RUN_STATUS="NA"
while [ "$RUN_STATUS" != "SUCCESS" ]; do
    sleep 30
    RESPONSE=$(curl -ks -X GET -H "Authorization: Bearer $API_TOKEN" -H "Accept: application/json" "$API_URL/restapi/run/$RUN_ID")
    RUN_STATUS=$(jq -r ".payload.status" <<< $RESPONSE)
done
