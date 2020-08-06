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


set -e

DOCKERS_MANIFEST_PATH=~/.pipe/tmp/dockers
if [[ "$1" == '-c' ]]; then
    shift
	DOCKERS_MANIFEST_PATH="$1"
	shift
fi

DOCKERS_SOURCES_PATH="."
if [[ "$1" == '-s' ]]; then
    shift
	DOCKERS_SOURCES_PATH="$1"
	shift
fi

DOCKERS_VERSION=""
if [[ "$1" == '-v' ]]; then
    shift
	DOCKERS_VERSION="$1"
    shift
fi

DOCKERS_INCLUDE_TESTS=""
if [[ "$1" == '-t' ]]; then
    shift
	DOCKERS_INCLUDE_TESTS="yes"
fi

function build_and_push_tool {
    local docker_context_path="$1"
    local docker_name="$2"
    local docker_pretty_name="$3"
    shift
    shift
    shift

    local docker_file_path="${docker_context_path}/Dockerfile"
    if [ "$1" == "--file" ]; then
        docker_file_path="$2"
        if [[ "$docker_file_path" != "/"* ]]; then
            docker_file_path="${docker_context_path}/${docker_file_path}"
        fi
        shift
        shift
    fi

    local docker_spec_path="$docker_context_path"
    if [ "$1" == "--spec" ]; then
        docker_spec_path="$2"
        if [[ "$docker_spec_path" != "/"* ]]; then
            docker_spec_path="${docker_context_path}/${docker_spec_path}"
        fi
        shift
        shift
    fi

    docker build "$docker_context_path" -t "$docker_name" -f "$docker_file_path" "$@"
    docker push "$docker_name"

    docker_manifest_file_path=$DOCKERS_MANIFEST_PATH/manifest.txt
    mkdir -p $DOCKERS_MANIFEST_PATH
    echo "$docker_name,$docker_pretty_name" >> "$docker_manifest_file_path"

    mkdir -p $DOCKERS_MANIFEST_PATH/$docker_pretty_name
    
    docker_icon_path="$docker_spec_path/icon.png"
    [ -f "$docker_icon_path" ] && cp "$docker_icon_path" "$DOCKERS_MANIFEST_PATH/$docker_pretty_name/"
    docker_readme_path="$docker_spec_path/README.md"
    [ -f "$docker_readme_path" ] && cp "$docker_readme_path" "$DOCKERS_MANIFEST_PATH/$docker_pretty_name/"
    docker_spec_file_path="$docker_spec_path/spec.json"
    [ -f "$docker_spec_file_path" ] && cp "$docker_spec_file_path" "$DOCKERS_MANIFEST_PATH/$docker_pretty_name/"

    return 0
}

if [ -z "$DOCKERS_VERSION" ]; then
    echo "DOCKERS_VERSION is not set via \"-v\" option. \"latest\" version will be used"
    DOCKERS_VERSION="latest"
fi

if [ -z "$CP_DOCKER_DIST_USER" ] || [ -z "$CP_DOCKER_DIST_PASS" ]; then
    echo "CP_DOCKER_DIST_USER or CP_DOCKER_DIST_PASS is not set, proceeding without registry authentication"
else
    docker login -u $CP_DOCKER_DIST_USER -p $CP_DOCKER_DIST_PASS
    if [ $? -ne 0 ]; then
        echo "Error occured while logging into the distr docker regsitry, exiting"
        exit 1
    fi
fi

export CP_DIST_REPO_NAME=${CP_DIST_REPO_NAME:-"$CP_DOCKER_DIST_USER/cloud-pipeline"}

if [ -z "$CP_DIST_REPO_NAME" ]; then
    CP_DIST_REPO_NAME="lifescience/cloud-pipeline"
fi

########################
# Cloud Pipeline dockers
########################

# API
CP_API_DIST_NAME=${CP_API_DIST_NAME:-"$CP_DIST_REPO_NAME:api-srv-${DOCKERS_VERSION}"}

