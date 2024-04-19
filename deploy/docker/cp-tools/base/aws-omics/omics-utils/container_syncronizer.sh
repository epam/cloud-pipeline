#!/bin/bash

function parse_options {
    while [[ $# -gt 0 ]]; do
        key="$1"
        case $key in
            --image_pull_config)
            export IMAGE_PULL_CONFIG=$2
            shift # past argument
            shift # past value
            ;;
            --image_build_config)
            export IMAGE_BUILD_CONFIG=$2
            shift # past argument
            shift # past value
            ;;
            --ecr)
            export PRIVATE_ECR=$2
            shift # past argument
            shift # past value
            ;;
        esac
    done
}

parse_options "$@"

if [ -z "${PRIVATE_ECR}" ]; then
    echo "AWS private ECR is not set! Please specify it with --ecr option. Exiting."
    exit 1
else
    echo "AWS private ECR is set to: ${PRIVATE_ECR}."
fi

if [ -n "$IMAGE_PULL_CONFIG" ]; then
    echo "Image pull config was set as:${IMAGE_PULL_CONFIG}. Will pull -> push images..."
fi

if [ -n "$IMAGE_BUILD_CONFIG" ]; then
    echo "Image build config was set as:${IMAGE_BUILD_CONFIG}. Will build -> push images..."
fi