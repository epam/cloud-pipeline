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

    ln -s dist "$docker_context_path/dist"
    docker build "$docker_context_path" -t "$docker_name" -f "$docker_file_path" "$@"
    docker push "$docker_name"
    rm "$docker_context_path/dist"

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

function build_and_push_image {
    local docker_context_path="$1"
    local docker_name="$2"
    shift
    shift

    ln -s dist "$docker_context_path/dist"
    docker build "$docker_context_path" -t "$docker_name" "$@"
    docker push "$docker_name"
    rm "$docker_context_path/dist"
}

if [ -z "$DOCKERS_VERSION" ]; then
    echo "DOCKERS_VERSION is not set via \"-v\" option. \"latest\" version will be used"
    DOCKERS_VERSION="latest"
fi

if [ -z "$CP_DOCKER_DIST_SRV" ]; then
    echo "CP_DOCKER_DIST_SRV is not set, https://index.docker.io/v1/ is used to authenticate against docker dist registry and create a kube secret"
    export CP_DOCKER_DIST_SRV="https://index.docker.io/v1/"
fi

if [ "${CP_DOCKER_DIST_SRV: -1}" != "/" ]; then
    echo "CP_DOCKER_DIST_SRV doesn't end with '/': ${CP_DOCKER_DIST_SRV}, will additionally add it."
    export CP_DOCKER_DIST_SRV="${CP_DOCKER_DIST_SRV}/"
fi

if [ -z "$CP_DOCKER_DIST_USER" ] || [ -z "$CP_DOCKER_DIST_PASS" ]; then
    echo "CP_DOCKER_DIST_USER or CP_DOCKER_DIST_PASS is not set, proceeding without registry authentication"
else
    docker login ${CP_DOCKER_DIST_SRV} -u $CP_DOCKER_DIST_USER -p $CP_DOCKER_DIST_PASS
    if [ $? -ne 0 ]; then
        echo "Error occured while logging into the distr docker regsitry, exiting"
        exit 1
    fi
fi

export CP_DIST_REPO_NAME=${CP_DIST_REPO_NAME:-"${CP_DOCKER_DIST_SRV}${CP_DOCKER_DIST_USER}/cloud-pipeline"}

if [ -z "$CP_DIST_REPO_NAME" ]; then
    CP_DIST_REPO_NAME="${CP_DOCKER_DIST_SRV}lifescience/cloud-pipeline"
fi

########################
# Cloud Pipeline dockers
########################

CP_API_DIST_URL_DEFAULT="https://s3.amazonaws.com/cloud-pipeline-oss-builds/builds/latest/develop/cloud-pipeline.latest.tgz"
if [ -z "$CP_API_DIST_URL" ]; then
    echo "CP_API_DIST_URL is not set, trying to use latest public distribution $CP_API_DIST_URL_DEFAULT"
    CP_API_DIST_URL="$CP_API_DIST_URL_DEFAULT"
fi

# todo: Get rid of cloud-pipeline.tgz usage in favor of service distributions
wget "$CP_API_DIST_URL" -O /tmp/cloud-pipeline.tgz
tar -xzf /tmp/cloud-pipeline.tgz -O dist
rm -rf /tmp/cloud-pipeline.tgz
unzip dist/bin/pipeline.jar -d /tmp/cloud-pipeline-jar
cp /tmp/cloud-pipeline-jar/BOOT-INF/classes/static/pipe-common.tar.gz dist/pipe-common.tar.gz
rm -rf /tmp/cloud-pipeline-jar

# API
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-api-srv" \
                     "${CP_API_DIST_NAME:-"$CP_DIST_REPO_NAME:api-srv-${DOCKERS_VERSION}"}"

# Basic IdP
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-idp" \
                     "${CP_IDP_DIST_NAME:-"$CP_DIST_REPO_NAME:idp-${DOCKERS_VERSION}"}"

# Docker registry
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-docker-registry" \
                     "${CP_REGISTRY_DIST_NAME:-"$CP_DIST_REPO_NAME:registry-${DOCKERS_VERSION}"}"

# EDGE
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-edge" \
                     "${CP_EDGE_DIST_NAME:-"$CP_DIST_REPO_NAME:edge-${DOCKERS_VERSION}"}"

