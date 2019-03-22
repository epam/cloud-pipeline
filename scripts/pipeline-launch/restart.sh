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

echo "------"
echo "Restarting run"
echo "------"

SCRIPT=$1

SSH_SERVER_EXEC_PATH='/usr/sbin/sshd'

if [ -f $SSH_SERVER_EXEC_PATH ] ;
then
    eval "$SSH_SERVER_EXEC_PATH"
    echo "SSH server is started"
else
    echo "$SSH_SERVER_EXEC_PATH not found, ssh server will not be started"
fi

rpcbind && rpc.statd

######################################################
echo Executing task
echo "-"
######################################################

# As some environments do not support "sleep infinity" command - it is substituted with "sleep 10000d"
SCRIPT="${SCRIPT/sleep infinity/sleep 10000d}"

# Execute task and get result exit code
cd $ANALYSIS_DIR
echo "CWD is now at $ANALYSIS_DIR"


# Tell the environment that initilization phase is finished and a source script is going to be executed
pipe_log SUCCESS "Environment initialization finished" "InitializeEnvironment"

echo "Command text:"
echo "${SCRIPT}"
bash -c "${SCRIPT}"
result=$?

echo "------"
echo
######################################################

exit "$result"