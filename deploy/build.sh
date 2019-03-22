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
if [ -z "$CLOUD_IMAGES_MANIFEST_FILE" ]; then
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
        bash $BUILD_SCRIPT_PATH/infra/build-infra.sh    -p az \
                                                        -r "${BUILD_AZ_REGIONS}" \
                                                        -o "${CLOUD_IMAGES_MANIFEST_FILE}.az"
        cat ${CLOUD_IMAGES_MANIFEST_FILE}.az >> $CLOUD_IMAGES_MANIFEST_FILE
        rm -f ${CLOUD_IMAGES_MANIFEST_FILE}.az
    fi
else
    echo "Cloud images manifest is specified explicitely ($CLOUD_IMAGES_MANIFEST_FILE). Cloud image WILL NOT be rebuilt"
fi

###############
# Build dockers
###############

if ! check_installed "docker"; then
    echo "Docker is not installed. Please install it and rerun the script"
    exit 1
fi

echo
echo "[BUILDING/PUSHING DOCKER IMAGES]"
DOCKERS_MANIFEST_DIR="$BUILD_DIR/dockers-manifest"
DOCKERS_BUILD_OTHER_OPTIONS=""
if [ "$BUILD_INCLUDE_TESTS" ]; then
    echo "Test docker images will be included"
    DOCKERS_BUILD_OTHER_OPTIONS="$DOCKERS_BUILD_OTHER_OPTIONS -t"
fi
bash $BUILD_SCRIPT_PATH/docker/build-dockers.sh -c "$DOCKERS_MANIFEST_DIR" -s "$BUILD_SCRIPT_PATH/docker" -v "$BUILD_VERSION" $DOCKERS_BUILD_OTHER_OPTIONS

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

if [ "$KEEP_MANIFEST_FILES" ]; then
    BUILD_OUTPUT_DIR=$(dirname $BUILD_OUTPUT_FILE)
    mkdir -p "$BUILD_OUTPUT_DIR"
    mv "$DOCKERS_MANIFEST_DIR" "$BUILD_OUTPUT_DIR/" &> /dev/null
    mv "$CLOUD_IMAGES_MANIFEST_FILE" "$BUILD_OUTPUT_DIR/" &> /dev/null

    echo "\"-k|--keep-manifests\" option is specified. Docker/Images manifests are backed up to the $BUILD_OUTPUT_DIR folder"
fi

rm -rf "$BUILD_DIR"
