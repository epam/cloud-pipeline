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
            export PRIVATE_REGISTRY_NAMING_PROPERTIES=$2
            shift # past argument
            shift # past value
            ;;
            --workflow_source)
            export WORKFLOW_SOURCE_DIR=$2
            shift # past argument
            shift # past value
            ;;
        esac
    done
}

parse_options "$@"

export _RESYNC_IMAGES=${RESYNC_IMAGES:-true}
export GET_PRIVATE_IMAGE_NAME_SCRIPT=/opt/omics/utils/get_private_image_name.py
export PRIVATE_REGISTRY_NAMING_PROPERTIES="${PRIVATE_REGISTRY_NAMING_PROPERTIES:-/opt/omics/utils/public_registry_properties.json}"
if [ ! -f "$PRIVATE_REGISTRY_NAMING_PROPERTIES" ]; then
    echo "Can't find a file: ${PRIVATE_REGISTRY_NAMING_PROPERTIES}. Please --public_registry_properties with an existing config or left unchanged to use default value: /opt/omics-utils/public_registry_properties.json"
    exit 1
fi

if [ -n "$WORKFLOW_SOURCE_DIR" ]; then
    IMAGE_PULL_CONFIG="${IMAGE_PULL_CONFIG:-$WORKFLOW_SOURCE_DIR/container_image_manifest.json}"
    IMAGE_BUILD_CONFIG="${IMAGE_BUILD_CONFIG:-$WORKFLOW_SOURCE_DIR/container_build_manifest.json}"
fi

_sync_image_log=$(mktemp)
if [ -z "${PRIVATE_ECR}" ]; then
    echo "AWS private ECR is not set! Please specify it with --ecr option. Exiting."
    exit 1
else
    echo "AWS private ECR is set to: ${PRIVATE_ECR}. Logging in ..."
     aws_region=$(echo $PRIVATE_ECR | grep -oP ".+.dkr.ecr.\K.+(?=.amazonaws.com)")
    docker login -u AWS -p "$(aws ecr get-login-password --region $aws_region)" "${PRIVATE_ECR}" &> "$_sync_image_log"
    if [ $? -ne 0 ]; then
        echo "There was a problem with login to ECR $PRIVATE_ECR ..."
        cat "$_sync_image_log"
        exit 1
    else
        echo "Successfully logged in ECR $PRIVATE_ECR ..."
    fi
fi

_ECR_REPO_POLICY_DOC=/tmp/ecr_policy.json
cat > $_ECR_REPO_POLICY_DOC <<EOF
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Sid": "omics workflow",
            "Effect": "Allow",
            "Principal": {
                "Service": "omics.amazonaws.com"
            },
            "Action": [
                "ecr:GetDownloadUrlForLayer",
                "ecr:BatchGetImage",
                "ecr:BatchCheckLayerAvailability"
            ]
        }
    ]
}
EOF

if [ -n "$IMAGE_PULL_CONFIG" ] && [ -f "$IMAGE_PULL_CONFIG" ]; then
    echo "Image pull config was set as: ${IMAGE_PULL_CONFIG}. Will pull -> push images..."
    _pull_image_log=$(mktemp)
    readarray -t images < <(jq -r --compact-output '.manifest[]' "${IMAGE_PULL_CONFIG}")
    for image in "${images[@]}"; do
        echo "Image $image will pulled -> pushed..."

        private_image=$(python "$GET_PRIVATE_IMAGE_NAME_SCRIPT" --image "$image" --ecr "$PRIVATE_ECR" --images-config "${PRIVATE_REGISTRY_NAMING_PROPERTIES}" 2> "$_pull_image_log")
        if [ $? -ne 0 ]; then
            echo "There was a problem with configuring private image name for $image ..."
            cat "$_pull_image_log"
            exit 1
        else
            echo "Private ECR image path is set to: $private_image"
        fi

        _ECR_REPO_NAME=$(echo "${private_image#"${PRIVATE_ECR}/"}" | cut -d: -f1)
        _ECR_REPO_TAG=$(echo "${_ECR_REPO_NAME}" | grep ":"  &> /dev/null && echo "${_ECR_REPO_NAME}" | cut -d: -f2 || echo latest)
        if ! aws ecr describe-images --repository-name="$_ECR_REPO_NAME" --region "$aws_region" &> /dev/null; then
            if aws ecr create-repository --region "$aws_region" --repository-name "$_ECR_REPO_NAME" &> "$_pull_image_log"; then
                echo "There was a problem with creating ECR repo for ${_ECR_REPO_NAME} ..."
                cat "$_pull_image_log"
            else
                echo "Private ECR repo: $_ECR_REPO_NAME was created."
            fi
        fi

        if ! aws ecr describe-images --repository-name="$_ECR_REPO_NAME" --image-ids=imageTag="$_ECR_REPO_TAG" --region "$aws_region" &> /dev/null || [ "$_RESYNC_IMAGES" == "true" ]; then
            docker pull "$image" &> "$_pull_image_log"
            if [ $? -ne 0 ]; then
                echo "There was a problem with pulling image $image ..."
                cat "$_pull_image_log"
                exit 1
            fi

            docker tag "$image" "$private_image"
            docker push "$private_image" &> "$_pull_image_log" && \
            aws ecr set-repository-policy --repository-name "$_ECR_REPO_NAME" --policy-text "file://$_ECR_REPO_POLICY_DOC" &> /dev/null
            if [ $? -ne 0 ]; then
                echo "There was a problem with pushing image $image to $private_image ..."
                cat "$_pull_image_log"
                exit 1
            else
                echo "Image $private_image pushed to the Private ECR repo."
            fi
        fi
    done
