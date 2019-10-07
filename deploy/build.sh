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


function check_installed {
    command -v "$1" >/dev/null 2>&1
    return $?
}

function cleanup_build {
    rm -rf $BUILD_DIR
}

BUILD_OUTPUT_FILE=~/.pipe/pipectl

###############
# Parse options
###############
POSITIONAL=()
OTHER_PACKAGES=()
BUILD_INCLUDE_TESTS=""
while [[ $# -gt 0 ]]
do
key="$1"

case $key in
    -v|--version)
    BUILD_VERSION="$2"
    shift # past argument
    shift # past value
    ;;
    -t|--include-tests)
    BUILD_INCLUDE_TESTS="yes"
    shift # past argument
    ;;
    -o|--output-path)
    BUILD_OUTPUT_FILE="$2"
    shift # past argument
    shift # past value
    ;;
    -aws|--aws-images-regions)
    BUILD_AMI_REGIONS="$2"
    shift # past argument
    shift # past value
    ;;
    -az|--azure-images-regions)
    BUILD_AZ_REGIONS="$2"
    shift # past argument
    shift # past value
    ;;
    -im|--images-manifest)
    CLOUD_IMAGES_MANIFEST_FILE="$2"
    shift # past argument
    shift # past value
    ;;
    -dm|--docker-manifest)
    DOCKER_IMAGES_MANIFEST_FILE="$2"
    shift # past argument
    shift # past value
    ;;
    -k|--keep-manifests)
    KEEP_MANIFEST_FILES="1"
    shift # past argument
    ;;
    -p|--package)
    OTHER_PACKAGES+=("$2")
    shift # past argument
    shift # past value
    ;;
    *)    # unknown option
    POSITIONAL+=("$1") # save it in an array for later
    shift # past argument
    ;;
esac
done
set -- "${POSITIONAL[@]}" # restore positional parameters

if [ -z "$BUILD_VERSION" ]; then
    echo "Build version is not set, specify it using -v|--version option"
    exit 1
fi

#######################
# Setup build directory
#######################
BUILD_SCRIPT_PATH="$( cd "$(dirname "$0")" ; pwd -P )"
BUILD_DIR=${BUILD_DIR:-"/tmp/cp-deploy-build"}
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

##########################
# Build AMIs, if requested
##########################
echo
echo "[BUILDING CLOUD IMAGES]"
if [ "$CLOUD_IMAGES_MANIFEST_FILE" == "rebuild" ]; then   
    CLOUD_IMAGES_MANIFEST_FILE="$BUILD_DIR/cloud-images-manifest.txt"
    if [ "$BUILD_AMI_REGIONS" ]; then
        echo "[BUILDING AWS AMIs in ${BUILD_AMI_REGIONS}]"
        bash $BUILD_SCRIPT_PATH/infra/build-infra.sh    -p aws \
                                                        -r "${BUILD_AMI_REGIONS}" \
                                                        -o "${CLOUD_IMAGES_MANIFEST_FILE}.aws"
        cat ${CLOUD_IMAGES_MANIFEST_FILE}.aws >> $CLOUD_IMAGES_MANIFEST_FILE
        rm -f ${CLOUD_IMAGES_MANIFEST_FILE}.aws
    fi
    if [ "$BUILD_AZ_REGIONS" ]; then
        echo "[BUILDING AZURE images in ${BUILD_AZ_REGIONS}]"

        if [  -z "$CP_AZURE_RESOURCE_GROUP" ]; then
            echo "AZURE_RESOURCE_GROUP is not defined for the Azure images build"
            cleanup_build
            exit 1
        fi
        if [  -z "$CP_AZURE_AUTH_LOCATION" ]; then
            echo "CP_AZURE_RESOURCE_GROUP is not defined for the Azure images build"
            cleanup_build
            exit 1
        fi

        bash $BUILD_SCRIPT_PATH/infra/build-infra.sh    -p az \
                                                        -r "${BUILD_AZ_REGIONS}" \
                                                        -o "${CLOUD_IMAGES_MANIFEST_FILE}.az"
        cat ${CLOUD_IMAGES_MANIFEST_FILE}.az >> $CLOUD_IMAGES_MANIFEST_FILE
        rm -f ${CLOUD_IMAGES_MANIFEST_FILE}.az
    fi
elif [ -z "$CLOUD_IMAGES_MANIFEST_FILE" ] || [[ "$CLOUD_IMAGES_MANIFEST_FILE" == "http"*"://"* ]]; then
    CLOUD_IMAGES_MANIFEST_URI=${CLOUD_IMAGES_MANIFEST_FILE:-"https://s3.amazonaws.com/cloud-pipeline-oss-builds/manifests/cloud-images-manifest.txt"}
    CLOUD_IMAGES_MANIFEST_FILE="$BUILD_DIR/cloud-images-manifest.txt"
    echo "Cloud images manifest is specified explicitely ($CLOUD_IMAGES_MANIFEST_URI) via the remote URI, downloading to $CLOUD_IMAGES_MANIFEST_FILE. Cloud images WILL NOT be rebuilt"
    if check_installed "wget"; then
        wget "$CLOUD_IMAGES_MANIFEST_URI" -O $CLOUD_IMAGES_MANIFEST_FILE
    elif check_installed "curl"; then
        curl "$CLOUD_IMAGES_MANIFEST_URI" -o $CLOUD_IMAGES_MANIFEST_FILE
    else
        echo "ERROR: wget and curl are not installed, please install one of them to use the remote images manifest"
        cleanup_build
        exit 1
    fi
else
    echo "Cloud images manifest is specified explicitely ($CLOUD_IMAGES_MANIFEST_FILE). Cloud image WILL NOT be rebuilt"
fi

###############
# Build dockers
###############