# Docker comp
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-docker-comp" \
                     "${CP_DOCKER_COMP_DIST_NAME:-"$CP_DIST_REPO_NAME:docker-comp-${DOCKERS_VERSION}"}"

# Clair
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-clair" \
                     "${CP_CLAIR_DIST_NAME:-"$CP_DIST_REPO_NAME:clair-${DOCKERS_VERSION}"}"

# GitLab
# 9.4.0 version
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-git" \
                     "${CP_GITLAB_DIST_NAME:-"$CP_DIST_REPO_NAME:git-9-${DOCKERS_VERSION}"}" \
                     -f "$CP_DIST_DIR/Dockerfile.9.4"

# 15.4.3 version
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-git" \
                     "${CP_GITLAB_15_DIST_NAME:-"$CP_DIST_REPO_NAME:git-15-${DOCKERS_VERSION}"}" \
                     -f "$CP_DIST_DIR/Dockerfile.15.5"

# Notifier
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-notifier" \
                     "${CP_NOTIFIER_DIST_NAME:-"$CP_DIST_REPO_NAME:notifier-${DOCKERS_VERSION}"}"

# Search
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-search" \
                     "${CP_SEARCH_DIST_NAME:-"$CP_DIST_REPO_NAME:search-${DOCKERS_VERSION}"}" \

# Search ELK
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-search-elk" \
                     "${CP_SEARCH_ELK_DIST_NAME:-"$CP_DIST_REPO_NAME:search-elk-${DOCKERS_VERSION}"}"

# Heapster ELK
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-heapster-elk" \
                     "${CP_HEAPSTER_ELK_DIST_NAME:-"$CP_DIST_REPO_NAME:heapster-elk-${DOCKERS_VERSION}"}"

# Node logger
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-node-logger" \
                     "${CP_NODE_LOGGER_DIST_NAME:-"$CP_DIST_REPO_NAME:node-logger-${DOCKERS_VERSION}"}"

# Node Reporter
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-node-reporter" \
                     "${CP_NODE_REPORTER_DIST_NAME:-"$CP_DIST_REPO_NAME:node-reporter-${DOCKERS_VERSION}"}"

# Backups manager
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-bkp-worker" \
                     "${CP_BKP_WORKER_DIST_NAME:-"$CP_DIST_REPO_NAME:cp-bkp-worker-${DOCKERS_VERSION}"}"

# VM Monitor
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-vm-monitor" \
                     "${CP_VM_MONITOR_DIST_NAME:-"$CP_DIST_REPO_NAME:vm-monitor-${DOCKERS_VERSION}"}"

# Drive Mapping
\cp -r "$DOCKERS_SOURCES_PATH/../../scripts/nfs-roles-management" "$DOCKERS_SOURCES_PATH/cp-dav/"
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-dav" \
                     "${CP_DRIVE_MAPPING_DIST_NAME:-"$CP_DIST_REPO_NAME:dav-${DOCKERS_VERSION}"}"
rm -rf "$DOCKERS_SOURCES_PATH/cp-dav/nfs-roles-management"

# Share Service
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-share-srv" \
                     "${CP_SHARE_SRV_DIST_NAME:-"$CP_DIST_REPO_NAME:share-srv-${DOCKERS_VERSION}"}"

# Billing Service
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-billing-srv" \
                     "${CP_BILLING_SRV_DIST_NAME:-"$CP_DIST_REPO_NAME:billing-srv-${DOCKERS_VERSION}"}"

# GitLab Reader Service
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-gitlab-reader" \
                     "${CP_GITLAB_READER_SRV_DIST_NAME:-"$CP_DIST_REPO_NAME:gitlab-reader-${DOCKERS_VERSION}"}"

# Tinyproxy
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-tinyproxy" \
                     "${CP_TP_DIST_NAME:-"$CP_DIST_REPO_NAME:tinyproxy-${DOCKERS_VERSION}"}"

# Leader Elector
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-leader-elector" \
                     "${CP_ELECTOR_DIST_NAME:-"$CP_DIST_REPO_NAME:leader-elector-${DOCKERS_VERSION}"}"

# Run owner's policy manager
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-run-policy-manager" \
                     "${CP_RUN_POLICY_MANAGER_DIST_NAME:-"$CP_DIST_REPO_NAME:run-policy-manager-${DOCKERS_VERSION}"}"

