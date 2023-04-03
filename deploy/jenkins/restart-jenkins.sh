#!/bin/bash
# Copyright 2017-2022 EPAM Systems, Inc. (https://www.epam.com/)
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

ENV_PATH=/root/.cp-jenkins/run-jenkins.env
set -o allexport
source $ENV_PATH
set +o allexport

docker stop cp-sqs-trigger
docker stop cp-jenkins-docker

docker rm cp-sqs-trigger
docker rm cp-jenkins-docker

docker run  -d \
            -v $JENKINS_HOME:/var/jenkins_home \
            -d \
            -v /var/run/docker.sock:/var/run/docker.sock \
            -v $(which docker):/usr/bin/docker \
            -v "$ENV_PATH":"$JENKINS_ENV" \
            -v "$AZURE_AUTH_LOCATION":"$AZURE_AUTH_LOCATION" \
            -p 8080:8080 \
            -p 50000:50000 \
            -u root \
            -e JENKINS_USER=$JENKINS_USER \
            -e JENKINS_PASS=$JENKINS_PASS \
            -e JENKINS_ENV=$JENKINS_ENV \
            --name cp-jenkins-docker \
            cp-jenkins-docker

docker run  -d \
            -e SQS_QUEUE=$SQS_QUEUE \
            -e SQS_POLL_SEC=$SQS_POLL_SEC \
            -e JENKINS_USER=$JENKINS_USER \
            -e JENKINS_PASS=$JENKINS_PASS \
            -e JENKINS_API_TOKEN= \
            -e JENKINS_HOST=$JENKINS_HOST \
            -e JENKINS_PORT=$JENKINS_PORT \
            -e JENKINS_JOB_NAME=cloud-pipeline-build-pipectl \
            -e JENKINS_JOB_TOKEN=$JENKINS_JOB_TOKEN \
            -e AWS_DEFAULT_REGION=$AWS_DEFAULT_REGION \
            --name cp-sqs-trigger \
            cp-sqs-trigger