else
    echo "Image pull config wasn't set. Nothing to pull/push."
fi


if [ -n "$IMAGE_BUILD_CONFIG" ] && [ -f "$IMAGE_BUILD_CONFIG" ]; then
    echo "Image build config was set as:${IMAGE_BUILD_CONFIG}. Will build -> push images..."

    _build_image_log=$(mktemp)
    readarray -t images < <(jq -r --compact-output '.manifest[] | to_entries | map(.value) | join(",")' "${IMAGE_BUILD_CONFIG}")
    for build_dir_and_image_name in "${images[@]}"; do
        _IMAGE_BUILD_DIR=$(echo "$build_dir_and_image_name" | cut -d, -f1)
        _IMAGE_NAME="$PRIVATE_ECR/$(echo "$build_dir_and_image_name" | cut -d, -f2)"
        echo "Image will be built from : ${_IMAGE_BUILD_DIR}, and pushed to ${_IMAGE_NAME}."

        _ECR_REPO_NAME=$(echo "${_IMAGE_NAME#"${PRIVATE_ECR}/"}" | cut -d: -f1)
        _ECR_REPO_TAG=$(echo "${_IMAGE_NAME}" | grep ":"  &> /dev/null && echo "${_IMAGE_NAME}" | cut -d: -f2 || echo latest)
        if ! aws ecr describe-images --repository-name="$_ECR_REPO_NAME" --region "$aws_region" &> /dev/null; then
            if aws ecr create-repository --region "$aws_region" --repository-name "$_ECR_REPO_NAME" &> "$_pull_image_log"; then
                echo "There was a problem with creating ECR repo for ${_ECR_REPO_NAME} ..."
                cat "$_pull_image_log"
            else
                echo "Private ECR repo: $_ECR_REPO_NAME was created."
            fi
        fi

        if ! aws ecr describe-images --repository-name="$_ECR_REPO_NAME" --image-ids=imageTag="$_ECR_REPO_TAG" --region "$aws_region" &> /dev/null || [ "$_RESYNC_IMAGES" == "true" ]; then
            docker build -t "$_IMAGE_NAME" "$(echo "$_IMAGE_BUILD_DIR" | envsubst)" &> "$_build_image_log"
            if [ $? -ne 0 ]; then
                echo "There was a problem with build process for image $_IMAGE_NAME ..."
                cat "$_build_image_log"
                exit 1
            else
               echo "Successfully built image $_IMAGE_NAME ..."
            fi

            docker push "$_IMAGE_NAME"  &> "$_build_image_log" && \
            aws ecr set-repository-policy --repository-name "$_ECR_REPO_NAME" --policy-text "file://$_ECR_REPO_POLICY_DOC" &> /dev/null
            if [ $? -ne 0 ]; then
                echo "There was a problem with pushing image to $_IMAGE_NAME ..."
                cat "$_build_image_log"
                exit 1
            else
               echo "Successfully pushed image $_IMAGE_NAME ..."
            fi
        fi
    done
else
    echo "Image build config wasn't set. Nothing to build/push."
fi