# Pods DNS sync
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-dns-hosts-sync" \
                     "${CP_DNS_PODS_SYNC_DIST_NAME:-"$CP_DIST_REPO_NAME:dns-hosts-sync-${DOCKERS_VERSION}"}"

# Monitoring service
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-monitoring-srv" \
                     "${CP_MONITORING_SRV_DIST_NAME:-"$CP_DIST_REPO_NAME:monitoring-service-${DOCKERS_VERSION}"}"

# Deployment autoscaler
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-deployment-autoscaler" \
                     "${CP_DEPLOYMENT_AUTOSCALER_DIST_NAME:-"$CP_DIST_REPO_NAME:deployment-autoscaler-${DOCKERS_VERSION}"}"

# Storage Lifecycle Service
build_and_push_image "$DOCKERS_SOURCES_PATH/cp-storage-lifecycle-service" \
                     "${CP_STORAGE_LIFECYCLE_SERVICE_DIST_NAME:-"$CP_DIST_REPO_NAME:storage-lifecycle-service-${DOCKERS_VERSION}"}"

########################
# Base tools dockers
########################

BASE_TOOLS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tools/base

# Centos
# - Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/vanilla "$CP_DIST_REPO_NAME:tools-base-centos-7-${DOCKERS_VERSION}" "library/centos:7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/vanilla "$CP_DIST_REPO_NAME:tools-base-centos-7-${DOCKERS_VERSION}" "library/centos:latest"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/vanilla "$CP_DIST_REPO_NAME:tools-base-centos-7-optimized-${DOCKERS_VERSION}" "library/centos:7-optimized" --file "Dockerfile.optimized"
# - CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/cuda "$CP_DIST_REPO_NAME:tools-base-centos-7-cuda11-${DOCKERS_VERSION}" "library/centos-cuda:7-cuda11" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-centos7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/centos/cuda "$CP_DIST_REPO_NAME:tools-base-centos-7-cuda-${DOCKERS_VERSION}" "library/centos-cuda:latest" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-centos7"

# Ubuntu
# - Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/vanilla "$CP_DIST_REPO_NAME:tools-base-ubuntu-16.04-${DOCKERS_VERSION}" "library/ubuntu:16.04" --build-arg BASE_IMAGE="library/ubuntu:16.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/vanilla "$CP_DIST_REPO_NAME:tools-base-ubuntu-18.04-${DOCKERS_VERSION}" "library/ubuntu:18.04" --build-arg BASE_IMAGE="library/ubuntu:18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/vanilla "$CP_DIST_REPO_NAME:tools-base-ubuntu-18.04-${DOCKERS_VERSION}" "library/ubuntu:latest" --build-arg BASE_IMAGE="library/ubuntu:18.04"
# - CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/cuda "$CP_DIST_REPO_NAME:tools-base-ubuntu-18.04-cuda11-${DOCKERS_VERSION}" "library/ubuntu-cuda:18.04-cuda11" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-ubuntu18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/ubuntu/cuda "$CP_DIST_REPO_NAME:tools-base-ubuntu-18.04-cuda-${DOCKERS_VERSION}" "library/ubuntu-cuda:latest" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-ubuntu18.04"

# RStudio
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/rstudio "$CP_DIST_REPO_NAME:tools-base-rstudio-${DOCKERS_VERSION}" "library/rstudio:3.5.1"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/rstudio "$CP_DIST_REPO_NAME:tools-base-rstudio-${DOCKERS_VERSION}" "library/rstudio:4.0.0" --file "Dockerfile.el7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/rstudio "$CP_DIST_REPO_NAME:tools-base-rstudio-${DOCKERS_VERSION}" "library/rstudio:latest" --file "Dockerfile.el7"

# Cromwell
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/cromwell "$CP_DIST_REPO_NAME:tools-base-cromwell-${DOCKERS_VERSION}" "library/cromwell:latest"

# Nextflow
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/nextflow "$CP_DIST_REPO_NAME:tools-base-nextflow-${DOCKERS_VERSION}" "library/nextflow:latest" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-ubuntu18.04"

# Snakemake
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/snakemake "$CP_DIST_REPO_NAME:tools-base-snakemake-${DOCKERS_VERSION}" "library/snakemake:latest"

# CWL Toil
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/toil "$CP_DIST_REPO_NAME:tools-base-cwl-runner-${DOCKERS_VERSION}" "library/cwl-runner:latest"

