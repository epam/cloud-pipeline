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
            --public_registry_properties)
            export _PRIVATE_REGISTRY_NAMING_PROPERTIES=$2
            shift # past argument
            shift # past value
            ;;
        esac
    done
}

parse_options "$@"

export PRIVATE_REGISTRY_NAMING_PROPERTIES="${_PRIVATE_REGISTRY_NAMING_PROPERTIES:-/opt/omics-utils/public_registry_properties.json}"
if [ ! -f "$PRIVATE_REGISTRY_NAMING_PROPERTIES" ]; then
    echo "Can't find a file: ${PRIVATE_REGISTRY_NAMING_PROPERTIES}. Please --public_registry_properties with an existing config or left unchanged
    to use default value: /opt/omics-utils/public_registry_properties.json"
fi

if [ -z "${PRIVATE_ECR}" ]; then
    echo "AWS private ECR is not set! Please specify it with --ecr option. Exiting."
    exit 1
else
    echo "AWS private ECR is set to: ${PRIVATE_ECR}."
fi

if [ -n "$IMAGE_PULL_CONFIG" ]; then
    echo "Image pull config was set as: ${IMAGE_PULL_CONFIG}. Will pull -> push images..."
fi

_pull_image_log=$(tmpfile)
readarray -t images < <(jq -r --compact-output '.manifest[]' "${IMAGE_PULL_CONFIG}")
for image in "${images[@]}"; do

  docker pull "$image" &> "$_pull_image_log"
  if [ $? -ne 0 ]; then
      echo "There was a problem with pulling image $image ..."
      cat _pull_image_log
  fi

  private_image=$(python /opt/omics-utils/get_private_image_name.py "$image" "$PRIVATE_ECR" "${PRIVATE_REGISTRY_NAMING_PROPERTIES}")

  docker tag "$image" "$private_image"
  docker push "$private_image" &> "$_pull_image_log"
  if [ $? -ne 0 ]; then
      echo "There was a problem with pushing image $image to $private_image ..."
      cat _pull_image_log
  fi

done

if [ -n "$IMAGE_BUILD_CONFIG" ]; then
    echo "Image build config was set as:${IMAGE_BUILD_CONFIG}. Will build -> push images..."
fi

