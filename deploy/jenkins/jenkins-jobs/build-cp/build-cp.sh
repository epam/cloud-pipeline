#!/bin/bash

CP_BUILD_SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
CP_BUILDER_IMAGE_NAME="lifescience/cloud-pipeline:cp-builder"
CP_BUILD_DIR=/builds/cloud-pipeline-$CP_BUILD_NUMBER

function run_cmd {
    local _cmd="set -o allexport
                source $JENKINS_ENV
                set +o allexport
                git clone https://github.com/epam/cloud-pipeline $CP_BUILD_DIR && \
                cd $CP_BUILD_DIR && \
                git checkout $CP_COMMIT && \
                $1"

    docker run  -i --rm \
                -e CP_BUILD_NUMBER=$CP_BUILD_NUMBER \
                -e CP_COMMIT=$CP_COMMIT \
                -e CP_GIT_REF=$CP_GIT_REF \
                -v $JENKINS_ENV:$JENKINS_ENV:ro \
                -v $CP_BUILD_DIR:$CP_BUILD_DIR \
                -v /tmp:/tmp \
                -v /var/run/docker.sock:/var/run/docker.sock \
                -v $(which docker):/usr/bin/docker:ro \
                $CP_BUILDER_IMAGE_NAME /bin/bash -s <<<"$_cmd"
    
    local _exec_result=$?

    # Cleanup the current build directory
    docker run  -i --rm \
                -v $(dirname $CP_BUILD_DIR):$(dirname $CP_BUILD_DIR) \
                $CP_BUILDER_IMAGE_NAME /bin/bash -s <<<"rm -rf $CP_BUILD_DIR"

    return $_exec_result
}

######################
# Check docker image
######################
docker pull "$CP_BUILDER_IMAGE_NAME"
if [ $? -ne 0 ]; then
    docker build ${CP_BUILD_SCRIPT_DIR}/docker -t "$CP_BUILDER_IMAGE_NAME"
fi

###########
# Build GUI
###########
run_cmd "./gradlew -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} -Pprofile=release client:buildUI --no-daemon && \
         tar -zcf client.tgz client/build && \
         aws s3 mv ${CP_BUILD_DIR}/client.tgz s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/"
if [ $? -ne 0 ]; then
    echo "[ERROR] Can't build GUI"
    exit 1
fi

########################
# Build CLI Common/Linux
########################
# FIXME: ignore build errors, as gradle may occasionally fail. "cleanCLIVersion" - shall be reviewed as it can be a source of such behavior
run_cmd "./gradlew -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} -Pprofile=release pipe-cli:build --no-daemon ; \
         ./gradlew -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} -Pprofile=release pipe-cli:buildLinux --no-daemon ; \
         tar -zcf cli-linux.tgz pipe-cli/dist ; \
         aws s3 mv cli-linux.tgz s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/"
if [ $? -ne 0 ]; then
    echo "[ERROR] Can't build CLI Common/Linux"
    exit 1
fi

######################
# Build CLI Linux (el6)
######################
run_cmd "_BUILD_DOCKER_IMAGE=lifescience/cloud-pipeline:python2.7-centos6 \
    ./gradlew   -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} \
                -Pprofile=release pipe-cli:buildLinux \
                --no-daemon ; \
    tar -zcf cli-linux-el6.tgz pipe-cli/dist ; \
    aws s3 mv cli-linux-el6.tgz s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/"
if [ $? -ne 0 ]; then
    rm -rf $CP_BUILD_DIR
    echo "[ERROR] Can't build CLI Linux (el6)"
    exit 1
fi

##############
# Build CLI Win
##############
run_cmd "./gradlew -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} -Pprofile=release pipe-cli:buildWin --no-daemon ; \
         tar -zcf cli-win.tgz pipe-cli/dist ; \
         aws s3 mv cli-win.tgz s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/"

if [ $? -ne 0 ]; then
    echo "[ERROR] Can't build CLI Win"
    exit 1
fi

################
# Build FS Browser
################
run_cmd "./gradlew -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} -Pprofile=release fs-browser:build --no-daemon && \
         aws s3 mv fs-browser/dist/fsbrowser-*.tar.gz s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/"
if [ $? -ne 0 ]; then
    echo "[ERROR] Can't build FS Browser"
    exit 1
fi

############################
# Build Cloud Data for Linux
############################
run_cmd "./gradlew -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} -Pprofile=release cloud-pipeline-webdav-client:buildLinux --no-daemon && \
         aws s3 mv cloud-pipeline-webdav-client/out/cloud-data-linux.tar.gz s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/"
if [ $? -ne 0 ]; then
    echo "[ERROR] Can't build Cloud Data for Linux"
    exit 1
fi

##############################
# Build Cloud Data for Windows
##############################
run_cmd "./gradlew -PbuildNumber=${CP_BUILD_NUMBER}.${CP_COMMIT} -Pprofile=release cloud-pipeline-webdav-client:buildWin --no-daemon && \
         aws s3 mv cloud-pipeline-webdav-client/out/cloud-data-win64.zip s3://cloud-pipeline-oss-builds/temp/${CP_BUILD_NUMBER}/"
if [ $? -ne 0 ]; then
    echo "[ERROR] Can't build Cloud Data for Windows"
    exit 1
fi

####################
# Pack jars and deploy
####################
run_cmd "bash deploy/travis/travis_pack_dist.sh"
if [ $? -ne 0 ]; then
    echo "[ERROR] Can't repack the JAR"
    exit 1
fi