# Luigi
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/luigi "$CP_DIST_REPO_NAME:tools-base-luigi-${DOCKERS_VERSION}" "library/luigi:latest"

# Jupyter
## - Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-310-${DOCKERS_VERSION}" "library/jupyter:conda-310" --spec "vanilla" --build-arg BASE_IMAGE="library/centos:7.7.1908" --build-arg ANACONDA_VERSION="3-py310_23.3.1-0"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-${DOCKERS_VERSION}" "library/jupyter:latest" --spec "vanilla" --build-arg BASE_IMAGE="library/centos:7.7.1908" --build-arg ANACONDA_VERSION="3-py310_23.3.1-0"

## - CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-310-cuda12-${DOCKERS_VERSION}" "library/jupyter-cuda:conda-310-cuda12" --spec "cuda" --build-arg BASE_IMAGE="nvidia/cuda:12.1.1-cudnn8-runtime-centos7" --build-arg ANACONDA_VERSION="3-py310_23.3.1-0"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter "$CP_DIST_REPO_NAME:tools-base-jupyter-cuda12-${DOCKERS_VERSION}" "library/jupyter-cuda:latest" --spec "cuda" --build-arg BASE_IMAGE="nvidia/cuda:12.1.1-cudnn8-runtime-centos7" --build-arg ANACONDA_VERSION="3-py310_23.3.1-0"

# JupyterLab
# Python2 version is not built due to https://github.com/jupyterlab/jupyterlab/issues/2096 (Python2 support is dropped since JupyterLab 0.33)
## - Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-310-${DOCKERS_VERSION}" "library/jupyter-lab:conda-310" --spec "vanilla" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-310-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-py310_23.3.1-0"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-${DOCKERS_VERSION}" "library/jupyter-lab:latest" --spec "vanilla" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-py310_23.3.1-0"

## - CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-310-cuda10-${DOCKERS_VERSION}" "library/jupyter-lab-cuda:conda-310-cuda12" --spec "cuda" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-310-cuda12-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-py310_23.3.1-0"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/jupyter-lab "$CP_DIST_REPO_NAME:tools-base-jupyter-lab-cuda12-${DOCKERS_VERSION}" "library/jupyter-lab-cuda:latest" --spec "cuda" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-jupyter-cuda12-${DOCKERS_VERSION}" --build-arg ANACONDA_VERSION="3-py310_23.3.1-0"


# Desktop NoMachine
## - Ubuntu
### -- Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-16.04-${DOCKERS_VERSION}" "library/ubuntu-nomachine:16.04" --file "ubuntu/Dockerfile" --spec "ubuntu/vanilla" --build-arg BASE_IMAGE="library/ubuntu:16.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-${DOCKERS_VERSION}" "library/ubuntu-nomachine:18.04" --file "ubuntu/Dockerfile" --spec "ubuntu/vanilla" --build-arg BASE_IMAGE="library/ubuntu:18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-${DOCKERS_VERSION}" "library/ubuntu-nomachine:latest" --file "ubuntu/Dockerfile" --spec "ubuntu/vanilla" --build-arg BASE_IMAGE="library/ubuntu:18.04"

### -- CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-cuda11-${DOCKERS_VERSION}" "library/ubuntu-nomachine-cuda:18.04-cuda11" --file "ubuntu/Dockerfile" --spec "ubuntu/cuda" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-ubuntu18.04"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-cuda-${DOCKERS_VERSION}" "library/ubuntu-nomachine-cuda:latest" --file "ubuntu/Dockerfile" --spec "ubuntu/cuda" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-ubuntu18.04"

## - Centos
### -- Vanilla
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-${DOCKERS_VERSION}" "library/centos-nomachine:7" --file "centos/Dockerfile" --spec "centos/vanilla" --build-arg BASE_IMAGE="library/centos:7.7.1908"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-${DOCKERS_VERSION}" "library/centos-nomachine:latest" --file "centos/Dockerfile" --spec "centos/vanilla" --build-arg BASE_IMAGE="library/centos:7.7.1908"

### -- CUDA
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-cuda11-${DOCKERS_VERSION}" "library/centos-nomachine-cuda:7-cuda11" --file "centos/Dockerfile" --spec "centos/cuda" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-centos7"
build_and_push_tool $BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/nomachine "$CP_DIST_REPO_NAME:tools-base-centos-nomachine-7-cuda-${DOCKERS_VERSION}" "library/centos-nomachine-cuda:latest" --file "centos/Dockerfile" --spec "centos/cuda" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-centos7"