CP_API_DIST_URL_DEFAULT="https://s3.amazonaws.com/cloud-pipeline-oss-builds/builds/latest/develop/cloud-pipeline.latest.tgz"
if [ -z "$CP_API_DIST_URL" ]; then
    echo "CP_API_DIST_URL is not set, trying to use latest public distribution $CP_API_DIST_URL_DEFAULT"
    CP_API_DIST_URL="$CP_API_DIST_URL_DEFAULT"
fi

docker build    $DOCKERS_SOURCES_PATH/cp-api-srv \
                -t "$CP_API_DIST_NAME" \
                --build-arg CP_API_DIST_URL="$CP_API_DIST_URL" && \
docker push "$CP_API_DIST_NAME"

# Basic IdP
CP_IDP_DIST_NAME=${CP_IDP_DIST_NAME:-"$CP_DIST_REPO_NAME:idp-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-idp \
                -t "$CP_IDP_DIST_NAME"
docker push "$CP_IDP_DIST_NAME"

# Docker registry
CP_IDP_DIST_NAME=${CP_DOCKER_DIST_NAME:-"$CP_DIST_REPO_NAME:registry-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-docker-registry \
                -t "$CP_IDP_DIST_NAME"
docker push "$CP_IDP_DIST_NAME"

# EDGE
CP_EDGE_DIST_NAME=${CP_EDGE_DIST_NAME:-"$CP_DIST_REPO_NAME:edge-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-edge \
                -t "$CP_EDGE_DIST_NAME"
docker push "$CP_EDGE_DIST_NAME"

# Docker comp
CP_DOCKER_COMP_DIST_NAME=${CP_DOCKER_COMP_DIST_NAME:-"$CP_DIST_REPO_NAME:docker-comp-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-docker-comp \
                -t "$CP_DOCKER_COMP_DIST_NAME" \
                --build-arg CP_API_DIST_URL="$CP_API_DIST_URL"
docker push "$CP_DOCKER_COMP_DIST_NAME"

# Clair
CP_CLAIR_DIST_NAME=${CP_CLAIR_DIST_NAME:-"$CP_DIST_REPO_NAME:clair-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-clair \
                -t "$CP_CLAIR_DIST_NAME"
docker push "$CP_CLAIR_DIST_NAME"

# GitLab
CP_GITLAB_DIST_NAME=${CP_GITLAB_DIST_NAME:-"$CP_DIST_REPO_NAME:git-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-git \
                -t "$CP_GITLAB_DIST_NAME"
docker push "$CP_GITLAB_DIST_NAME"

# Notifier
CP_NOTIFIER_DIST_NAME=${CP_NOTIFIER_DIST_NAME:-"$CP_DIST_REPO_NAME:notifier-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-notifier \
                -t "$CP_NOTIFIER_DIST_NAME" \
                --build-arg CP_API_DIST_URL="$CP_API_DIST_URL"
docker push "$CP_NOTIFIER_DIST_NAME"

# Search
CP_SEARCH_DIST_NAME=${CP_SEARCH_DIST_NAME:-"$CP_DIST_REPO_NAME:search-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-search \
                -t "$CP_SEARCH_DIST_NAME" \
                --build-arg CP_API_DIST_URL="$CP_API_DIST_URL"
docker push "$CP_SEARCH_DIST_NAME"

# Search ELK
CP_SEARCH_ELK_DIST_NAME=${CP_SEARCH_ELK_DIST_NAME:-"$CP_DIST_REPO_NAME:search-elk-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-search-elk \
                -t "$CP_SEARCH_ELK_DIST_NAME"
docker push "$CP_SEARCH_ELK_DIST_NAME"

# Node logger
CP_NODE_LOGGER_DIST_NAME=${CP_NODE_LOGGER_DIST_NAME:-"$CP_DIST_REPO_NAME:node-logger-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-node-logger \
                -t "$CP_NODE_LOGGER_DIST_NAME"
docker push "$CP_NODE_LOGGER_DIST_NAME"

# Backups manager
CP_BKP_WORKER_DIST_NAME=${CP_BKP_WORKER_DIST_NAME:-"$CP_DIST_REPO_NAME:cp-bkp-worker-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-bkp-worker \
                -t "$CP_BKP_WORKER_DIST_NAME"
docker push "$CP_BKP_WORKER_DIST_NAME"

# VM Monitor
CP_VM_MONITOR_DIST_NAME=${CP_VM_MONITOR_DIST_NAME:-"$CP_DIST_REPO_NAME:vm-monitor-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-vm-monitor \
                -t "$CP_VM_MONITOR_DIST_NAME" \
                --build-arg CP_API_DIST_URL="$CP_API_DIST_URL"
docker push "$CP_VM_MONITOR_DIST_NAME"

# Drive Mapping
CP_DRIVE_MAPPING_DIST_NAME=${CP_DRIVE_MAPPING_DIST_NAME:-"$CP_DIST_REPO_NAME:dav-${DOCKERS_VERSION}"}
\cp -r $DOCKERS_SOURCES_PATH/../../scripts/nfs-roles-management $DOCKERS_SOURCES_PATH/cp-dav/
docker build    $DOCKERS_SOURCES_PATH/cp-dav \
                -t "$CP_DRIVE_MAPPING_DIST_NAME"
docker push "$CP_DRIVE_MAPPING_DIST_NAME"
rm -rf $DOCKERS_SOURCES_PATH/cp-dav

# Share Service
CP_SHARE_SRV_DIST_NAME=${CP_SHARE_SRV_DIST_NAME:-"$CP_DIST_REPO_NAME:share-srv-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-share-srv \
                -t "$CP_SHARE_SRV_DIST_NAME" \
                --build-arg CP_API_DIST_URL="$CP_API_DIST_URL"
docker push "$CP_SHARE_SRV_DIST_NAME"

# Billing Service
CP_BILLING_SRV_DIST_NAME=${CP_BILLING_SRV_DIST_NAME:-"$CP_DIST_REPO_NAME:billing-srv-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-billing-srv \
                -t "$CP_BILLING_SRV_DIST_NAME" \
                --build-arg CP_API_DIST_URL="$CP_API_DIST_URL"
docker push "$CP_BILLING_SRV_DIST_NAME"

# Tinyproxy
CP_TP_DIST_NAME=${CP_TP_DIST_NAME:-"$CP_DIST_REPO_NAME:tinyproxy-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-tinyproxy \
                -t "$CP_TP_DIST_NAME"
docker push "$CP_TP_DIST_NAME"

# Leader Elector
CP_ELECTOR_DIST_NAME=${CP_ELECTOR_DIST_NAME:-"$CP_DIST_REPO_NAME:leader-elector-${DOCKERS_VERSION}"}
docker build    $DOCKERS_SOURCES_PATH/cp-leader-elector \
                -t "$CP_ELECTOR_DIST_NAME"
docker push "$CP_ELECTOR_DIST_NAME"

########################
# Base tools dockers
########################

BASE_TOOLS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tools/base

# Centos
# - Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/vanilla "$CP_DIST_REPO_NAME:tools-base-centos-7-${DOCKERS_VERSION}" "library/centos:7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/vanilla "$CP_DIST_REPO_NAME:tools-base-centos-7-${DOCKERS_VERSION}" "library/centos:latest"
# - CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/cuda "$CP_DIST_REPO_NAME:tools-base-centos-7-cuda9-${DOCKERS_VERSION}" "library/centos-cuda:7-cuda9" --build-arg BASE_IMAGE="nvidia/cuda:9.0-cudnn7-runtime-centos7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/cuda "$CP_DIST_REPO_NAME:tools-base-centos-7-cuda10-${DOCKERS_VERSION}" "library/centos-cuda:7-cuda10" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-centos7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/cuda "$CP_DIST_REPO_NAME:tools-base-centos-7-cuda-${DOCKERS_VERSION}" "library/centos-cuda:latest" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-centos7"

# Ubuntu
# - Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/vanilla "$CP_DIST_REPO_NAME:tools-base-ubuntu-16.04-${DOCKERS_VERSION}" "library/ubuntu:16.04" --build-arg BASE_IMAGE="library/ubuntu:16.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/vanilla "$CP_DIST_REPO_NAME:tools-base-ubuntu-18.04-${DOCKERS_VERSION}" "library/ubuntu:18.04" --build-arg BASE_IMAGE="library/ubuntu:18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/vanilla "$CP_DIST_REPO_NAME:tools-base-ubuntu-18.04-${DOCKERS_VERSION}" "library/ubuntu:latest" --build-arg BASE_IMAGE="library/ubuntu:18.04"
# - CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/cuda "$CP_DIST_REPO_NAME:tools-base-ubuntu-16.04-cuda9-${DOCKERS_VERSION}" "library/ubuntu-cuda:16-cuda9" --build-arg BASE_IMAGE="nvidia/cuda:9.0-cudnn7-runtime-ubuntu16.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/cuda "$CP_DIST_REPO_NAME:tools-base-ubuntu-18.04-cuda10-${DOCKERS_VERSION}" "library/ubuntu-cuda:18-cuda10" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-ubuntu18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/cuda "$CP_DIST_REPO_NAME:tools-base-ubuntu-18.04-cuda-${DOCKERS_VERSION}" "library/ubuntu-cuda:latest" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-ubuntu18.04"

# RStudio
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/rstudio "$CP_DIST_REPO_NAME:tools-base-rstudio-${DOCKERS_VERSION}" "library/rstudio:3.5.1"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/rstudio "$CP_DIST_REPO_NAME:tools-base-rstudio-${DOCKERS_VERSION}" "library/rstudio:4.0.0" --file "Dockerfile.el7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/rstudio "$CP_DIST_REPO_NAME:tools-base-rstudio-${DOCKERS_VERSION}" "library/rstudio:latest" --file "Dockerfile.el7"

# Cromwell
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/cromwell "$CP_DIST_REPO_NAME:tools-base-cromwell-${DOCKERS_VERSION}" "library/cromwell:latest"

# Nextflow
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/nextflow "$CP_DIST_REPO_NAME:tools-base-nextflow-${DOCKERS_VERSION}" "library/nextflow:latest"

# Snakemake
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/snakemake "$CP_DIST_REPO_NAME:tools-base-snakemake-${DOCKERS_VERSION}" "library/snakemake:latest"

# Luigi
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/luigi "$CP_DIST_REPO_NAME:tools-base-luigi-${DOCKERS_VERSION}" "library/luigi:latest"

# Jupyter
## - Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-2-${DOCKERS_VERSION}" "library/jupyter:conda-2" --spec "vanilla" --build-arg BASE_IMAGE="library/centos:7" --build-arg ANACONDA_VERSION="2-latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-3-${DOCKERS_VERSION}" "library/jupyter:conda-3" --spec "vanilla" --build-arg BASE_IMAGE="library/centos:7" --build-arg ANACONDA_VERSION="3-latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-${DOCKERS_VERSION}" "library/jupyter:latest" --spec "vanilla" --build-arg BASE_IMAGE="library/centos:7" --build-arg ANACONDA_VERSION="3-latest"

## - CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-2-cuda9-${DOCKERS_VERSION}" "library/jupyter-cuda:conda-2-cuda9" --spec "cuda" --build-arg BASE_IMAGE="nvidia/cuda:9.0-cudnn7-runtime-centos7" --build-arg ANACONDA_VERSION="2-latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-3-cuda9-${DOCKERS_VERSION}" "library/jupyter-cuda:conda-3-cuda9" --spec "cuda" --build-arg BASE_IMAGE="nvidia/cuda:9.0-cudnn7-runtime-centos7" --build-arg ANACONDA_VERSION="3-latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-3-cuda10-${DOCKERS_VERSION}" "library/jupyter-cuda:conda-3-cuda10" --spec "cuda" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-centos7" --build-arg ANACONDA_VERSION="3-latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-cuda9-${DOCKERS_VERSION}" "library/jupyter-cuda:latest" --spec "cuda" --build-arg BASE_IMAGE="nvidia/cuda:9.0-cudnn7-runtime-centos7" --build-arg ANACONDA_VERSION="3-latest"

# JupyterLab
# Python2 version is not built due to https://github.com/jupyterlab/jupyterlab/issues/2096 (Python2 support is dropped since JupyterLab 0.33)
## - Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-3-${DOCKERS_VERSION}" "library/jupyter-lab:conda-3" --spec "vanilla" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-3-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-${DOCKERS_VERSION}" "library/jupyter-lab:latest" --spec "vanilla" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-latest"

## - CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-3-cuda9-${DOCKERS_VERSION}" "library/jupyter-lab-cuda:conda-3-cuda9" --spec "cuda" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-3-cuda9-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-3-cuda10-${DOCKERS_VERSION}" "library/jupyter-lab-cuda:conda-3-cuda10" --spec "cuda" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-3-cuda10-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-cuda9-${DOCKERS_VERSION}" "library/jupyter-lab-cuda:latest" --spec "cuda" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-cuda9-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-latest"


# Desktop
## - Ubuntu
### -- Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-16.04-${DOCKERS_VERSION}" "library/ubuntu-nomachine:16.04" --file "ubuntu/Dockerfile" --spec "ubuntu/vanilla" --build-arg BASE_IMAGE="library/ubuntu:16.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-${DOCKERS_VERSION}" "library/ubuntu-nomachine:18.04" --file "ubuntu/Dockerfile" --spec "ubuntu/vanilla" --build-arg BASE_IMAGE="library/ubuntu:18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-${DOCKERS_VERSION}" "library/ubuntu-nomachine:latest" --file "ubuntu/Dockerfile" --spec "ubuntu/vanilla" --build-arg BASE_IMAGE="library/ubuntu:18.04"

### -- Vanilla + noVNC
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC "$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-16.04-${DOCKERS_VERSION}" "library/ubuntu-novnc:16.04" --build-arg BASE_IMAGE="library/ubuntu:16.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC "$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-18.04-${DOCKERS_VERSION}" "library/ubuntu-novnc:18.04" --build-arg BASE_IMAGE="library/ubuntu:18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC "$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-${DOCKERS_VERSION}" "library/ubuntu-novnc:latest" --build-arg BASE_IMAGE="library/ubuntu:18.04"

### -- CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-16.04-cuda9-${DOCKERS_VERSION}" "library/ubuntu-nomachine-cuda:16-cuda9" --file "ubuntu/Dockerfile" --spec "ubuntu/cuda" --build-arg BASE_IMAGE="nvidia/cuda:9.0-cudnn7-runtime-ubuntu16.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-cuda10-${DOCKERS_VERSION}" "library/ubuntu-nomachine-cuda:18-cuda10" --file "ubuntu/Dockerfile" --spec "ubuntu/cuda" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-ubuntu18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-cuda-${DOCKERS_VERSION}" "library/ubuntu-nomachine-cuda:latest" --file "ubuntu/Dockerfile" --spec "ubuntu/cuda" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-ubuntu18.04"

## - Centos
### -- Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-${DOCKERS_VERSION}" "library/centos-nomachine:7" --file "centos/Dockerfile" --spec "centos/vanilla" --build-arg BASE_IMAGE="library/centos:7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-${DOCKERS_VERSION}" "library/centos-nomachine:latest" --file "centos/Dockerfile" --spec "centos/vanilla" --build-arg BASE_IMAGE="library/centos:7"

### -- CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-cuda9-${DOCKERS_VERSION}" "library/centos-nomachine-cuda:7-cuda9" --file "centos/Dockerfile" --spec "centos/cuda" --build-arg BASE_IMAGE="nvidia/cuda:9.0-cudnn7-runtime-centos7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-cuda10-${DOCKERS_VERSION}" "library/centos-nomachine-cuda:7-cuda10" --file "centos/Dockerfile" --spec "centos/cuda" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-centos7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-cuda-${DOCKERS_VERSION}" "library/centos-nomachine-cuda:latest" --file "centos/Dockerfile" --spec "centos/cuda" --build-arg BASE_IMAGE="nvidia/cuda:10.0-cudnn7-runtime-centos7"


########################
# NGS tools dockers
########################

NGS_TOOLS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tools/ngs

# bcl2fastq2
build_and_push_tool $NGS_TOOLS_DOCKERS_SOURCES_PATH/bcl2fastq2 "$CP_DIST_REPO_NAME:tools-ngs-bcl2fastq2-${DOCKERS_VERSION}" "ngs/bcl2fastq2:latest"

# ngs-essential
build_and_push_tool $NGS_TOOLS_DOCKERS_SOURCES_PATH/ngs-essential "$CP_DIST_REPO_NAME:tools-ngs-essential-${DOCKERS_VERSION}" "ngs/ngs-essential:latest"

# cellranger
export CELLRANGER_URL=${CELLRANGER_URL:-"https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/cellranger/cellranger-2.1.0.tar.gz https://s3.amazonaws.com/cloud-pipeline-oss-builds/tools/cellranger/cellranger-3.0.2.tar.gz"}
build_and_push_tool $NGS_TOOLS_DOCKERS_SOURCES_PATH/cellranger "$CP_DIST_REPO_NAME:tools-ngs-cellranger-${DOCKERS_VERSION}" "ngs/cellranger:latest" --build-arg CELLRANGER_URL="$CELLRANGER_URL"

# msgen
build_and_push_tool $NGS_TOOLS_DOCKERS_SOURCES_PATH/msgen "$CP_DIST_REPO_NAME:tools-ngs-msgen-${DOCKERS_VERSION}" "ngs/msgen:latest"

# bioconductor
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/rstudio "$CP_DIST_REPO_NAME:tools-ngs-bioconductor-${DOCKERS_VERSION}" "ngs/bioconductor:latest" --spec "../../ngs/bioconductor" --build-arg BASE_IMAGE="bioconductor/release_core2:R3.5.3_Bioc3.8"

########################
# Research environments dockers
########################

RESEARCH_TOOLS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tools/research

# Spyder with noVNC
build_and_push_tool $RESEARCH_TOOLS_DOCKERS_SOURCES_PATH/spyder "$CP_DIST_REPO_NAME:tools-research-spyder-novnc-py37-${DOCKERS_VERSION}" "library/spyder-novnc:3.7" --spec "noVNC" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-${DOCKERS_VERSION}"
build_and_push_tool $RESEARCH_TOOLS_DOCKERS_SOURCES_PATH/spyder "$CP_DIST_REPO_NAME:tools-research-spyder-novnc-${DOCKERS_VERSION}" "library/spyder-novnc:latest" --spec "noVNC" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-${DOCKERS_VERSION}"

# Spyder with nomachine
build_and_push_tool $RESEARCH_TOOLS_DOCKERS_SOURCES_PATH/spyder "$CP_DIST_REPO_NAME:tools-research-spyder-nomachine-py37-${DOCKERS_VERSION}" "library/spyder-nomachine:3.7" --spec "nomachine" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-${DOCKERS_VERSION}"
build_and_push_tool $RESEARCH_TOOLS_DOCKERS_SOURCES_PATH/spyder "$CP_DIST_REPO_NAME:tools-research-spyder-nomachine-${DOCKERS_VERSION}" "library/spyder-nomachine:latest" --spec "nomachine" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-${DOCKERS_VERSION}"


########################
# MD tools dockers
########################

MD_TOOLS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tools/md

# FIXME: Add gromacs and namd


########################
# E2E tests dockers
########################

if [ "$DOCKERS_INCLUDE_TESTS" == "yes" ]; then

    E2E_TESTS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tests

    # endpoints-e2e-test
    build_and_push_tool $E2E_TESTS_DOCKERS_SOURCES_PATH/e2e-endpoints "$CP_DIST_REPO_NAME:tools-tests-e2e-endpoints-${DOCKERS_VERSION}" "tests/e2e-endpoints:latest"
    build_and_push_tool $E2E_TESTS_DOCKERS_SOURCES_PATH/e2e-endpoints "$CP_DIST_REPO_NAME:tools-tests-e2e-endpoints-${DOCKERS_VERSION}" "tests/e2e-endpoints:test"

    # empty-settings-test
    build_and_push_tool $E2E_TESTS_DOCKERS_SOURCES_PATH/e2e-empty "$CP_DIST_REPO_NAME:tools-tests-e2e-empty-${DOCKERS_VERSION}" "tests/e2e-empty:latest"

fi
