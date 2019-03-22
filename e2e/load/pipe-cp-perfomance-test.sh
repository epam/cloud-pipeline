#!/usr/bin/env bash

##############################
# Usage:
# Test script expects that pipe CLI is already installed in the system and is configured to work with Cloud Pipeline
# Test script output time for "pipe storage cp" command for the following operations:
#   - download one large file from cloud storage to local filesystem
#   - upload one large file from local filesystem to cloud storage
#   - upload a large number of small files from local filesystem to cloud storage
#   - download a large number of small files from cloud storage to local filesystem
# Script takes three arguments:
# test folder (required) - path to folder that will be created for test and deleted after test
# path to large file (required) - path in S3 (Cloud Pipeline) to existing relatively large file
# number of small files (optional, default = 1000) - specifies number of small test files used for test
# Example command:
#   ./pipe-cp-perfomance-test.sh ~/Work/test-pipe-cp s3://pipeline-test/BK0001_S2.bam 500
##############################

function execute_command {
    local _COMMAND=$1
    eval "${_COMMAND}"
    if [ $? -ne 0 ];
    then
        echo "Execution of command: '${_COMMAND}' failed"
        exit 1
    fi
}


TEST_FOLDER=$1
PATH_TO_LARGE_FILE=$2
NUMBER_OF_SMALL_FILES=$3

###########################
# Check input arguments
###########################

if [ -z ${TEST_FOLDER} ];
then
    echo "Test folder shall be specified"
    exit 1
fi

if [ -z ${PATH_TO_LARGE_FILE} ];
then
    echo "Path to large file for tests shall be specified"
    exit 1
fi

if [ -z ${NUMBER_OF_SMALL_FILES} ];
then
    echo "Number of small files is not set. Using default value: 1000."
    NUMBER_OF_SMALL_FILES=1000
fi

###########################
# Check pipe cp installation
###########################
execute_command "pipe storage ls >/dev/null 2>&1"


###########################
# Set up prerequisites
###########################
execute_command "mkdir -p ${TEST_FOLDER}"

LARGE_FILE_NAME=$(basename "${PATH_TO_LARGE_FILE}")
LOCAL_LARGE_FILE="${TEST_FOLDER}/${LARGE_FILE_NAME}"

REMOTE_FOLDER=$(dirname "${PATH_TO_LARGE_FILE}")
SMALL_FILE_DIRECTORY="${TEST_FOLDER}/small"
execute_command "mkdir -p ${SMALL_FILE_DIRECTORY}"

for i in $(seq 1 ${NUMBER_OF_SMALL_FILES})
do
   echo "test" > "${SMALL_FILE_DIRECTORY}/file${i}"
done

###########################
# Download large file
###########################
echo "Time to download large file:"
time execute_command "pipe storage cp ${PATH_TO_LARGE_FILE} ${LOCAL_LARGE_FILE} -q"

###########################
# Upload large file
###########################
echo "Time to upload large file:"
time execute_command "pipe storage cp ${LOCAL_LARGE_FILE} ${PATH_TO_LARGE_FILE}-copy -q"

###########################
# Upload 1000 small files
###########################
echo "Time to upload ${NUMBER_OF_SMALL_FILES} small files:"
time execute_command "pipe storage cp ${SMALL_FILE_DIRECTORY} ${REMOTE_FOLDER}/small -q -r -f"

###########################
# Download 1000 small files
###########################
echo "Time to download ${NUMBER_OF_SMALL_FILES} small files:"
time execute_command "pipe storage cp ${REMOTE_FOLDER}/small ${SMALL_FILE_DIRECTORY}-copy -q -r -f"

###########################
# Clean up
###########################
execute_command "rm -rf ${TEST_FOLDER}"
execute_command "pipe storage rm ${PATH_TO_LARGE_FILE}-copy -y -d"
execute_command "pipe storage rm ${REMOTE_FOLDER}/small -y -r -d"

echo "Pipe CP test finished successfully"