# Desktop noVNC
## - Ubuntu
### -- Vanilla
build_and_push_tool "$BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC" "$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-18.04-${DOCKERS_VERSION}" "library/ubuntu-novnc:18.04" --file "ubuntu/Dockerfile" --build-arg BASE_IMAGE="library/ubuntu:18.04"
build_and_push_tool "$BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC" "$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-18.04-${DOCKERS_VERSION}" "library/ubuntu-novnc:latest" --file "ubuntu/Dockerfile" --build-arg BASE_IMAGE="library/ubuntu:18.04"

### -- CUDA
build_and_push_tool "$BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC" "$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-18.04-cuda11-${DOCKERS_VERSION}" "library/ubuntu-novnc-cuda:18.04-cuda11" --file "ubuntu/Dockerfile" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-ubuntu18.04"
build_and_push_tool "$BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC" "$CP_DIST_REPO_NAME:tools-base-ubuntu-novnc-18.04-cuda11-${DOCKERS_VERSION}" "library/ubuntu-novnc-cuda:latest" --file "ubuntu/Dockerfile" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-ubuntu18.04"

## - Centos
### -- Vanilla
build_and_push_tool "$BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC" "$CP_DIST_REPO_NAME:tools-base-centos-novnc-7-${DOCKERS_VERSION}" "library/centos-novnc:7" --file "centos/Dockerfile" --build-arg BASE_IMAGE="library/centos:7.7.1908"
build_and_push_tool "$BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC" "$CP_DIST_REPO_NAME:tools-base-centos-novnc-7-${DOCKERS_VERSION}" "library/centos-novnc:latest" --file "centos/Dockerfile" --build-arg BASE_IMAGE="library/centos:7.7.1908"

### -- CUDA
build_and_push_tool "$BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC" "$CP_DIST_REPO_NAME:tools-base-centos-novnc-7-cuda11-${DOCKERS_VERSION}" "library/centos-novnc-cuda:7-cuda11" --file "centos/Dockerfile" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-centos7"
build_and_push_tool "$BASE_TOOLS_DOCKERS_SOURCES_PATH/desktop/noVNC" "$CP_DIST_REPO_NAME:tools-base-centos-novnc-7-cuda11-${DOCKERS_VERSION}" "library/centos-novnc-cuda:latest" --file "centos/Dockerfile" --build-arg BASE_IMAGE="nvidia/cuda:11.3.1-cudnn8-runtime-centos7"

########################
# NGS tools dockers
########################

NGS_TOOLS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tools/ngs

# bcl2fastq2
build_and_push_tool $NGS_TOOLS_DOCKERS_SOURCES_PATH/bcl2fastq2 "$CP_DIST_REPO_NAME:tools-ngs-bcl2fastq2-${DOCKERS_VERSION}" "ngs/bcl2fastq2:latest"

# ngs-essential
# !!! Disabled due to multiple build errors. To be revised. !!!
# build_and_push_tool $NGS_TOOLS_DOCKERS_SOURCES_PATH/ngs-essential "$CP_DIST_REPO_NAME:tools-ngs-essential-${DOCKERS_VERSION}" "ngs/ngs-essential:latest"

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

# QuPath with nomachine
build_and_push_tool $RESEARCH_TOOLS_DOCKERS_SOURCES_PATH/qupath "$CP_DIST_REPO_NAME:tools-research-qupath-${DOCKERS_VERSION}" "library/qupath:latest" --spec "nomachine" --build-arg BASE_IMAGE="$CP_DIST_REPO_NAME:tools-base-ubuntu-nomachine-18.04-${DOCKERS_VERSION}"


########################
# MD tools dockers
########################

MD_TOOLS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tools/md

# FIXME: Add gromacs and namd

########################
# System dockers
########################

SYSTEM_TOOLS_DOCKERS_SOURCES_PATH=$DOCKERS_SOURCES_PATH/cp-tools/system

build_and_push_tool $SYSTEM_TOOLS_DOCKERS_SOURCES_PATH/system-job "$CP_DIST_REPO_NAME:tools-system-system-job-${DOCKERS_VERSION}" "system/system-job-launcher:latest"

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