DOCKERS_MANIFEST_DIR="$BUILD_DIR/dockers-manifest"

if [[ "$DOCKER_IMAGES_MANIFEST_FILE" == "http"*"://"* ]]; then
    DOCKER_IMAGES_MANIFEST_URI="$DOCKER_IMAGES_MANIFEST_FILE"
    DOCKER_IMAGES_MANIFEST_DIR_TMP="$(mktemp -d)"
    DOCKER_IMAGES_MANIFEST_FILE="${DOCKER_IMAGES_MANIFEST_DIR_TMP}/dockers-manifest.tgz"
    echo "Docker images manifest is specified explicitely ($DOCKER_IMAGES_MANIFEST_URI) via the remote URI, downloading to $DOCKER_IMAGES_MANIFEST_FILE. Docker images WILL NOT be rebuilt"
    if check_installed "wget"; then
        wget "$DOCKER_IMAGES_MANIFEST_URI" -O $DOCKER_IMAGES_MANIFEST_FILE
    elif check_installed "curl"; then
        curl "$DOCKER_IMAGES_MANIFEST_URI" -o $DOCKER_IMAGES_MANIFEST_FILE
    else
        echo "ERROR: wget and curl are not installed, please install one of them to use the remote images manifest"
        cleanup_build
        exit 1
    fi

    cd "$DOCKER_IMAGES_MANIFEST_DIR_TMP"
    tar -zxf $DOCKER_IMAGES_MANIFEST_FILE
    if [ $? -ne 0 ]; then
        echo "ERROR: unable to unpack the dockers manifest tarball, exiting"
        cleanup_build
        exit 1
    fi
    cd -
    if [ ! -d "$DOCKER_IMAGES_MANIFEST_DIR_TMP/dockers-manifest" ]; then
        echo "ERROR: unable to find the unpacked dockers-manifest directory, exiting"
        cleanup_build
        exit 1
    fi
    rm -rf "$DOCKERS_MANIFEST_DIR"
    \cp -r "$DOCKER_IMAGES_MANIFEST_DIR_TMP/dockers-manifest" "$DOCKERS_MANIFEST_DIR"
    rm -rf "$DOCKER_IMAGES_MANIFEST_DIR_TMP"
else
    if ! check_installed "docker"; then
        echo "Docker is not installed. Please install it and rerun the script"
        cleanup_build
        exit 1
    fi

    echo
    echo "[BUILDING/PUSHING DOCKER IMAGES]"
    DOCKERS_BUILD_OTHER_OPTIONS=""
    if [ "$BUILD_INCLUDE_TESTS" ]; then
        echo "Test docker images will be included"
        DOCKERS_BUILD_OTHER_OPTIONS="$DOCKERS_BUILD_OTHER_OPTIONS -t"
    fi
    bash $BUILD_SCRIPT_PATH/docker/build-dockers.sh -c "$DOCKERS_MANIFEST_DIR" -s "$BUILD_SCRIPT_PATH/docker" -v "$BUILD_VERSION" $DOCKERS_BUILD_OTHER_OPTIONS

    if [ $? -ne 0 ]; then
        echo "ERROR occured while building/pushing docker images, FAILING build"
        cleanup_build
        exit 1
    fi
fi

##########################################################################
# Prepare pipectl package directory structure and build pipectl executable
##########################################################################
PIPECTL_SOURCES_DIR="$BUILD_SCRIPT_PATH/contents"
PIPECTL_CONTENTS_DIR="$BUILD_DIR/contents"
\cp -r "$PIPECTL_SOURCES_DIR" "$BUILD_DIR/"
\cp -r "$DOCKERS_MANIFEST_DIR" "$PIPECTL_CONTENTS_DIR/"
\cp "$CLOUD_IMAGES_MANIFEST_FILE" "$PIPECTL_CONTENTS_DIR/"

if [ "$OTHER_PACKAGES" ]; then
    OTHER_PACKAGES_DIR="$BUILD_DIR/other-packages"
    rm -rf "$OTHER_PACKAGES_DIR"
    mkdir -p "$OTHER_PACKAGES_DIR"
    echo "--> Other packages will be included:"
    for o_p in "${OTHER_PACKAGES[@]}"; do
        echo "-- $o_p"
        \cp -r "$o_p" "$OTHER_PACKAGES_DIR/"
    done

    \cp -r "$OTHER_PACKAGES_DIR" "$PIPECTL_CONTENTS_DIR/"
fi

echo "$BUILD_VERSION" > "$PIPECTL_CONTENTS_DIR/version.txt"

echo
echo "[BUILDING PIPECTL]"
export DEPLOY_BUILD_OUTPUT_PATH=$BUILD_OUTPUT_FILE
bash $BUILD_SCRIPT_PATH/pipectl/build-pipectl.sh --base64 "$PIPECTL_CONTENTS_DIR/"

if [ $? -ne 0 ]; then
    echo "ERROR occured while building pipectl executable, FAILING build"
    cleanup_build
    rm -f "$BUILD_OUTPUT_FILE"
    exit 1
fi

if [ "$KEEP_MANIFEST_FILES" ]; then
    BUILD_OUTPUT_DIR=$(dirname $BUILD_OUTPUT_FILE)
    mkdir -p "$BUILD_OUTPUT_DIR"
    mv "$DOCKERS_MANIFEST_DIR" "$BUILD_OUTPUT_DIR/" &> /dev/null
    mv "$CLOUD_IMAGES_MANIFEST_FILE" "$BUILD_OUTPUT_DIR/" &> /dev/null

    echo "\"-k|--keep-manifests\" option is specified. Docker/Images manifests are backed up to the $BUILD_OUTPUT_DIR folder"
fi

rm -rf "$BUILD_DIR"
