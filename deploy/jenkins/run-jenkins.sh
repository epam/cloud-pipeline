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

function create_jenkins_job {
    local job_dir="$1"

    echo "Creating job $job_dir"

    cd $SELF_PATH/jenkins-jobs/$job_dir

    # sed is used to "html-style" escape script, e.g. &quot; / &lt; / etc..
    export BUILD_COMMAND="$(cat script.sh | sed 's/&/\&amp;/g; s/</\&lt;/g; s/>/\&gt;/g; s/"/\&quot;/g; s/'"'"'/\&#39;/g')"

    JOB_CONFIG_XML="$(envsubst < config.xml)"
    cat <<< "$JOB_CONFIG_XML" > config.xml

    curl -s -XPOST "http://${JENKINS_HOST}:${JENKINS_PORT}/createItem?name=cloud-pipeline-${job_dir}" \
        -u ${JENKINS_USER}:${JENKINS_PASS} \
        --data-binary @config.xml \
        -H "Content-Type:text/xml"

    echo "Job created with exit code $?"

    cd -
}

echo "Starting jenkins setup"

SELF_PATH="$( cd "$(dirname "$0")" ; pwd -P )"
ENV_PATH="${1:-"$SELF_PATH/run-jenkins.env"}"

echo "Reading config from $ENV_PATH"

set -o allexport
source $ENV_PATH
set +o allexport

echo "Building jenkins docker image from $SELF_PATH/jenkins-docker"

docker rm -f cp-jenkins-docker
docker build $SELF_PATH/jenkins-docker -t cp-jenkins-docker
rm -rf $JENKINS_HOME

echo "Starting jenkins docker image cp-jenkins-docker"

docker run  -d \
            -v $JENKINS_HOME:/var/jenkins_home \
            -d -v /var/run/docker.sock:/var/run/docker.sock \
            -v $(which docker):/usr/bin/docker \
            -p 8080:8080 \
            -p 50000:50000 \
            -u root \
            -e JENKINS_USER=$JENKINS_USER \
            -e JENKINS_PASS=$JENKINS_PASS \
            -e DOCKER_USER=$DOCKER_USER \
            -e DOCKER_PASS=$DOCKER_PASS \
            --name cp-jenkins-docker \
            cp-jenkins-docker

echo "Waiting for jenkins to init"

sleep 30

echo "Creating jenkins jobs"

create_jenkins_job build-pipectl
create_jenkins_job deploy-dev

echo "Building sqs trigger docker image from $SELF_PATH/jenkins-sqs-trigger"

docker rm -f cp-sqs-trigger
docker build $SELF_PATH/jenkins-sqs-trigger -t cp-sqs-trigger

echo "Starting jenkins docker image cp-sqs-trigger"

docker run  -d \
            -e SQS_QUEUE=$SQS_QUEUE \
            -e SQS_POLL_SEC=$SQS_POLL_SEC \
            -e JENKINS_USER=$JENKINS_USER \
            -e JENKINS_PASS=$JENKINS_PASS \
            -e JENKINS_API_TOKEN=$JENKINS_API_TOKEN \
            -e JENKINS_HOST=$JENKINS_HOST \
            -e JENKINS_PORT=$JENKINS_PORT \
            -e JENKINS_JOB_NAME=$JENKINS_JOB_NAME \
            -e JENKINS_JOB_TOKEN=$JENKINS_JOB_TOKEN \
            -e AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID \
            -e AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY \
            -e AWS_DEFAULT_REGION=$AWS_DEFAULT_REGION \
            --name cp-sqs-trigger \
            cp-sqs-trigger

echo "Jenkins setup done"